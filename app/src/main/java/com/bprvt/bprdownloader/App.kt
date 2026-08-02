package com.bprvt.bprdownloader

import android.app.Application
import com.bprvt.bprdownloader.data.HistoryRepo

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        HistoryRepo.init(this)
    }
}
