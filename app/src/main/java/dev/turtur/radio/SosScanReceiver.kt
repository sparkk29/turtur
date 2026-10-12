package dev.turtur.radio

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import dev.turtur.LoftRuntime
import dev.turtur.sos.SosRuntime

class SosScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SosListener.ACTION_SCAN) return
        LoftRuntime.init(context)
        val results = IntentCompat.getParcelableArrayListExtra(
            intent,
            BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
            ScanResult::class.java,
        ).orEmpty()
        if (results.isEmpty()) return
        val pending = goAsync()
        SosReader.handle(context.applicationContext, results) { pending.finish() }
    }
}

/** Neither PendingIntent scans nor the beacon service survive a reboot or an app update. */
class SosBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                LoftRuntime.init(context)
                SosListener.sync(context)
                runCatching { SosRuntime.resume(context) }
            }
        }
    }
}
