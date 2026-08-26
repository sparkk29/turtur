package dev.turtur.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.turtur.LoftRuntime
import dev.turtur.invite.InviteCodec
import dev.turtur.model.FlightState
import dev.turtur.model.Habitat
import dev.turtur.model.OkayStatus
import dev.turtur.model.PigeonKind
import dev.turtur.model.label
import dev.turtur.ui.theme.Copper
import dev.turtur.ui.theme.Ink
import dev.turtur.ui.theme.Moss
import dev.turtur.ui.theme.Paper
import dev.turtur.ui.theme.Wine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TurturApp(state: UiState, vm: TurturViewModel, cameraGranted: Boolean, onNeedCamera: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(20.dp),
    ) {
        state.error?.let {
            Text(it, color = Wine, modifier = Modifier.padding(bottom = 12.dp))
        }
        val trip = state.engine.trip
        if (trip != null && !trip.warned) {
            Warning(vm)
        } else when (state.screen) {
            Screen.GATE -> Gate(state, vm)
            Screen.CREATE -> Create(state, vm)
            Screen.JOIN -> Join(state, vm, cameraGranted, onNeedCamera)
            Screen.HOME -> Home(state, vm)
            Screen.NOTE -> Note(state, vm)
            Screen.MEETUP -> Meetup(state, vm)
            Screen.OKAY -> Okay(vm)
            Screen.ARRIVALS -> Arrivals(state, vm)
            Screen.WING -> Wing(state, vm)
            Screen.ROSTER -> Roster(state, vm)
            Screen.SETTINGS -> Settings(state, vm)
        }
    }
}

@Composable
private fun Gate(state: UiState, vm: TurturViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Turtur", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, color = Paper)
        Text("Camp mail that flies in pockets.", color = Copper)
        Text(
            "A letter moves when your flock is in range. No cell tower, no server.",
            color = Paper.copy(alpha = 0.8f),
        )
        Field("Your name on this trip", state.displayName, vm::onDisplayName)
        CopperButton("Start a trip") { vm.go(Screen.CREATE) }
        CopperButton("Join a trip") { vm.go(Screen.JOIN) }
    }
}

@Composable
private fun Create(state: UiState, vm: TurturViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Back("Gate") { vm.go(Screen.GATE) }
        Text("Start a trip", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Paper)
        Field("Trip name", state.tripName, vm::onTripName)
        HabitatPicker(state.habitat, vm::onHabitat)
        CopperButton("Create loft") { vm.createTrip() }
    }
}

@Composable
private fun Join(state: UiState, vm: TurturViewModel, cameraGranted: Boolean, onNeedCamera: () -> Unit) {
    LaunchedEffect(Unit) { if (!cameraGranted) onNeedCamera() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        Back("Gate") { vm.go(Screen.GATE) }
        Text("Join a trip", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Paper)
        if (cameraGranted) {
            Box(Modifier.height(220.dp)) {
                QrScanner(onFound = vm::joinFromScan)
            }
        }
        Field("Or paste invite", state.inviteText, vm::onInvite)
        CopperButton("Join") { vm.joinTrip() }
    }
}

@Composable
private fun Home(state: UiState, vm: TurturViewModel) {
    val trip = state.engine.trip ?: return
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(trip.name, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Paper)
        Text(
            "${trip.me.name} · ${trip.me.band} · ${trip.habitat.name.lowercase()} · ${trip.members.size} birds",
            color = Copper,
        )
        Text(
            if (state.loftLive) {
                val nearby = state.engine.inRange
                if (nearby > 0) "Loft open · $nearby in range · syncing when birds pass"
                else "Loft open · listening for flock (screen can be off)"
            } else {
                "Loft closed · grant Bluetooth to fly"
            },
            color = if (state.loftLive) Moss else Wine,
        )
        Text(
            state.engine.lastFeedNote
                ?: "No flock in range. That's normal on the trail. Feed at camp.",
            color = Paper.copy(alpha = 0.75f),
        )
        CopperButton(if (state.feeding) "Feeding…" else "Feed the flock", enabled = !state.feeding) {
            vm.feed()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Note") { vm.go(Screen.NOTE) }
            Tile("Meetup") { vm.go(Screen.MEETUP) }
            Tile("Okay") { vm.go(Screen.OKAY) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Arrivals (${state.engine.arrivals.size})") { vm.go(Screen.ARRIVALS) }
            Tile("On the wing (${state.engine.outbox.count { it.state == FlightState.ON_THE_WING }})") {
                vm.go(Screen.WING)
            }
        }
        TextButton(onClick = { vm.go(Screen.ROSTER) }) { Text("Flock QR", color = Copper) }
        TextButton(onClick = { vm.go(Screen.SETTINGS) }) { Text("Trip settings", color = Copper) }
    }
}

@Composable
private fun Note(state: UiState, vm: TurturViewModel) {
    val others = state.engine.trip?.members?.filter { it.loftId != state.engine.trip?.me?.loftId }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Back("Home") { vm.go(Screen.HOME) }
        Text("Note", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Paper)
        if (others.isEmpty()) {
            Text("Stand near another bird so roster can finish.", color = Paper.copy(alpha = 0.7f))
        }
        others.forEach { member ->
            FilterChip(
                selected = state.destLoftId == member.loftId,
                onClick = { vm.onDest(member.loftId) },
                label = { Text("${member.name} · ${member.band}") },
                colors = chipColors(),
            )
        }
        Field("Letter (500)", state.noteText, vm::onNote)
        CopperButton("Release") { vm.releaseNote() }
    }
}

