package com.bprvt.bprdownloader.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

@SuppressLint("StaticFieldLeak")
object Prefs {

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("bpr_downloader", Context.MODE_PRIVATE)
    }

    var homepage: String
        get() = prefs.getString(KEY_HOMEPAGE, DEFAULT_HOMEPAGE) ?: DEFAULT_HOMEPAGE
        set(value) = prefs.edit().putString(KEY_HOMEPAGE, value).apply()

    var historyLimit: Int
        get() = prefs.getInt(KEY_HISTORY_LIMIT, 200)
        set(value) = prefs.edit().putInt(KEY_HISTORY_LIMIT, value).apply()

    var deleteAfterInstall: Boolean
        get() = prefs.getBoolean(KEY_DELETE_AFTER_INSTALL, false)
        set(value) = prefs.edit().putBoolean(KEY_DELETE_AFTER_INSTALL, value).apply()

    var confirmBeforeDownload: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_DOWNLOAD, true)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRM_DOWNLOAD, value).apply()

    /** Pixels the browser cursor moves per key event, before acceleration. */
    var cursorSpeed: Int
        get() = prefs.getInt(KEY_CURSOR_SPEED, 12)
        set(value) = prefs.edit().putInt(KEY_CURSOR_SPEED, value.coerceIn(4, 20)).apply()

    private const val KEY_HOMEPAGE = "homepage"
    private const val KEY_HISTORY_LIMIT = "history_limit"
    private const val KEY_DELETE_AFTER_INSTALL = "delete_after_install"
    private const val KEY_CONFIRM_DOWNLOAD = "confirm_download"
    private const val KEY_CURSOR_SPEED = "cursor_speed"

    const val DEFAULT_HOMEPAGE = "https://github.com/BPRVT?tab=repositories"
}
