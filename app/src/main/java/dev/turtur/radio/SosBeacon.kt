package dev.turtur.radio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import dev.turtur.model.SosRecord
import dev.turtur.sos.SosRecords
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { encodeDefaults = false }

/**
 * Victim side: a slow, high-power advertisement on a dedicated SOS UUID that any Turtur
 * phone can hear (no trip match), plus a read-only GATT characteristic with the signed record.
 */
class SosBeacon(private val context: Context) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var legacyOn = false
    private var codedSet: AdvertisingSet? = null
    private var codedStarting = false

    @Volatile
    private var recordBytes = ByteArray(0)

    fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun update(record: SosRecord) {
        if (!hasPermissions() || adapter?.isEnabled != true) return
        recordBytes = json.encodeToString(record).toByteArray(Charsets.UTF_8)
        ensureServer()
        val payload = SosRecords.advertPayload(record)
        restartLegacy(payload)
        updateCoded(payload)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(legacyCallback) }
        legacyOn = false
        runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertisingSet(codedCallback) }
        codedSet = null
        codedStarting = false
        runCatching { gattServer?.close() }
        gattServer = null
    }

    @SuppressLint("MissingPermission")
    private fun restartLegacy(payload: ByteArray) {
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        if (legacyOn) runCatching { advertiser.stopAdvertising(legacyCallback) }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(TurturUuids.SOS_SERVICE))
            .setIncludeDeviceName(false)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .addManufacturerData(TurturUuids.MANUFACTURER_ID, payload)
            .setIncludeDeviceName(false)
            .build()
        runCatching { advertiser.startAdvertising(settings, data, scanResponse, legacyCallback) }
        legacyOn = true
    }

    /** Long-range Coded PHY set where the radio supports it; legacy keeps older phones covered. */
    @SuppressLint("MissingPermission")
    private fun updateCoded(payload: ByteArray) {
        val a = adapter ?: return
        if (!a.isLeCodedPhySupported || !a.isLeExtendedAdvertisingSupported) return
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(TurturUuids.SOS_SERVICE))
            .addManufacturerData(TurturUuids.MANUFACTURER_ID, payload)
            .setIncludeDeviceName(false)
            .build()
        codedSet?.let {
            runCatching { it.setAdvertisingData(data) }
            return
        }
        if (codedStarting) return
        val params = AdvertisingSetParameters.Builder()
            .setLegacyMode(false)
            .setConnectable(true)
            .setScannable(false)
            .setPrimaryPhy(BluetoothDevice.PHY_LE_CODED)
            .setSecondaryPhy(BluetoothDevice.PHY_LE_CODED)
            .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .build()
        codedStarting = true
        runCatching {
            a.bluetoothLeAdvertiser?.startAdvertisingSet(params, data, null, null, null, codedCallback)
        }.onFailure { codedStarting = false }
    }

    @SuppressLint("MissingPermission")
    private fun ensureServer() {
        if (gattServer != null) return
        val server = manager.openGattServer(context, serverCallback) ?: return
        val service = BluetoothGattService(TurturUuids.SOS_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                TurturUuids.SOS_RECORD,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ),
        )
        server.addService(service)
        gattServer = server
    }

    private val legacyCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            legacyOn = false
        }
    }

    private val codedCallback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(set: AdvertisingSet?, txPower: Int, status: Int) {
            codedStarting = false
            codedSet = if (status == ADVERTISE_SUCCESS) set else null
        }

        override fun onAdvertisingSetStopped(set: AdvertisingSet?) {
            codedSet = null
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val server = gattServer ?: return
            if (characteristic.uuid != TurturUuids.SOS_RECORD) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                return
            }
            val bytes = recordBytes
            if (offset > bytes.size) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
            } else {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes.copyOfRange(offset, bytes.size))
            }
        }
    }
}
