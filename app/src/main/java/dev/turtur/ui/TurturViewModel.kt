package dev.turtur.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.turtur.LoftRuntime
import dev.turtur.flock.EngineState
import dev.turtur.model.EmergencyContact
import dev.turtur.model.Habitat
import dev.turtur.model.OkayStatus
import dev.turtur.notify.ArrivalNotifier
import dev.turtur.radio.SosBeacon
import dev.turtur.radio.SosFinderScanner
import dev.turtur.radio.SosListener
import dev.turtur.radio.SosReader
import dev.turtur.radio.TurturUuids
import dev.turtur.sos.GuardianCardCodec
import dev.turtur.sos.Locator
import dev.turtur.sos.RssiMeter
import dev.turtur.sos.SmsBridge
import dev.turtur.sos.SosNotifier
import dev.turtur.sos.SosRecords
import dev.turtur.sos.SosRuntime
import dev.turtur.sos.SosState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen {
    GATE, CREATE, JOIN, HOME, NOTE, MEETUP, OKAY, ARRIVALS, WING, POCKET, ROSTER, SETTINGS,
    SOS, SAFETY, HEARD, FINDER,
}

data class FinderState(
    val beaconId: String? = null,
    val rssi: Double? = null,
    val myLat: Double? = null,
    val myLon: Double? = null,
)

data class UiState(
    val screen: Screen = Screen.GATE,
    val engine: EngineState = EngineState(),
    val displayName: String = "",
    val tripName: String = "",
    val habitat: Habitat = Habitat.WILD,
    val inviteText: String = "",
    val noteText: String = "",
    val destLoftId: String? = null,
    val meetupPlace: String = "",
    val meetupTime: String = "",
    val ttlHours: Int? = null,
    val error: String? = null,
    val feeding: Boolean = false,
    val loftLive: Boolean = false,
    val batteryHint: String = "",
    val sos: SosState = SosState(),
    val sosName: String = "",
    val sosPhone: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    val scanningCard: Boolean = false,
    val locationGranted: Boolean = false,
    val finder: FinderState = FinderState(),
)

class TurturViewModel(app: Application) : AndroidViewModel(app) {
    private val engine get() = LoftRuntime.engine
    private val radio get() = LoftRuntime.radio

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init {
        LoftRuntime.init(app)
        engine.load()
        ArrivalNotifier.ensureChannel(app)
        engine.onArrival = { arrival ->
            val sender = engine.state.trip?.members
                ?.find { it.loftId == arrival.senderLoftId }
                ?.name
                ?: "flock"
            ArrivalNotifier.notifyArrival(app, arrival, sender)
            viewModelScope.launch { refresh(screen = _ui.value.screen) }
        }
        radio.onEngineChanged = {
            viewModelScope.launch {
                refresh(screen = _ui.value.screen)
            }
        }
        refresh(if (engine.state.trip == null) Screen.GATE else Screen.HOME)
        if (engine.state.trip != null) startRadioIfPossible()
        val profile = SosRuntime.state.value.profile
        _ui.update { it.copy(sosName = profile.name, sosPhone = profile.phone) }
        viewModelScope.launch {
            SosRuntime.state.collect { sos -> _ui.update { it.copy(sos = sos) } }
        }
    }

    fun go(screen: Screen) {
        if (screen != Screen.FINDER) stopFinder()
        _ui.update {
            it.copy(
                screen = screen,
                error = null,
                scanningCard = false,
                locationGranted = Locator.hasPermission(getApplication()),
            )
        }
    }

    fun homeScreen(): Screen = if (engine.state.trip == null) Screen.GATE else Screen.HOME

    /** Notification taps land here with a screen hint and optionally a beacon. */
    fun openFromIntent(screen: String?, beaconId: String?) {
        when (screen) {
            SosNotifier.SCREEN_SOS -> go(Screen.SOS)
            SosNotifier.SCREEN_HEARD -> go(Screen.HEARD)
            SosNotifier.SCREEN_FINDER -> beaconId?.let(::openFinder) ?: go(Screen.HEARD)
        }
    }

    // ---- safety: identity and emergency contacts ----

    fun onSosName(v: String) = _ui.update { it.copy(sosName = v.take(SosRecords.NAME_MAX)) }
    fun onSosPhone(v: String) = _ui.update { it.copy(sosPhone = v.take(24)) }
    fun onContactName(v: String) = _ui.update { it.copy(contactName = v.take(SosRecords.NAME_MAX)) }
    fun onContactPhone(v: String) = _ui.update { it.copy(contactPhone = v.take(24)) }

