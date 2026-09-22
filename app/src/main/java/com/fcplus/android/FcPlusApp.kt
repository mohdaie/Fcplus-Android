package com.fcplus.android

import android.app.Application

class FcPlusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.context = applicationContext
    }
}
