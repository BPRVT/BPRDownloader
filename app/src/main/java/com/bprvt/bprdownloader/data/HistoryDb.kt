package com.bprvt.bprdownloader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HistoryDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_URL TEXT NOT NULL UNIQUE,
                $COL_LABEL TEXT NOT NULL,
                $COL_FILE_NAME TEXT,
                $COL_SIZE INTEGER NOT NULL DEFAULT 0,
                $COL_LAST_USED INTEGER NOT NULL,
                $COL_USE_COUNT INTEGER NOT NULL DEFAULT 1,
                $COL_FAVORITE INTEGER NOT NULL DEFAULT 0,
                $COL_KIND TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_last_used ON $TABLE($COL_LAST_USED DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No shipped schema changes yet; nothing here is precious enough to migrate.
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    companion object {
        const val NAME = "history.db"
        const val VERSION = 1

        const val TABLE = "history"
        const val COL_ID = "id"
        const val COL_URL = "url"
        const val COL_LABEL = "label"
        const val COL_FILE_NAME = "file_name"
        const val COL_SIZE = "size_bytes"
        const val COL_LAST_USED = "last_used"
        const val COL_USE_COUNT = "use_count"
        const val COL_FAVORITE = "favorite"
        const val COL_KIND = "kind"
    }
}
