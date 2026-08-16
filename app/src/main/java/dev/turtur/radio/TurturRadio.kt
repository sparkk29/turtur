package dev.turtur.radio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import dev.turtur.flock.FlockEngine
import dev.turtur.model.Member
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object TurturUuids {
    val SERVICE: UUID = UUID.fromString("76320000-7254-7572-7475-724c6f667431")
    val MAIL: UUID = UUID.fromString("76320001-7254-7572-7475-724c6f667431")
    const val MANUFACTURER_ID = 0xFFFF
}

@Serializable
data class RadioHello(
    val t: String = "hello",
    val tripId: String,
    val joinMac: String,
    val member: Member,
)

@Serializable
data class RadioRoster(
    val t: String = "roster",
    val members: List<Member>,
)

@Serializable
data class RadioHave(
    val t: String = "have",
    val ids: List<String>,
)

@Serializable
data class RadioGive(
    val t: String = "give",
    val env: dev.turtur.model.Envelope,
)

class TurturRadio(
    private val context: Context,
    private val engine: FlockEngine,
    private val scope: CoroutineScope,
) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var scanning = false
    private var advertising = false
    private var scanJob: Job? = null
    var lastPeers: Int = 0
        private set
    var lastGiven: Int = 0
        private set
    var lastTaken: Int = 0
        private set

    private val seen = ConcurrentHashMap<String, Long>()

    fun hasPermissions(): Boolean {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            needed += Manifest.permission.BLUETOOTH_SCAN
            needed += Manifest.permission.BLUETOOTH_CONNECT
            needed += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return needed.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startLoft() {
        if (engine.state.trip == null || !hasPermissions()) return
        startServer()
        startAdvertise()
    }

    @SuppressLint("MissingPermission")
    fun stopLoft() {
        scanJob?.cancel()
        scanning = false
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        advertising = false
        gattServer?.close()
        gattServer = null
    }

    @SuppressLint("MissingPermission")
    fun feed(onDone: (peers: Int, given: Int, taken: Int) -> Unit) {
        if (engine.state.trip == null) {
            onDone(0, 0, 0)
            return
        }
        if (!hasPermissions()) {
            onDone(0, 0, 0)
            return
        }
        lastPeers = 0
        lastGiven = 0
        lastTaken = 0
        startLoft()
        val habitat = engine.state.trip!!.habitat
        scanning = true
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(TurturUuids.SERVICE)).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        adapter?.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        scanJob?.cancel()
        scanJob = scope.launch {
            delay(habitat.scanPeriodMs())
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            scanning = false
            delay(2_500)
            val peers = lastPeers
            val given = lastGiven
            val taken = lastTaken
            launch(Dispatchers.Main) { onDone(peers, given, taken) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertise() {
        if (advertising) return
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addManufacturerData(MANUFACTURER_ID, engine.tripAdvertHash())
            .setIncludeDeviceName(false)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(TurturUuids.SERVICE))
            .setIncludeDeviceName(false)
            .build()
        advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
        advertising = true
    }

    @SuppressLint("MissingPermission")
    private fun startServer() {
        if (gattServer != null) return
        gattServer = manager.openGattServer(context, serverCallback)
        val service = BluetoothGattService(TurturUuids.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val mail = BluetoothGattCharacteristic(
            TurturUuids.MAIL,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ,
        )
        mail.addDescriptor(
            BluetoothGattDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(mail)
        gattServer?.addService(service)
    }

    private val advertiseCallback = object : AdvertiseCallback() {}

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val trip = engine.state.trip ?: return
            val data = result.scanRecord?.getManufacturerSpecificData(TurturUuids.MANUFACTURER_ID)
            if (!engine.tripMatchesAdvert(data)) return
            val address = result.device.address ?: return
            val now = System.currentTimeMillis()
            val prev = seen[address] ?: 0
            if (now - prev < 8_000) return
            seen[address] = now
            lastPeers += 1
            connectAsClient(result.device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectAsClient(device: BluetoothDevice) {
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.requestMtu(517)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    pendingWrites.remove(gatt)
                    writeBusy.remove(gatt)
                    gatt.close()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val ch = gatt.getService(TurturUuids.SERVICE)?.getCharacteristic(TurturUuids.MAIL) ?: return
                gatt.setCharacteristicNotification(ch, true)
                val cccd = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                if (cccd != null) {
                    writeBusy[gatt] = true
                    if (Build.VERSION.SDK_INT >= 33) {
                        gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(cccd)
                    }
                } else {
                    queueHandshake(gatt, ch)
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                writeBusy[gatt] = false
                drainWrite(gatt)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                writeBusy[gatt] = false
                val ch = gatt.getService(TurturUuids.SERVICE)?.getCharacteristic(TurturUuids.MAIL)
                if (ch != null) queueHandshake(gatt, ch)
                drainWrite(gatt)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                val chunk = characteristic.value?.toString(Charsets.UTF_8) ?: return
                appendAndDispatch(gatt.device.address, chunk, clientGatt = gatt)
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    private fun queueHandshake(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        val trip = engine.state.trip ?: return
        writeLine(gatt, ch, json.encodeToString(RadioHello(tripId = trip.tripId, joinMac = engine.joinMacHex(), member = trip.me)))
        writeLine(gatt, ch, json.encodeToString(RadioRoster(members = trip.members)))
        writeLine(gatt, ch, json.encodeToString(RadioHave(ids = engine.state.pocket.map { it.pigeonId })))
    }

    private val pendingWrites = ConcurrentHashMap<BluetoothGatt, ArrayDeque<Pair<BluetoothGattCharacteristic, ByteArray>>>()
    private val writeBusy = ConcurrentHashMap<BluetoothGatt, Boolean>()

    @SuppressLint("MissingPermission")
    private fun writeLine(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, line: String) {
        val payload = (line + "\n").toByteArray(Charsets.UTF_8)
        val q = pendingWrites.getOrPut(gatt) { ArrayDeque() }
        synchronized(q) {
            var i = 0
            while (i < payload.size) {
                val end = minOf(i + 180, payload.size)
                q.add(ch to payload.copyOfRange(i, end))
                i = end
            }
        }
        drainWrite(gatt)
    }

    @SuppressLint("MissingPermission")
    private fun drainWrite(gatt: BluetoothGatt) {
        val q = pendingWrites[gatt] ?: return
        val next: Pair<BluetoothGattCharacteristic, ByteArray>?
        synchronized(q) {
            if (writeBusy[gatt] == true) return
            next = q.removeFirstOrNull()
            writeBusy[gatt] = next != null
        }
        if (next == null) return
        val (ch, bytes) = next
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            ch.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
        }
        if (!ok) {
            writeBusy[gatt] = false
        }
    }

    private val buffers = ConcurrentHashMap<String, StringBuilder>()

    private fun appendAndDispatch(
        key: String,
        chunk: String,
        clientGatt: BluetoothGatt? = null,
        serverDevice: BluetoothDevice? = null,
    ) {
        val buf = buffers.getOrPut(key) { StringBuilder() }
        buf.append(chunk)
        var idx: Int
        while (buf.indexOf("\n").also { idx = it } >= 0) {
            val line = buf.substring(0, idx)
            buf.delete(0, idx + 1)
            handleLine(line, clientGatt = clientGatt, serverDevice = serverDevice)
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            val chunk = value?.toString(Charsets.UTF_8) ?: return
            appendAndDispatch(device.address, chunk, serverDevice = device)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleLine(
        line: String,
        clientGatt: BluetoothGatt? = null,
        serverDevice: BluetoothDevice? = null,
    ) {
        if (line.isBlank()) return
        val trip = engine.state.trip ?: return
        runCatching {
            when {
                "\"t\":\"hello\"" in line -> {
                    val hello = json.decodeFromString<RadioHello>(line)
                    if (hello.tripId != trip.tripId) return
                    engine.onJoinHello(hello.member, hello.joinMac)
                    engine.mergeRoster(listOf(hello.member))
                }
                "\"t\":\"roster\"" in line -> {
                    val roster = json.decodeFromString<RadioRoster>(line)
                    engine.mergeRoster(roster.members)
                }
                "\"t\":\"have\"" in line -> {
                    val have = json.decodeFromString<RadioHave>(line)
                    val give = engine.envelopesForPeer(trip.me.loftId)
                        .filter { it.pigeonId !in have.ids }
                    lastGiven += give.size
                    give.forEach { env ->
                        notifyOrWrite(json.encodeToString(RadioGive(env = env)), clientGatt, serverDevice)
                    }
                }
                "\"t\":\"give\"" in line -> {
                    val give = json.decodeFromString<RadioGive>(line)
                    if (engine.ingestEnvelope(give.env)) lastTaken += 1
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyOrWrite(text: String, clientGatt: BluetoothGatt?, serverDevice: BluetoothDevice?) {
        val bytes = (text + "\n").toByteArray()
        if (clientGatt != null) {
            val ch = clientGatt.getService(TurturUuids.SERVICE)?.getCharacteristic(TurturUuids.MAIL) ?: return
            writeLine(clientGatt, ch, text)
            return
        }
        val ch = gattServer?.getService(TurturUuids.SERVICE)?.getCharacteristic(TurturUuids.MAIL) ?: return
        var i = 0
        while (i < bytes.size) {
            val end = minOf(i + 180, bytes.size)
            val slice = bytes.copyOfRange(i, end)
            if (Build.VERSION.SDK_INT >= 33 && serverDevice != null) {
                gattServer?.notifyCharacteristicChanged(serverDevice, ch, false, slice)
            } else {
                @Suppress("DEPRECATION")
                ch.value = slice
                @Suppress("DEPRECATION")
                serverDevice?.let { gattServer?.notifyCharacteristicChanged(it, ch, false) }
            }
            i = end
        }
    }
}
