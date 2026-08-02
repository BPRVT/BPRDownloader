package com.bprvt.bprdownloader.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object Installer {

    /**
     * Android 8+ gates sideloading behind a per-app "install unknown apps"
     * toggle. Without it the package installer silently bounces us.
     */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun permissionIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    fun install(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(Uri.fromFile(file), APK_MIME)
            }
        }
        context.startActivity(intent)
    }

    /** Opens a non-APK file with whatever app on the device claims it. */
    fun open(context: Context, file: File) {
        val mime = mimeFor(file.name)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(Uri.fromFile(file), mime)
            }
        }
        context.startActivity(intent)
    }

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "apk" -> APK_MIME
            "mp4", "mkv", "avi", "mov" -> "video/*"
            "mp3", "flac", "m4a", "wav" -> "audio/*"
            "png", "jpg", "jpeg", "gif", "webp" -> "image/*"
            "pdf" -> "application/pdf"
            "txt", "json", "xml", "log" -> "text/plain"
            "zip" -> "application/zip"
            else -> "*/*"
        }
    }

    private const val APK_MIME = "application/vnd.android.package-archive"
}
