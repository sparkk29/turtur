package dev.turtur

import android.app.Application

class TurturApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LoftRuntime.init(this)
    }
}