    fun saveIdentity() {
        runCatching {
            SosRuntime.setIdentity(_ui.value.sosName, _ui.value.sosPhone)
            val p = SosRuntime.state.value.profile
            _ui.update { it.copy(sosName = p.name, sosPhone = p.phone, error = null) }
        }.onFailure { show(it) }
    }

    fun addContact() {
        runCatching {
            SosRuntime.addContact(EmergencyContact(name = _ui.value.contactName, phone = _ui.value.contactPhone))
            _ui.update { it.copy(contactName = "", contactPhone = "", error = null) }
        }.onFailure { show(it) }
    }

    fun removeContact(index: Int) = SosRuntime.removeContact(index)

    fun toggleCardScan() = _ui.update { it.copy(scanningCard = !it.scanningCard, error = null) }

    fun addContactFromCard(raw: String) {
        runCatching {
            val card = GuardianCardCodec.decode(raw)
            require(card.signPkB64 != SosRuntime.state.value.profile.signPkB64) { "that is your own card" }
            SosRuntime.addContact(GuardianCardCodec.toContact(card))
            _ui.update { it.copy(scanningCard = false, error = null) }
        }.onFailure { t ->
            _ui.update { it.copy(scanningCard = false) }
            show(t)
        }
    }

    fun setSamaritan(on: Boolean) = SosRuntime.setSamaritan(on)

    // ---- SOS: victim side ----

    fun startSos() {
        val app = getApplication<Application>()
        if (!SosBeacon(app).hasPermissions()) {
            show(IllegalStateException("SOS needs Bluetooth permission to broadcast."))
            return
        }
        runCatching { SosRuntime.startSos(app, engine.state.trip?.me?.name) }.onFailure { show(it) }
    }

    fun cancelSos() {
        runCatching { SosRuntime.cancelSos(getApplication()) }.onFailure { show(it) }
    }

    fun onLocationResult() {
        _ui.update { it.copy(locationGranted = Locator.hasPermission(getApplication())) }
    }

    // ---- SOS: listener side ----

    fun textContacts(beaconId: String) {
        val sighting = SosRuntime.state.value.sightings.find { it.record.beaconId == beaconId } ?: return
        if (!SmsBridge.openSms(getApplication(), sighting.record)) {
            show(IllegalStateException("No SMS app could take this message."))
        }
    }

    fun markSmsDone(beaconId: String) = SosRuntime.markSmsDone(beaconId)

    fun dismissSighting(beaconId: String) {
        SosRuntime.dismiss(beaconId)
        if (_ui.value.finder.beaconId == beaconId) go(Screen.HEARD)
    }

    private var finderScanner: SosFinderScanner? = null
    private var finderFixJob: Job? = null
    private val meter = RssiMeter()

    fun openFinder(beaconId: String) {
        stopFinder()
        meter.reset()
        _ui.update {
            it.copy(
                screen = Screen.FINDER,
                error = null,
                finder = FinderState(beaconId = beaconId),
                locationGranted = Locator.hasPermission(getApplication()),
            )
        }
        val app = getApplication<Application>()
        finderScanner = SosFinderScanner(app).also { scanner ->
            scanner.start { result ->
                val advert = SosRecords.parseAdvert(
                    result.scanRecord?.getManufacturerSpecificData(TurturUuids.MANUFACTURER_ID),
                )
                if (advert?.beaconId != beaconId) return@start
                val smoothed = meter.add(result.rssi)
                _ui.update { it.copy(finder = it.finder.copy(rssi = smoothed)) }
                SosReader.handle(app, listOf(result))
            }
        }
        finderFixJob = viewModelScope.launch {
            while (true) {
                Locator.currentFix(app, timeoutMs = 20_000)?.let { loc ->
                    _ui.update { it.copy(finder = it.finder.copy(myLat = loc.latitude, myLon = loc.longitude)) }
                }
                delay(30_000)
            }
        }
    }

    private fun stopFinder() {
        finderScanner?.stop()
        finderScanner = null
        finderFixJob?.cancel()
        finderFixJob = null
    }

    fun onDisplayName(v: String) = _ui.update { it.copy(displayName = v) }
    fun onTripName(v: String) = _ui.update { it.copy(tripName = v) }
    fun onHabitat(v: Habitat) = _ui.update { it.copy(habitat = v) }
    fun onInvite(v: String) = _ui.update { it.copy(inviteText = v) }
    fun onNote(v: String) = _ui.update { it.copy(noteText = v.take(500)) }
    fun onDest(v: String) = _ui.update { it.copy(destLoftId = v) }
    fun onPlace(v: String) = _ui.update { it.copy(meetupPlace = v) }
    fun onTime(v: String) = _ui.update { it.copy(meetupTime = v) }
    fun onTtlHours(hours: Int?) = _ui.update { it.copy(ttlHours = hours) }

