package com.bprvt.bprdownloader.util

import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {

    fun bytes(value: Long): String {
        if (value <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        return if (unit == 0) "${value} B"
        else String.format(Locale.US, "%.1f %s", size, units[unit])
    }

    fun relativeTime(timestamp: Long): String {
        val delta = System.currentTimeMillis() - timestamp
        if (delta < 0) return "just now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        if (minutes < 1) return "just now"
        if (minutes < 60) return "${minutes}m ago"
        val hours = TimeUnit.MILLISECONDS.toHours(delta)
        if (hours < 24) return "${hours}h ago"
        val days = TimeUnit.MILLISECONDS.toDays(delta)
        if (days < 30) return "${days}d ago"
        val months = days / 30
        if (months < 12) return "${months}mo ago"
        return "${days / 365}y ago"
    }
}
