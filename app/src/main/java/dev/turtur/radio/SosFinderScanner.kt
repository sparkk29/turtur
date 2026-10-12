package dev.turtur.radio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context

/** Foreground, low-latency SOS scan while the finder screen is open. */
class SosFinderScanner(private val context: Context) {
    private var running = false
    private var onResult: ((ScanResult) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start(onResult: (ScanResult) -> Unit) {
        if (running || !SosListener.hasScanPermission(context)) return
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?.takeIf { it.isEnabled }
            ?.bluetoothLeScanner ?: return
        this.onResult = onResult
        runCatching {
            scanner.startScan(
                SosListener.filters(),
                SosListener.settings(context, ScanSettings.SCAN_MODE_LOW_LATENCY),
                callback,
            )
        }.onSuccess { running = true }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!running) return
        running = false
        onResult = null
        runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback)
        }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            onResult?.invoke(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { r -> onResult?.invoke(r) }
        }
    }
}
