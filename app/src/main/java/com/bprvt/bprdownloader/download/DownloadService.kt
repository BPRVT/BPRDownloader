package com.bprvt.bprdownloader.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bprvt.bprdownloader.MainActivity
import com.bprvt.bprdownloader.R
import com.bprvt.bprdownloader.data.HistoryRepo
import com.bprvt.bprdownloader.util.Urls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    private data class Request(val url: String, val fileNameHint: String?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Request>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        scope.launch {
            for (request in queue) {
                runCatching { download(request) }
                    .onFailure { fail(request.url, request.fileNameHint ?: "", it.message ?: "Unknown error") }
                if (pending.decrementAndGet() <= 0) {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ENQUEUE) {
            val url = intent.getStringExtra(EXTRA_URL)
            if (!url.isNullOrBlank()) {
                pending.incrementAndGet()
                startForeground(NOTIFICATION_ID, buildNotification(Urls.shortLabel(url), -1))
                queue.trySend(Request(url, intent.getStringExtra(EXTRA_FILE_NAME)))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun download(request: Request) {
        var currentUrl = request.url
        var connection: HttpURLConnection? = null
        var redirects = 0

        // HttpURLConnection refuses to follow http -> https (and vice versa)
        // automatically, which is exactly the case GitHub release links hit.
        while (true) {
            connection?.disconnect()
            connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
            val code = connection.responseCode
            if (code !in 300..399) break
            val location = connection.getHeaderField("Location")
                ?: throw IllegalStateException("Redirect with no Location header")
            if (++redirects > MAX_REDIRECTS) throw IllegalStateException("Too many redirects")
            currentUrl = URL(URL(currentUrl), location).toString()
        }

        val conn = connection!!
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            fail(request.url, request.fileNameHint ?: Urls.fileNameFrom(request.url), "HTTP $code")
            return
        }

        val fileName = resolveFileName(request, conn, currentUrl)
        val total = contentLength(conn)
        val target = uniqueFile(Downloads.downloadDir(this), fileName)

        Downloads.active.value = Downloads.Task(request.url, fileName, 0, total)
        updateNotification(fileName, -1)

        var done = 0L
        var lastPublished = 0L
        try {
            conn.inputStream.use { input: InputStream ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        // Throttle UI/notification churn to ~4 updates a second.
                        val now = System.currentTimeMillis()
                        if (now - lastPublished > 250) {
                            lastPublished = now
                            val task = Downloads.Task(request.url, fileName, done, total)
                            Downloads.active.value = task
                            updateNotification(fileName, task.percent)
                        }
                    }
                    output.flush()
                }
            }
        } catch (t: Throwable) {
            target.delete()
            throw t
        } finally {
            conn.disconnect()
        }

        HistoryRepo.record(request.url, fileName)
        HistoryRepo.markDownloaded(request.url, fileName, done)

        val task = Downloads.Task(request.url, fileName, done, done, Downloads.State.DONE, null, target)
        Downloads.active.value = task
        Downloads.finished.emit(task)
        notifyDone(fileName)
    }

    private suspend fun fail(url: String, fileName: String, message: String) {
        val task = Downloads.Task(url, fileName, 0, -1, Downloads.State.FAILED, message)
        Downloads.active.value = task
        Downloads.finished.emit(task)
        notifyFailed(fileName, message)
    }

    private fun resolveFileName(request: Request, conn: HttpURLConnection, finalUrl: String): String {
        request.fileNameHint?.takeIf { it.isNotBlank() }?.let { return Urls.sanitize(it) }
        conn.getHeaderField("Content-Disposition")?.let { disposition ->
            FILENAME_STAR.find(disposition)?.groupValues?.get(1)?.let { encoded ->
                return Urls.sanitize(runCatching {
                    java.net.URLDecoder.decode(encoded.substringAfterLast('\''), "UTF-8")
                }.getOrDefault(encoded))
            }
            FILENAME.find(disposition)?.groupValues?.get(1)?.let { return Urls.sanitize(it) }
        }
        return Urls.fileNameFrom(finalUrl)
    }

    private fun contentLength(conn: HttpURLConnection): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) conn.contentLengthLong
        else conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L

    /** Never clobber an existing download; append (1), (2), … instead. */
    private fun uniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val next = if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext"
            candidate = File(dir, next)
            index++
        }
        return candidate
    }

    // --- notifications -----------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun contentIntent(): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
    }

    private fun buildNotification(title: String, percent: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.downloading))
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .apply {
                if (percent >= 0) setProgress(100, percent, false) else setProgress(0, 0, true)
            }
            .build()

    private fun updateNotification(title: String, percent: Int) {
        notificationManager().notify(NOTIFICATION_ID, buildNotification(title, percent))
    }

    private fun notifyDone(fileName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.download_complete))
            .setContentText(fileName)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        notificationManager().notify(NOTIFICATION_ID + 1, notification)
    }

    private fun notifyFailed(fileName: String, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(getString(R.string.download_failed))
            .setContentText("$fileName — $message")
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        notificationManager().notify(NOTIFICATION_ID + 2, notification)
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    companion object {
        const val ACTION_ENQUEUE = "com.bprvt.bprdownloader.ENQUEUE"
        const val EXTRA_URL = "url"
        const val EXTRA_FILE_NAME = "file_name"

        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_REDIRECTS = 8
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; AFTT) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

        private val FILENAME_STAR = Regex("filename\\*=([^;]+)", RegexOption.IGNORE_CASE)
        private val FILENAME = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
    }
}
