package com.bprvt.bprdownloader.download

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Shared, process-wide view of download progress. The service is the only
 * writer; the UI observes [active] for the status bar and [finished] for
 * one-shot completion handling (the install prompt).
 */
object Downloads {

    enum class State { RUNNING, DONE, FAILED }

    data class Task(
        val url: String,
        val fileName: String,
        val bytesDone: Long = 0,
        val bytesTotal: Long = -1,
        val state: State = State.RUNNING,
        val error: String? = null,
        val file: File? = null
    ) {
        val percent: Int
            get() = if (bytesTotal > 0) ((bytesDone * 100) / bytesTotal).toInt().coerceIn(0, 100) else -1
    }

    val active = MutableStateFlow<Task?>(null)
    val finished = MutableSharedFlow<Task>(extraBufferCapacity = 8)

    fun enqueue(context: Context, url: String, fileNameHint: String? = null) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_ENQUEUE
            putExtra(DownloadService.EXTRA_URL, url)
            putExtra(DownloadService.EXTRA_FILE_NAME, fileNameHint)
        }
        context.startService(intent)
    }

    /** Where downloaded files live. App-specific, so no storage permission needed. */
    fun downloadDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "Download").apply { if (!exists()) mkdirs() }
    }
}
