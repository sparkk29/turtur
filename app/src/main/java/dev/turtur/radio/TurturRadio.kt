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
import dev.turtur.model.Envelope
import dev.turtur.model.Habitat
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
import java.util.concurrent.atomic.AtomicInteger

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object TurturUuids {
    val SERVICE: UUID = UUID.fromString("76320000-7254-7572-7475-724c6f667431")
    val MAIL: UUID = UUID.fromString("76320001-7254-7572-7475-724c6f667431")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
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
    val epoch: Long = 1,
    val signerLoftId: String = "",
    val sigB64: String = "",
)

@Serializable
data class RadioHave(
    val t: String = "have",
    val ids: List<String>,
)

@Serializable
data class RadioGive(
    val t: String = "give",
    val env: Envelope,
)

@Serializable
data class RadioGot(
    val t: String = "got",
    val pigeonId: String,
)

data class FeedResult(
    val peers: Int,
    val given: Int,
    val taken: Int,
    val peerNames: List<String> = emptyList(),
    val failedWrites: Int = 0,
)

private data class WriteItem(
    val characteristic: BluetoothGattCharacteristic,
    val bytes: ByteArray,
    val attempts: Int = 0,
)

class TurturRadio(
    private val context: Context,
    private val engine: FlockEngine,
    private val scope: CoroutineScope,
) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var advertising = false
    private var scanJob: Job? = null
    private var opportunisticJob: Job? = null
    private var feeding = false
    private var opportunistic = false

    var onEngineChanged: (() -> Unit)? = null

    private val givenCount = AtomicInteger(0)
    private val takenCount = AtomicInteger(0)
    private val failedWrites = AtomicInteger(0)
    private val peerNames = ConcurrentHashMap.newKeySet<String>()
    private val peerByAddress = ConcurrentHashMap<String, Member>()
    private val peerHave = ConcurrentHashMap<String, Set<String>>()
    private val seen = ConcurrentHashMap<String, Long>()
    private val pendingWrites = ConcurrentHashMap<BluetoothGatt, ArrayDeque<WriteItem>>()
    private val writeBusy = ConcurrentHashMap<BluetoothGatt, Boolean>()
    private val buffers = ConcurrentHashMap<String, StringBuilder>()
    private val activeGatts = ConcurrentHashMap.newKeySet<BluetoothGatt>()
    private val inRangeAddresses = ConcurrentHashMap.newKeySet<String>()

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
        startOpportunisticWatch()
    }

    @SuppressLint("MissingPermission")
    fun stopLoft() {
        scanJob?.cancel()
        opportunisticJob?.cancel()
        feeding = false
        opportunistic = false
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        advertising = false
        activeGatts.forEach { runCatching { it.close() } }
        activeGatts.clear()
        inRangeAddresses.clear()
        gattServer?.close()
        gattServer = null
    }

    /**
     * Quiet background scan: when a flock peer appears, sync without tapping Feed.
     * Cadence follows habitat (Wild is stingier).
     */
    @SuppressLint("MissingPermission")
    private fun startOpportunisticWatch() {
        if (opportunistic || !hasPermissions() || engine.state.trip == null) return
        opportunistic = true
        opportunisticJob?.cancel()
        opportunisticJob = scope.launch {
            while (opportunistic && engine.state.trip != null) {
                if (!feeding) {
                    val habitat = engine.state.trip!!.habitat
                    inRangeAddresses.clear()
                    val filters = listOf(
                        ScanFilter.Builder().setServiceUuid(ParcelUuid(TurturUuids.SERVICE)).build(),
                    )
                    val mode = if (habitat == Habitat.CROWD) {
                        ScanSettings.SCAN_MODE_BALANCED
                    } else {
                        ScanSettings.SCAN_MODE_LOW_POWER
                    }
                    val settings = ScanSettings.Builder().setScanMode(mode).build()
                    runCatching {
                        adapter?.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
                    }
                    delay(habitat.opportunisticScanMs())
                    if (!feeding) {
                        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
                    }
                    engine.setInRange(inRangeAddresses.size)
                    onEngineChanged?.invoke()
                    delay(habitat.opportunisticRestMs())
                } else {
                    delay(1_000)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun feed(onDone: (FeedResult) -> Unit) {
        if (engine.state.trip == null || !hasPermissions()) {
            onDone(FeedResult(0, 0, 0))
            return
        }
        feeding = true
        givenCount.set(0)
        takenCount.set(0)
        failedWrites.set(0)
        peerNames.clear()
        peerHave.clear()
        startLoft()
        val habitat = engine.state.trip!!.habitat
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(TurturUuids.SERVICE)).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        adapter?.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        scanJob?.cancel()
        scanJob = scope.launch {
            delay(habitat.scanPeriodMs().coerceAtLeast(6_000))
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            delay(habitat.feedSettleMs())
            feeding = false
            val result = FeedResult(
                peers = peerNames.size,
                given = givenCount.get(),
                taken = takenCount.get(),
                peerNames = peerNames.toList().sorted(),
                failedWrites = failedWrites.get(),
            )
            launch(Dispatchers.Main) { onDone(result) }
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
            .addManufacturerData(TurturUuids.MANUFACTURER_ID, engine.tripAdvertHash())
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
                TurturUuids.CCCD,
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
            if (engine.state.trip == null) return
            val data = result.scanRecord?.getManufacturerSpecificData(TurturUuids.MANUFACTURER_ID)
            if (!engine.tripMatchesAdvert(data)) return
            val address = result.device.address ?: return
            inRangeAddresses.add(address)
            val now = System.currentTimeMillis()
            val cooldown = if (feeding) 10_000L else 30_000L
            val prev = seen[address] ?: 0
            if (now - prev < cooldown) return
            seen[address] = now
            connectAsClient(result.device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectAsClient(device: BluetoothDevice) {
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    activeGatts.add(gatt)
                    gatt.requestMtu(517)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    pendingWrites.remove(gatt)
                    writeBusy.remove(gatt)
                    activeGatts.remove(gatt)
                    gatt.close()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val ch = gatt.getService(TurturUuids.SERVICE)?.getCharacteristic(TurturUuids.MAIL) ?: return
                gatt.setCharacteristicNotification(ch, true)
                val cccd = ch.getDescriptor(TurturUuids.CCCD)
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

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    requeueHead(gatt, characteristic)
                    failedWrites.incrementAndGet()
                }
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
        writeLine(
            gatt,
            ch,
            json.encodeToString(
                RadioHello(tripId = trip.tripId, joinMac = engine.joinMacHex(), member = trip.me),
            ),
        )
        val signed = engine.signedRosterFrame()
        writeLine(
            gatt,
            ch,
            json.encodeToString(
                RadioRoster(
                    members = signed.members,
                    epoch = signed.epoch,
                    signerLoftId = signed.signerLoftId,
                    sigB64 = signed.sigB64,
                ),
            ),
        )
        writeLine(gatt, ch, json.encodeToString(RadioHave(ids = engine.knownPigeonIds())))
    }

    @SuppressLint("MissingPermission")
    private fun writeLine(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, line: String) {
        val payload = (line + "\n").toByteArray(Charsets.UTF_8)
        val q = pendingWrites.getOrPut(gatt) { ArrayDeque() }
        synchronized(q) {
            var i = 0
            while (i < payload.size) {
                val end = minOf(i + 180, payload.size)
                q.add(WriteItem(ch, payload.copyOfRange(i, end)))
                i = end
            }
        }
        drainWrite(gatt)
    }

    private fun requeueHead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val q = pendingWrites[gatt] ?: return
        // Last drained item is already gone; we only bump failed count. Next drain continues.
        // Re-push a sentinel retry by reading last failed bytes from characteristic if available.
        val bytes = characteristic.value ?: return
        synchronized(q) {
            val attempts = 1
            if (attempts < 3) {
                q.addFirst(WriteItem(characteristic, bytes, attempts))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun drainWrite(gatt: BluetoothGatt) {
        val q = pendingWrites[gatt] ?: return
        val next: WriteItem?
        synchronized(q) {
            if (writeBusy[gatt] == true) return
            next = q.removeFirstOrNull()
            writeBusy[gatt] = next != null
        }
        if (next == null) return
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(
                next.characteristic,
                next.bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            next.characteristic.value = next.bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(next.characteristic)
        }
        if (!ok) {
            if (next.attempts < 3) {
                synchronized(q) { q.addFirst(next.copy(attempts = next.attempts + 1)) }
            } else {
                failedWrites.incrementAndGet()
            }
            writeBusy[gatt] = false
            scope.launch {
                delay(120)
                drainWrite(gatt)
            }
        }
    }

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
            handleLine(line, key, clientGatt = clientGatt, serverDevice = serverDevice)
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
        peerKey: String,
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
                    if (!engine.onJoinHello(hello.member, hello.joinMac)) return
                    peerByAddress[peerKey] = hello.member
                    peerNames.add("${hello.member.name} · ${hello.member.band}")
                    val signed = engine.signedRosterFrame()
                    reply(
                        json.encodeToString(
                            RadioRoster(
                                members = signed.members,
                                epoch = signed.epoch,
                                signerLoftId = signed.signerLoftId,
                                sigB64 = signed.sigB64,
                            ),
                        ),
                        clientGatt,
                        serverDevice,
                    )
                    reply(
                        json.encodeToString(RadioHave(ids = engine.knownPigeonIds())),
                        clientGatt,
                        serverDevice,
                    )
                    onEngineChanged?.invoke()
                }
                "\"t\":\"roster\"" in line -> {
                    val roster = json.decodeFromString<RadioRoster>(line)
                    val ok = if (roster.sigB64.isNotBlank() && roster.signerLoftId.isNotBlank()) {
                        engine.mergeSignedRoster(
                            incoming = roster.members,
                            epoch = roster.epoch,
                            signerLoftId = roster.signerLoftId,
                            sigB64 = roster.sigB64,
                            requireSig = true,
                        )
                    } else {
                        // Legacy unsigned frames during rollout: only expand with known joiners.
                        engine.mergeRoster(roster.members)
                        true
                    }
                    if (ok) onEngineChanged?.invoke()
                }
                "\"t\":\"have\"" in line -> {
                    val have = json.decodeFromString<RadioHave>(line)
                    peerHave[peerKey] = have.ids.toSet()
                    val peerLoft = peerByAddress[peerKey]?.loftId ?: trip.me.loftId
                    val give = engine.envelopesForPeer(peerLoft)
                        .filter { it.pigeonId !in have.ids }
                        .take(24)
                    give.forEach { env ->
                        reply(json.encodeToString(RadioGive(env = env)), clientGatt, serverDevice)
                    }
                }
                "\"t\":\"give\"" in line -> {
                    val give = json.decodeFromString<RadioGive>(line)
                    if (engine.ingestEnvelope(give.env)) {
                        takenCount.incrementAndGet()
                        onEngineChanged?.invoke()
                        reply(
                            json.encodeToString(RadioGot(pigeonId = give.env.pigeonId)),
                            clientGatt,
                            serverDevice,
                        )
                    }
                }
                "\"t\":\"got\"" in line -> {
                    val got = json.decodeFromString<RadioGot>(line)
                    givenCount.incrementAndGet()
                    // Peer confirmed they hold this pigeon; keep ours for other hops.
                    peerHave.compute(peerKey) { _, old -> (old.orEmpty() + got.pigeonId) }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun reply(text: String, clientGatt: BluetoothGatt?, serverDevice: BluetoothDevice?) {
        if (clientGatt != null) {
            val ch = clientGatt.getService(TurturUuids.SERVICE)?.getCharacteristic(TurturUuids.MAIL) ?: return
            writeLine(clientGatt, ch, text)
            return
        }
        notifyServer(text, serverDevice)
    }

    @SuppressLint("MissingPermission")
    private fun notifyServer(text: String, serverDevice: BluetoothDevice?) {
        val bytes = (text + "\n").toByteArray(Charsets.UTF_8)
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
