package dev.turtur

import android.content.Context
import android.content.Intent
import android.os.Build
import dev.turtur.flock.FlockEngine
import dev.turtur.radio.TurturRadio
import dev.turtur.store.TripStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Process-wide loft: shared by the UI and the foreground BLE service so advertising
 * survives when the activity is backgrounded.
 */
object LoftRuntime {
    @Volatile
    private var ready = false

    lateinit var store: TripStore
        private set
    lateinit var engine: FlockEngine
        private set
    lateinit var radio: TurturRadio
        private set

    private val job = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(job + Dispatchers.Main.immediate)

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val app = context.applicationContext
            store = TripStore(app)
            engine = FlockEngine(store)
            radio = TurturRadio(app, engine, scope)
            engine.load()
            ready = true
        }
    }

    fun startLoftService(context: Context) {
        if (engine.state.trip == null) return
        if (!radio.hasPermissions()) return
        val intent = Intent(context, LoftService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopLoftService(context: Context) {
        context.stopService(Intent(context, LoftService::class.java))
    }

    fun shutdown() {
        radio.stopLoft()
        scope.cancel()
        ready = false
    }
}
