package com.bprvt.bprdownloader.util

import java.util.Locale

object Urls {

    /**
     * Turns whatever the user typed into something loadable, or null if it can't
     * plausibly be a URL. Bare hosts get https://, and a value with no dot and no
     * scheme is treated as not-a-URL so the caller can fall back to searching.
     */
    fun normalize(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty()) return null
        if (input.startsWith("http://", true) || input.startsWith("https://", true)) return input
        if (input.startsWith("ftp://", true) || input.startsWith("file://", true)) return input
        if (!input.contains('.') || input.contains(' ')) return null
        return "https://$input"
    }

    private val FILE_EXTENSIONS = setOf(
        "apk", "zip", "tar", "gz", "xz", "7z", "rar", "bin", "img", "iso",
        "mp4", "mkv", "avi", "mp3", "flac", "m4a", "pdf", "txt", "json", "xml",
        "png", "jpg", "jpeg", "gif", "exe", "deb", "rpm", "obb"
    )

    /** Best-effort guess at whether a URL points at a file rather than a page. */
    fun looksLikeFile(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('/', "").substringAfterLast('.', "")
        return ext.isNotEmpty() && ext.lowercase(Locale.US) in FILE_EXTENSIONS
    }

    /** Filename from the URL path, sanitised for the filesystem. */
    fun fileNameFrom(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val candidate = path.substringAfterLast('/', "")
        return sanitize(candidate.ifEmpty { "download.bin" })
    }

    fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._\\-]"), "_").trim('.', '_')
        return if (cleaned.isEmpty()) "download.bin" else cleaned.take(120)
    }

    /** Short label for a URL: the filename if there is one, else the host. */
    fun shortLabel(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        val host = withoutScheme.substringBefore('/')
        val file = url.substringBefore('?').substringAfterLast('/', "")
        return if (file.isNotEmpty() && file.contains('.')) file else host
    }

    fun host(url: String): String = url.substringAfter("://", url).substringBefore('/')

    fun isApk(nameOrUrl: String): Boolean =
        nameOrUrl.substringBefore('?').lowercase(Locale.US).endsWith(".apk")
}