    fun createTrip() {
        runCatching {
            engine.createTrip(_ui.value.tripName, _ui.value.displayName, _ui.value.habitat)
            startRadioIfPossible()
            SosListener.sync(getApplication())
            refresh(Screen.HOME)
        }.onFailure { show(it) }
    }

    fun joinTrip() {
        runCatching {
            engine.joinTrip(_ui.value.inviteText, _ui.value.displayName)
            startRadioIfPossible()
            SosListener.sync(getApplication())
            refresh(Screen.HOME)
        }.onFailure { show(it) }
    }

    fun joinFromScan(value: String) {
        _ui.update { it.copy(inviteText = value) }
        joinTrip()
    }

    fun acknowledgeWarning() {
        engine.acknowledgeWarning()
        refresh(Screen.HOME)
    }

    fun releaseNote() {
        val dest = _ui.value.destLoftId ?: return
        runCatching {
            val ttlMs = _ui.value.ttlHours?.let { it * 3_600_000L }
            engine.releaseNote(dest, _ui.value.noteText, ttlMs)
            _ui.update { it.copy(noteText = "") }
            refresh(Screen.HOME)
        }.onFailure { show(it) }
    }

    fun releaseMeetup() {
        runCatching {
            val ttlMs = _ui.value.ttlHours?.let { it * 3_600_000L }
            engine.releaseMeetup(_ui.value.meetupPlace, _ui.value.meetupTime, ttlMs)
            refresh(Screen.HOME)
        }.onFailure { show(it) }
    }

    fun releaseOkay(status: OkayStatus) {
        runCatching {
            val ttlMs = _ui.value.ttlHours?.let { it * 3_600_000L }
            engine.releaseOkay(status, ttlMs)
            refresh(Screen.HOME)
        }.onFailure { show(it) }
    }

    fun dropPigeon(pigeonId: String) {
        engine.dropFromPocket(pigeonId)
        refresh(Screen.POCKET)
    }

    fun setHabitat(habitat: Habitat) {
        engine.setHabitat(habitat)
        // Restart loft so advertise cadence can follow habitat later.
        if (engine.state.trip != null) {
            LoftRuntime.stopLoftService(getApplication())
            startRadioIfPossible()
        }
        refresh(Screen.SETTINGS)
    }

    fun feed() {
        _ui.update { it.copy(feeding = true, error = null) }
        startRadioIfPossible()
        ArrivalNotifier.hapticFeed(getApplication())
        radio.feed { result ->
            viewModelScope.launch {
                engine.markFed(
                    peers = result.peers,
                    given = result.given,
                    taken = result.taken,
                    peerNames = result.peerNames,
                    failed = result.failedWrites,
                )
                if (result.taken > 0 || result.given > 0) {
                    ArrivalNotifier.hapticFeed(getApplication())
                }
                refresh(screen = _ui.value.screen)
                _ui.update { it.copy(feeding = false) }
            }
        }
    }

    fun molt() {
        LoftRuntime.stopLoftService(getApplication())
        radio.stopLoft()
        engine.molt()
        SosListener.sync(getApplication())
        val keep = _ui.value
        _ui.value = UiState(
            screen = Screen.GATE,
            loftLive = false,
            sos = keep.sos,
            sosName = keep.sosName,
            sosPhone = keep.sosPhone,
            locationGranted = keep.locationGranted,
        )
    }

    fun startRadioIfPossible() {
        if (engine.state.trip == null) return
        if (!radio.hasPermissions()) {
            _ui.update { it.copy(loftLive = false, error = "Turtur needs Bluetooth to fly.") }
            return
        }
        LoftRuntime.startLoftService(getApplication())
        _ui.update { it.copy(loftLive = true, error = null) }
    }

    fun refresh(screen: Screen = _ui.value.screen) {
        val trip = engine.state.trip
        val hint = when (trip?.habitat) {
            Habitat.CROWD -> "~3–6%/hr estimated with loft open (Crowd)"
            Habitat.WILD -> "~1–3%/hr estimated with loft open (Wild)"
            null -> ""
        }
        _ui.update {
            it.copy(
                screen = screen,
                engine = engine.state,
                loftLive = trip != null && radio.hasPermissions(),
                batteryHint = hint,
                locationGranted = Locator.hasPermission(getApplication()),
                destLoftId = it.destLoftId
                    ?: trip?.members?.firstOrNull { m ->
                        m.loftId != trip.me.loftId
                    }?.loftId,
            )
        }
    }

    private fun show(t: Throwable) {
        _ui.update { it.copy(error = t.message ?: t::class.java.simpleName) }
    }

    /** Keep the loft service running across activity recreate; do not stop radio here. */
    override fun onCleared() {
        stopFinder()
        super.onCleared()
    }
}
