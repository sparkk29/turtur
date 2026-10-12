package dev.turtur.sos

import android.content.Context
import android.content.Intent
import dev.turtur.crypto.TurturCrypto
import dev.turtur.model.ActiveSos
import dev.turtur.model.EmergencyContact
import dev.turtur.model.GuardianCard
import dev.turtur.model.GuardianProfile
import dev.turtur.model.MAX_SOS_CONTACTS
import dev.turtur.model.SosRecord
import dev.turtur.model.SosSighting
import dev.turtur.radio.SosListener
import dev.turtur.store.GuardianStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SosState(
    val profile: GuardianProfile = GuardianProfile(),
    val active: ActiveSos? = null,
    val sightings: List<SosSighting> = emptyList(),
)

/** Process-wide SOS hub shared by the UI, the beacon service, and the scan receiver. */
object SosRuntime {
    const val CANCEL_BROADCAST_MS = 30 * 60_000L
    private const val SIGHTING_KEEP_MS = 72L * 3600_000

    @Volatile
    private var ready = false
    private lateinit var store: GuardianStore
    private lateinit var appContext: Context

    private val _state = MutableStateFlow(SosState())
    val state: StateFlow<SosState> = _state

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            appContext = context.applicationContext
            store = GuardianStore(appContext)
            _state.value = SosState(
                profile = store.loadProfile(),
                active = store.loadActive(),
                sightings = pruned(store.loadSightings(), now()),
            )
            ready = true
        }
    }

    // ---- profile and contacts ----

    fun setIdentity(name: String, phone: String) = editProfile {
        it.copy(name = name.trim().take(SosRecords.NAME_MAX), phone = Phones.normalize(phone))
    }

    fun addContact(contact: EmergencyContact) = editProfile { p ->
        val clean = contact.copy(
            name = contact.name.trim().take(SosRecords.NAME_MAX).ifBlank { "contact" },
            phone = Phones.normalize(contact.phone),
        )
        require(clean.phone.isNotBlank() || clean.signPkB64 != null) { "add a phone number or scan a card" }
        val others = p.contacts.filterNot { existing ->
            (clean.signPkB64 != null && existing.signPkB64 == clean.signPkB64) ||
                (clean.phone.isNotBlank() && existing.phone == clean.phone)
        }
        p.copy(contacts = others + clean)
    }

    fun removeContact(index: Int) = editProfile { p ->
        p.copy(contacts = p.contacts.filterIndexed { i, _ -> i != index })
    }

    fun setSamaritan(on: Boolean) {
        editProfile { it.copy(samaritan = on) }
        SosListener.sync(appContext)
    }

    fun card(): GuardianCard? {
        val p = _state.value.profile
        if (p.name.isBlank()) return null
        return GuardianCard(name = p.name, phone = p.phone, signPkB64 = p.signPkB64)
    }

    fun displayName(fallback: String?): String =
        _state.value.profile.name.ifBlank { fallback.orEmpty() }.ifBlank { "Turtur hiker" }

    fun smsContacts(): List<String> =
        _state.value.profile.contacts.map { it.phone }.filter(Phones::isDialable).take(MAX_SOS_CONTACTS)

    private fun editProfile(change: (GuardianProfile) -> GuardianProfile) {
        val next = change(_state.value.profile)
        store.saveProfile(next)
        _state.value = _state.value.copy(profile = next)
    }

    // ---- victim side ----

    fun startSos(context: Context, fallbackName: String?) {
        val existing = _state.value.active
        if (existing == null || existing.record.cancelled) {
            val record = SosRecords.sign(
                SosRecord(
                    beaconId = TurturCrypto.randomId(SosRecords.BEACON_ID_BYTES),
                    name = displayName(fallbackName),
                    seq = 1,
                    contacts = smsContacts(),
                    signerPkB64 = _state.value.profile.signPkB64,
                ),
                store.signSecret(),
            )
            saveActive(ActiveSos(record = record, startedUnix = now()))
        }
        startService(context, SosService.ACTION_START)
    }

    /** Bring a still-active beacon back after a reboot or app update. */
    fun resume(context: Context) {
        if (_state.value.active == null) return
        startService(context, SosService.ACTION_START)
    }

    fun cancelSos(context: Context) {
        val active = _state.value.active ?: return
        if (active.record.cancelled) return
        val record = SosRecords.next(active.record, store.signSecret(), cancelled = true)
        saveActive(active.copy(record = record, cancelUntilUnix = now() + CANCEL_BROADCAST_MS, flockNotified = false))
        startService(context, SosService.ACTION_UPDATE)
    }

    fun updateFix(lat: Double?, lon: Double?, accuracyM: Float?, fixUnix: Long?, battery: Int?): ActiveSos? {
        val active = _state.value.active ?: return null
        val record = SosRecords.next(
            active.record,
            store.signSecret(),
            lat = lat ?: active.record.lat,
            lon = lon ?: active.record.lon,
            accuracyM = if (lat != null) accuracyM else active.record.accuracyM,
            fixUnix = if (lat != null) fixUnix else active.record.fixUnix,
            battery = battery ?: active.record.battery,
        )
        val next = active.copy(record = record)
        saveActive(next)
        return next
    }

    fun markFlockNotified() {
        val active = _state.value.active ?: return
        saveActive(active.copy(flockNotified = true))
    }

    fun clearActive() = saveActive(null)

    private fun saveActive(active: ActiveSos?) {
        store.saveActive(active)
        _state.value = _state.value.copy(active = active)
    }

    private fun startService(context: Context, action: String) {
        context.startForegroundService(Intent(context, SosService::class.java).setAction(action))
    }

    // ---- listener side ----

    fun isOwnBeacon(beaconId: String): Boolean = _state.value.active?.record?.beaconId == beaconId

    fun knownSeq(beaconId: String): Long? =
        _state.value.sightings.find { it.record.beaconId == beaconId }?.record?.seq

    /**
     * Merge a heard record. [viaFlock] means it arrived inside a trip-signed pigeon, so the
     * signer key becomes trusted for later beacon sightings.
     */
    @Synchronized
    fun onHeard(record: SosRecord, rssi: Int?, viaFlock: Boolean) {
        if (isOwnBeacon(record.beaconId)) return
        val t = now()
        val current = pruned(_state.value.sightings, t)
        val previous = current.find { it.record.beaconId == record.beaconId }
        if (viaFlock && record.signerPkB64 !in _state.value.profile.trustedPks) {
            editProfile { it.copy(trustedPks = (it.trustedPks + record.signerPkB64).takeLast(64)) }
        }
        if (!SosRecords.isNewer(previous?.record, record)) {
            if (previous != null && rssi != null) {
                saveSightings(current.map {
                    if (it === previous) it.copy(lastSeenUnix = t, lastRssi = rssi) else it
                })
            }
            return
        }
        val alert = AlertPolicy.shouldAlert(previous, record, t)
        val sighting = SosSighting(
            record = record,
            verified = isTrusted(record.signerPkB64),
            firstSeenUnix = previous?.firstSeenUnix ?: t,
            lastSeenUnix = t,
            lastAlertUnix = if (alert) t else previous?.lastAlertUnix ?: 0,
            lastRssi = rssi ?: previous?.lastRssi,
            smsDone = previous?.smsDone ?: false,
        )
        saveSightings(listOf(sighting) + current.filterNot { it.record.beaconId == record.beaconId })
        SosNotifier.showHeard(appContext, sighting, loud = alert)
        SmsBridge.refreshCarrying(appContext)
    }

    /** Beacon still in range with nothing new to read: keep the finder's signal fresh. */
    @Synchronized
    fun touch(beaconId: String, rssi: Int) {
        val current = _state.value.sightings
        if (current.none { it.record.beaconId == beaconId }) return
        val t = now()
        _state.value = _state.value.copy(
            sightings = current.map {
                if (it.record.beaconId == beaconId) it.copy(lastSeenUnix = t, lastRssi = rssi) else it
            },
        )
    }

    fun markSmsDone(beaconId: String) {
        saveSightings(_state.value.sightings.map {
            if (it.record.beaconId == beaconId) it.copy(smsDone = true) else it
        })
        SmsBridge.refreshCarrying(appContext)
    }

    fun dismiss(beaconId: String) {
        saveSightings(_state.value.sightings.filterNot { it.record.beaconId == beaconId })
        SosNotifier.cancelHeard(appContext, beaconId)
        SmsBridge.refreshCarrying(appContext)
    }

    /** Sightings with contacts still waiting for someone to text them. */
    fun carrying(): List<SosSighting> = _state.value.sightings.filter {
        !it.smsDone && !it.record.cancelled && it.record.contacts.isNotEmpty()
    }

    private fun isTrusted(pk: String): Boolean {
        val p = _state.value.profile
        return pk in p.trustedPks || p.contacts.any { it.signPkB64 == pk }
    }

    private fun saveSightings(items: List<SosSighting>) {
        store.saveSightings(items)
        _state.value = _state.value.copy(sightings = items)
    }

    private fun pruned(items: List<SosSighting>, t: Long) =
        items.filter { t - it.lastSeenUnix <= SIGHTING_KEEP_MS }

    private fun now() = System.currentTimeMillis()
}
