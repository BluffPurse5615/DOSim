package com.keylerperales07.projectmountains.launcher

import android.app.Application

class ReSimApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