@Composable
private fun Meetup(state: UiState, vm: TurturViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Back("Home") { vm.go(Screen.HOME) }
        Text("Meetup", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Paper)
        Field("Place (red banner, Hut 3)", state.meetupPlace, vm::onPlace)
        Field("Time (local)", state.meetupTime, vm::onTime)
        CopperButton("Release to flock") { vm.releaseMeetup() }
    }
}

@Composable
private fun Okay(vm: TurturViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Back("Home") { vm.go(Screen.HOME) }
        Text("Okay", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Paper)
        Text("This goes to your flock, not rescue services.", color = Paper.copy(alpha = 0.7f))
        CopperButton(OkayStatus.FINE.label()) { vm.releaseOkay(OkayStatus.FINE) }
        CopperButton(OkayStatus.DELAYED.label()) { vm.releaseOkay(OkayStatus.DELAYED) }
        CopperButton(OkayStatus.STAY_PUT.label()) { vm.releaseOkay(OkayStatus.STAY_PUT) }
    }
}

@Composable
private fun Arrivals(state: UiState, vm: TurturViewModel) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Back("Home") { vm.go(Screen.HOME) }
        Text("Arrivals", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Paper)
        if (state.engine.arrivals.isEmpty()) {
            Text("Nothing has landed yet.", color = Paper.copy(alpha = 0.7f))
        }
        state.engine.arrivals.forEach { item ->
            val sender = state.engine.trip?.members?.find { it.loftId == item.senderLoftId }?.name ?: "loft"
            Card {
                Text(kindLabel(item.kind), color = Copper)
                Text(item.summary, color = Paper)
                Text("$sender · ${prettyTime(item.landedUnix)}", color = Paper.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun Wing(state: UiState, vm: TurturViewModel) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Back("Home") { vm.go(Screen.HOME) }
        Text("On the wing", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Paper)
        if (state.engine.outbox.isEmpty()) {
            Text("No pigeons released yet.", color = Paper.copy(alpha = 0.7f))
        }
        state.engine.outbox.reversed().forEach { item ->
            val dest = state.engine.trip?.members?.find { it.loftId == item.destLoftId }?.name ?: "loft"
            Card {
                Text(item.state.name.lowercase().replace('_', ' '), color = Copper)
                Text(item.summary, color = Paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$dest · ${kindLabel(item.kind)}", color = Paper.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun Roster(state: UiState, vm: TurturViewModel) {
    val invite = remember(state.engine.trip) { LoftRuntime.engine.invite() }
    val bitmap: Bitmap? = remember(invite) { invite?.let { InviteCodec.qrBitmap(it) } }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Back("Home") { vm.go(Screen.HOME) }
        Text("Flock", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Paper)
        bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "Flock QR")
        }
        Text("Treat this QR like a house key while the trip is live.", color = Paper.copy(alpha = 0.7f))
        state.engine.trip?.members?.forEach { member ->
            val you = if (member.loftId == state.engine.trip?.me?.loftId) " (you)" else ""
            Text("${member.name} · ${member.band}$you", color = Paper)
        }
        Text(
            "Roster epoch ${state.engine.trip?.rosterEpoch ?: 0} · keys finish when you stand near another bird.",
            color = Paper.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun Settings(state: UiState, vm: TurturViewModel) {
    val trip = state.engine.trip ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Back("Home") { vm.go(Screen.HOME) }
        Text("Trip settings", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Paper)
        HabitatPicker(trip.habitat, vm::setHabitat)
        Text("Faster flight, worse battery, easier to notice.", color = Paper.copy(alpha = 0.65f))
        Text("End trip only molts this phone.", color = Paper.copy(alpha = 0.65f))
        CopperButton("End trip") { vm.molt() }
    }
}

@Composable
private fun Warning(vm: TurturViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.96f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Before you fly", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Paper)
        Text(
            "Turtur does not use the internet. Notes are sealed for the recipient. Nearby Bluetooth devices can still see that Turtur is on. This is not an emergency beacon. Mail can be late or never land.",
            color = Paper.copy(alpha = 0.85f),
        )
        CopperButton("I understand") { vm.acknowledgeWarning() }
    }
}

@Composable
private fun HabitatPicker(selected: Habitat, onSelect: (Habitat) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Habitat.entries.forEach { habitat ->
            FilterChip(
                selected = selected == habitat,
                onClick = { onSelect(habitat) },
                label = { Text(if (habitat == Habitat.WILD) "Wild" else "Crowd") },
                colors = chipColors(),
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Copper,
            unfocusedBorderColor = Paper.copy(alpha = 0.3f),
            focusedLabelColor = Copper,
            unfocusedLabelColor = Paper.copy(alpha = 0.6f),
            focusedTextColor = Paper,
            unfocusedTextColor = Paper,
            cursorColor = Copper,
        ),
    )
}

@Composable
private fun CopperButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = Ink),
        shape = RoundedCornerShape(4.dp),
    ) { Text(text) }
}

@Composable
private fun Tile(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Ink,
        modifier = Modifier
            .background(Copper, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A211C), RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = { content() },
    )
}

@Composable
private fun Back(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("← $label", color = Copper) }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Copper,
    selectedLabelColor = Ink,
    containerColor = Color(0xFF2A211C),
    labelColor = Paper,
)

private fun kindLabel(kind: PigeonKind) = when (kind) {
    PigeonKind.NOTE -> "Note"
    PigeonKind.MEETUP -> "Meetup"
    PigeonKind.OKAY -> "Okay"
    PigeonKind.ACK -> "Landed"
    PigeonKind.ROSTER -> "Roster"
}

private fun prettyTime(unix: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(unix))
