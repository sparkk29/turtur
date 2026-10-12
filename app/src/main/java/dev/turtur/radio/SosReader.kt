package dev.turtur.radio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanResult
import android.content.Context
import dev.turtur.LoftRuntime
import dev.turtur.model.SosRecord
import dev.turtur.sos.SosNotifier
import dev.turtur.sos.SosRecords
import dev.turtur.sos.SosRuntime
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

private val json = Json { ignoreUnknownKeys = true }

/** Listener side: turn SOS scan hits into verified records, reading GATT only when needed. */
object SosReader {
    private const val RETRY_MS = 60_000L
    private const val READ_TIMEOUT_MS = 15_000L
    private const val BATCH_TIMEOUT_MS = 25_000L
    private const val FAILS_BEFORE_UNREAD = 2

    private val lastAttempt = ConcurrentHashMap<String, Long>()
    private val failures = ConcurrentHashMap<String, Int>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun handle(context: Context, results: List<ScanResult>, onDone: () -> Unit = {}) {
        LoftRuntime.scope.launch {
            try {
                withTimeoutOrNull(BATCH_TIMEOUT_MS) {
                    results.distinctBy { it.device.address }.forEach { handleOne(context, it) }
                }
            } finally {
                onDone()
            }
        }
    }

    private suspend fun handleOne(context: Context, result: ScanResult) {
        val advert = SosRecords.parseAdvert(
            result.scanRecord?.getManufacturerSpecificData(TurturUuids.MANUFACTURER_ID),
        )
        val key = advert?.beaconId ?: result.device.address
        if (advert != null) {
            if (SosRuntime.isOwnBeacon(advert.beaconId)) return
            val known = SosRuntime.knownSeq(advert.beaconId)
            SosRuntime.touch(advert.beaconId, result.rssi)
            if (known != null && (known and 0xffffffffL) >= advert.seq) return
        }
        val now = System.currentTimeMillis()
        if (now - (lastAttempt[key] ?: 0) < RETRY_MS) return
        if (!inFlight.add(key)) return
        lastAttempt[key] = now
        try {
            val record = readRecord(context, result.device)
            if (record != null && SosRecords.verify(record)) {
                failures.remove(key)
                SosRuntime.onHeard(record, result.rssi, viaFlock = false)
            } else {
                val n = (failures[key] ?: 0) + 1
                failures[key] = n
                if (n == FAILS_BEFORE_UNREAD && advert != null && !advert.cancelled) {
                    SosNotifier.showUnread(context, advert.beaconId)
                }
            }
        } finally {
            inFlight.remove(key)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun readRecord(context: Context, device: BluetoothDevice): SosRecord? =
        withTimeoutOrNull(READ_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                var gatt: BluetoothGatt? = null
                fun finish(record: SosRecord?) {
                    if (cont.isActive) cont.resume(record)
                    gatt?.let {
                        runCatching { it.disconnect() }
                        runCatching { it.close() }
                    }
                }
                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        gatt = g
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> g.requestMtu(517)
                            BluetoothProfile.STATE_DISCONNECTED -> finish(null)
                        }
                    }

                    override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                        g.discoverServices()
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        val ch = g.getService(TurturUuids.SOS_SERVICE)?.getCharacteristic(TurturUuids.SOS_RECORD)
                        if (ch == null || !g.readCharacteristic(ch)) finish(null)
                    }

                    override fun onCharacteristicRead(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int,
                    ) {
                        finish(if (status == BluetoothGatt.GATT_SUCCESS) parse(value) else null)
                    }

                    @Deprecated("Pre-33 callback")
                    @Suppress("DEPRECATION")
                    override fun onCharacteristicRead(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int,
                    ) {
                        finish(if (status == BluetoothGatt.GATT_SUCCESS) parse(characteristic.value) else null)
                    }
                }
                gatt = device.connectGatt(
                    context,
                    false,
                    callback,
                    BluetoothDevice.TRANSPORT_LE,
                    SosListener.connectPhy(context),
                )
                cont.invokeOnCancellation {
                    gatt?.let {
                        runCatching { it.disconnect() }
                        runCatching { it.close() }
                    }
                }
            }
        }

    private fun parse(bytes: ByteArray?): SosRecord? = bytes?.let {
        runCatching { json.decodeFromString<SosRecord>(String(it, Charsets.UTF_8)) }.getOrNull()
    }
}
