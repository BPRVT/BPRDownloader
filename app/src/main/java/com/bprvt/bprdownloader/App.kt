package com.bprvt.bprdownloader

import android.app.Application
import com.bprvt.bprdownloader.data.HistoryRepo
import com.bprvt.bprdownloader.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        HistoryRepo.init(this)
    }
}
