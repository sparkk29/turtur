package dev.turtur.sos

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.turtur.LoftRuntime
import dev.turtur.model.ActiveSos
import dev.turtur.radio.SosBeacon
import dev.turtur.radio.SosListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the SOS beacon advertising with the screen off, refreshes the GPS fix, and
 * broadcasts "I'm safe" for a while after cancel so listeners can stand down.
 */
class SosService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var beacon: SosBeacon
    private var fixJob: Job? = null
    private var stopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LoftRuntime.init(this)
        SosNotifier.ensureChannels(this)
        beacon = SosBeacon(this)
        ContextCompat.registerReceiver(
            this,
            bluetoothOn,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private val bluetoothOn = SosListener.onBluetoothOn {
        SosRuntime.state.value.active?.let { beacon.restart(it.record) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LoftRuntime.init(this)
        val active = SosRuntime.state.value.active
        if (active == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = SosNotifier.beaconNotification(this, active)
        runCatching {
            ServiceCompat.startForeground(this, SosNotifier.BEACON_ID, notification, fgsTypes(withLocation = true))
        }.recoverCatching {
            // A sticky restart from the background may not hold location; keep the beacon anyway.
            ServiceCompat.startForeground(this, SosNotifier.BEACON_ID, notification, fgsTypes(withLocation = false))
        }.onFailure {
            stopSelf()
            return START_NOT_STICKY
        }
        beacon.update(active.record)
        if (active.record.cancelled) {
            fixJob?.cancel()
            notifyFlock(active)
            scheduleStop(active.cancelUntilUnix ?: System.currentTimeMillis())
        } else {
            stopJob?.cancel()
            if (active.flockNotified || active.record.lat != null) notifyFlock(active)
            if (fixJob?.isActive != true) fixJob = scope.launch { fixLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(bluetoothOn) }
        beacon.stop()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun fixLoop() {
        while (scope.isActive) {
            val loc = Locator.currentFix(this)
            val next = SosRuntime.updateFix(
                lat = loc?.latitude,
                lon = loc?.longitude,
                accuracyM = loc?.takeIf { it.hasAccuracy() }?.accuracy,
                fixUnix = loc?.time,
                battery = Locator.batteryPercent(this),
            ) ?: return
            if (next.record.cancelled) return
            beacon.update(next.record)
            SosNotifier.updateBeacon(this, next)
            notifyFlock(next)
            delay(FIX_INTERVAL_MS)
        }
    }

    /** One SOS pigeon to the trip when it starts (with a fix if we got one) and one on cancel. */
    private fun notifyFlock(active: ActiveSos) {
        if (active.flockNotified) return
        val engine = LoftRuntime.engine
        if (engine.state.trip == null) return
        runCatching { engine.releaseSos(active.record) }
        SosRuntime.markFlockNotified()
        LoftRuntime.radio.onEngineChanged?.invoke()
    }

    private fun scheduleStop(untilUnix: Long) {
        stopJob?.cancel()
        stopJob = scope.launch {
            delay((untilUnix - System.currentTimeMillis()).coerceAtLeast(0))
            SosRuntime.clearActive()
            ServiceCompat.stopForeground(this@SosService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun fgsTypes(withLocation: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (withLocation && Locator.hasPermission(this)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        return types
    }

    companion object {
        const val ACTION_START = "dev.turtur.sos.START"
        const val ACTION_UPDATE = "dev.turtur.sos.UPDATE"
        private const val FIX_INTERVAL_MS = 10 * 60_000L
    }
}
