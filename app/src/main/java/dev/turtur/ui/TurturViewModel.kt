package dev.turtur.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.turtur.LoftRuntime
import dev.turtur.flock.EngineState
import dev.turtur.model.Habitat
import dev.turtur.model.OkayStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen {
    GATE, CREATE, JOIN, HOME, NOTE, MEETUP, OKAY, ARRIVALS, WING, ROSTER, SETTINGS,
}

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
    val error: String? = null,
    val feeding: Boolean = false,
    val loftLive: Boolean = false,
)

class TurturViewModel(app: Application) : AndroidViewModel(app) {
    private val engine get() = LoftRuntime.engine
    private val radio get() = LoftRuntime.radio

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init {
        LoftRuntime.init(app)
        engine.load()
        refresh(if (engine.state.trip == null) Screen.GATE else Screen.HOME)
        if (engine.state.trip != null) startRadioIfPossible()
    }

    fun go(screen: Screen) {
        _ui.update { it.copy(screen = screen, error = null) }
    }

    fun onDisplayName(v: String) = _ui.update { it.copy(displayName = v) }
    fun onTripName(v: String) = _ui.update { it.copy(tripName = v) }
    fun onHabitat(v: Habitat) = _ui.update { it.copy(habitat = v) }
    fun onInvite(v: String) = _ui.update { it.copy(inviteText = v) }
    fun onNote(v: String) = _ui.update { it.copy(noteText = v.take(500)) }
    fun onDest(v: String) = _ui.update { it.copy(destLoftId = v) }
    fun onPlace(v: String) = _ui.update { it.copy(meetupPlace = v) }
    fun onTime(v: String) = _ui.update { it.copy(meetupTime = v) }

    fun createTrip() {
        runCatching {
            engine.createTrip(_ui.value.tripName, _ui.value.displayName, _ui.value.habitat)
            startRadioIfPossible()
            refresh(Screen.HOME)
        }.onFailure { show(it) }
    }

    fun joinTrip() {
        runCatching {
            engine.joinTrip(_ui.value.inviteText, _ui.value.displayName)
            startRadioIfPossible()
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
            engine.releaseNote(dest, _ui.value.noteText)
            _ui.update { it.copy(noteText = "") }
            refresh(Screen.HOME)
        }.onFailure { show(it) }
    }

    fun releaseMeetup() {
        runCatching {
            engine.releaseMeetup(_ui.value.meetupPlace, _ui.value.meetupTime)
            refresh(Screen.HOME)
        }.onFailure { show(it) }
    }

    fun releaseOkay(status: OkayStatus) {
        runCatching {
            engine.releaseOkay(status)
            refresh(Screen.HOME)
        }.onFailure { show(it) }
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
        radio.feed { peers, given, taken ->
            viewModelScope.launch {
                engine.markFed(peers, given, taken)
                refresh(screen = _ui.value.screen)
                _ui.update { it.copy(feeding = false) }
            }
        }
    }

    fun molt() {
        LoftRuntime.stopLoftService(getApplication())
        radio.stopLoft()
        engine.molt()
        _ui.value = UiState(screen = Screen.GATE, loftLive = false)
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
        _ui.update {
            it.copy(
                screen = screen,
                engine = engine.state,
                loftLive = engine.state.trip != null && radio.hasPermissions(),
                destLoftId = it.destLoftId
                    ?: engine.state.trip?.members?.firstOrNull { m ->
                        m.loftId != engine.state.trip?.me?.loftId
                    }?.loftId,
            )
        }
    }

    private fun show(t: Throwable) {
        _ui.update { it.copy(error = t.message ?: t::class.java.simpleName) }
    }

    /** Keep the loft service running across activity recreate; do not stop radio here. */
    override fun onCleared() {
        super.onCleared()
    }
}
