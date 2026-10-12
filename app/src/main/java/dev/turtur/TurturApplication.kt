package dev.turtur

import android.app.Application
import dev.turtur.radio.SosListener
import dev.turtur.sos.SmsBridge

class TurturApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LoftRuntime.init(this)
        SosListener.sync(this)
        SmsBridge.refreshCarrying(this)
    }
}
