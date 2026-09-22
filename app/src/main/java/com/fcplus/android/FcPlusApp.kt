package com.fcplus.android

import android.app.Application
import android.content.Context

class FcPlusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.context = applicationContext
        TradingStore.initialize()
    }
}

object AppContextHolder {
    lateinit var context: Context
}
