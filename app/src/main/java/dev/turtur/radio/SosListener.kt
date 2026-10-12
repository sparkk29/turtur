package dev.turtur.radio

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import dev.turtur.LoftRuntime
import dev.turtur.sos.SosRuntime

/**
 * Background SOS listening via a PendingIntent scan: the Bluetooth stack filters on the
 * SOS UUID and wakes [SosScanReceiver] even when Turtur is not running.
 */
object SosListener {
    const val ACTION_SCAN = "dev.turtur.sos.SCAN_RESULT"
    private const val REQUEST = 7

    fun shouldListen(): Boolean =
        SosRuntime.state.value.profile.samaritan || LoftRuntime.engine.state.trip != null

    fun hasScanPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 31) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun sync(context: Context) {
        LoftRuntime.init(context)
        if (shouldListen() && hasScanPermission(context)) start(context) else stop(context)
    }

    private var bluetoothWatch: BroadcastReceiver? = null

    /** The stack drops PendingIntent scans when Bluetooth restarts; re-arm when it comes back. */
    fun watchBluetooth(context: Context) {
        if (bluetoothWatch != null) return
        val app = context.applicationContext
        val receiver = onBluetoothOn { sync(app) }
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        bluetoothWatch = receiver
    }

    fun onBluetoothOn(action: () -> Unit) = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_ON) action()
        }
    }

    fun filters(): List<ScanFilter> =
        listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(TurturUuids.SOS_SERVICE)).build())

    /** Include extended/Coded PHY adverts where the radio can hear them. */
    fun settings(context: Context, mode: Int): ScanSettings {
        val builder = ScanSettings.Builder().setScanMode(mode)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter != null && adapter.isLeExtendedAdvertisingSupported) {
            builder.setLegacy(false)
            builder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
        }
        return builder.build()
    }

    fun connectPhy(context: Context): Int {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return if (adapter?.isLeCodedPhySupported == true) {
            BluetoothDevice.PHY_LE_1M_MASK or BluetoothDevice.PHY_LE_CODED_MASK
        } else {
            BluetoothDevice.PHY_LE_1M_MASK
        }
    }

    @SuppressLint("MissingPermission")
    private fun start(context: Context) {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?.takeIf { it.isEnabled }
            ?.bluetoothLeScanner ?: return
        val pi = pendingIntent(context)
        runCatching { scanner.stopScan(pi) }
        runCatching { scanner.startScan(filters(), settings(context, ScanSettings.SCAN_MODE_LOW_POWER), pi) }
    }

    @SuppressLint("MissingPermission")
    private fun stop(context: Context) {
        if (!hasScanPermission(context)) return
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return
        runCatching { scanner.stopScan(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SosScanReceiver::class.java).setAction(ACTION_SCAN)
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
    }
}
