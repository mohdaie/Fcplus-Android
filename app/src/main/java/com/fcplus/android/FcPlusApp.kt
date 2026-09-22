package com.fcplus.android

import android.app.Application
import android.content.Context

class FcPlusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.context = applicationContext
    }
}

object AppContextHolder {
    lateinit var context: Context
}
