package com.bprvt.bprdownloader.util

import java.util.Locale

object Urls {

    /**
     * Turns whatever the phone sent into something downloadable, or null if it
     * can't plausibly be a URL. Bare hosts get https://.
     */
    fun normalize(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty()) return null
        if (input.startsWith("http://", true) || input.startsWith("https://", true)) return input
        if (!input.contains('.') || input.contains(' ')) return null
        return "https://$input"
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

    fun isApk(nameOrUrl: String): Boolean =
        nameOrUrl.substringBefore('?').lowercase(Locale.US).endsWith(".apk")
}
