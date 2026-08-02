package com.bprvt.bprdownloader.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.bprvt.bprdownloader.data.HistoryDb.Companion.COL_FAVORITE
import com.bprvt.bprdownloader.data.HistoryDb.Companion.COL_FILE_NAME
import com.bprvt.bprdownloader.data.HistoryDb.Companion.COL_ID
import com.bprvt.bprdownloader.data.HistoryDb.Companion.COL_KIND
import com.bprvt.bprdownloader.data.HistoryDb.Companion.COL_LABEL
import com.bprvt.bprdownloader.data.HistoryDb.Companion.COL_LAST_USED
import com.bprvt.bprdownloader.data.HistoryDb.Companion.COL_SIZE
import com.bprvt.bprdownloader.data.HistoryDb.Companion.COL_URL
import com.bprvt.bprdownloader.data.HistoryDb.Companion.COL_USE_COUNT
import com.bprvt.bprdownloader.data.HistoryDb.Companion.TABLE
import com.bprvt.bprdownloader.util.Urls
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * All history reads/writes are blocking; callers are expected to be on a
 * background dispatcher. [revision] bumps on every mutation so the UI can
 * reload without a change-notification framework.
 */
@SuppressLint("StaticFieldLeak")
object HistoryRepo {

    private lateinit var db: HistoryDb

    val revision = MutableStateFlow(0)

    fun init(context: Context) {
        db = HistoryDb(context.applicationContext)
    }

    /** Favourites first, then most recently used. */
    private const val ORDER = "$COL_FAVORITE DESC, $COL_LAST_USED DESC"

    fun all(): List<HistoryEntry> = query(null, null)

    fun search(term: String): List<HistoryEntry> {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return all()
        val like = "%${trimmed.replace("%", "\\%").replace("_", "\\_")}%"
        return query(
            "($COL_URL LIKE ? ESCAPE '\\' OR $COL_LABEL LIKE ? ESCAPE '\\' OR $COL_FILE_NAME LIKE ? ESCAPE '\\')",
            arrayOf(like, like, like)
        )
    }

    private fun query(selection: String?, args: Array<String>?): List<HistoryEntry> {
        val out = ArrayList<HistoryEntry>()
        db.readableDatabase.query(TABLE, null, selection, args, null, null, ORDER).use { c ->
            while (c.moveToNext()) out.add(c.toEntry())
        }
        return out
    }

    fun byUrl(url: String): HistoryEntry? =
        query("$COL_URL = ?", arrayOf(url)).firstOrNull()

    /**
     * Records a visit/download. Existing URLs are bumped rather than duplicated,
     * which is what makes the list stay short and useful.
     */
    fun record(url: String, label: String? = null, kind: String = HistoryEntry.KIND_DOWNLOAD): Long {
        val now = System.currentTimeMillis()
        val existing = byUrl(url)
        val writable = db.writableDatabase
        if (existing != null) {
            val values = ContentValues().apply {
                put(COL_LAST_USED, now)
                put(COL_USE_COUNT, existing.useCount + 1)
                if (!label.isNullOrBlank()) put(COL_LABEL, label)
                put(COL_KIND, kind)
            }
            writable.update(TABLE, values, "$COL_ID = ?", arrayOf(existing.id.toString()))
            revision.value++
            return existing.id
        }
        val values = ContentValues().apply {
            put(COL_URL, url)
            put(COL_LABEL, label?.takeIf { it.isNotBlank() } ?: Urls.shortLabel(url))
            put(COL_LAST_USED, now)
            put(COL_USE_COUNT, 1)
            put(COL_FAVORITE, 0)
            put(COL_KIND, kind)
        }
        val id = writable.insert(TABLE, null, values)
        revision.value++
        return id
    }

    fun markDownloaded(url: String, fileName: String, size: Long) {
        val values = ContentValues().apply {
            put(COL_FILE_NAME, fileName)
            put(COL_SIZE, size)
            put(COL_KIND, HistoryEntry.KIND_DOWNLOAD)
            put(COL_LAST_USED, System.currentTimeMillis())
        }
        db.writableDatabase.update(TABLE, values, "$COL_URL = ?", arrayOf(url))
        revision.value++
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        val values = ContentValues().apply { put(COL_FAVORITE, if (favorite) 1 else 0) }
        db.writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
        revision.value++
    }

    fun delete(id: Long) {
        db.writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
        revision.value++
    }

    fun clear() {
        db.writableDatabase.delete(TABLE, null, null)
        revision.value++
    }

    fun count(): Long {
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }

    /** Drops the oldest non-favourite rows once the list outgrows [limit]. */
    fun trimTo(limit: Int) {
        if (limit <= 0) return
        db.writableDatabase.execSQL(
            """
            DELETE FROM $TABLE WHERE $COL_FAVORITE = 0 AND $COL_ID NOT IN (
                SELECT $COL_ID FROM $TABLE ORDER BY $COL_FAVORITE DESC, $COL_LAST_USED DESC LIMIT ?
            )
            """.trimIndent(),
            arrayOf<Any>(limit)
        )
        revision.value++
    }

    private fun Cursor.toEntry(): HistoryEntry {
        val fileNameIndex = getColumnIndexOrThrow(COL_FILE_NAME)
        return HistoryEntry(
            id = getLong(getColumnIndexOrThrow(COL_ID)),
            url = getString(getColumnIndexOrThrow(COL_URL)),
            label = getString(getColumnIndexOrThrow(COL_LABEL)),
            fileName = if (isNull(fileNameIndex)) null else getString(fileNameIndex),
            sizeBytes = getLong(getColumnIndexOrThrow(COL_SIZE)),
            lastUsed = getLong(getColumnIndexOrThrow(COL_LAST_USED)),
            useCount = getInt(getColumnIndexOrThrow(COL_USE_COUNT)),
            favorite = getInt(getColumnIndexOrThrow(COL_FAVORITE)) == 1,
            kind = getString(getColumnIndexOrThrow(COL_KIND))
        )
    }
}
