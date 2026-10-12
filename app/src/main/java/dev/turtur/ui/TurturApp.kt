package dev.turtur.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.turtur.LoftRuntime
import dev.turtur.R
import dev.turtur.invite.InviteCodec
import dev.turtur.model.FlightState
import dev.turtur.model.Habitat
import dev.turtur.model.OkayStatus
import dev.turtur.model.PigeonKind
import dev.turtur.ui.theme.Glacier
import dev.turtur.ui.theme.Line
import dev.turtur.ui.theme.Mist
import dev.turtur.ui.theme.Night
import dev.turtur.ui.theme.Pine
import dev.turtur.ui.theme.Rose
import dev.turtur.ui.theme.Signal
import dev.turtur.ui.theme.Slate
import dev.turtur.ui.theme.Snow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TurturApp(
    state: UiState,
    vm: TurturViewModel,
    cameraGranted: Boolean,
    onNeedCamera: () -> Unit,
    onNeedLocation: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Night, contentColor = Snow) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                InfoCard(Icons.Outlined.ErrorOutline, it, tint = Rose)
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
                Screen.POCKET -> Pocket(state, vm)
                Screen.ROSTER -> Roster(state, vm)
                Screen.SETTINGS -> Settings(state, vm)
                Screen.SOS -> SosScreen(state, vm, onNeedLocation)
                Screen.SAFETY -> SafetyScreen(state, vm, cameraGranted, onNeedCamera, onNeedLocation)
                Screen.HEARD -> HeardScreen(state, vm)
                Screen.FINDER -> FinderScreen(state, vm, onNeedLocation)
            }
        }
    }
}

@Composable
private fun Gate(state: UiState, vm: TurturViewModel) {
    ScreenColumn {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(92.dp),
            )
            Spacer(Modifier.width(2.dp))
            Column {
                Text("Turtur", style = MaterialTheme.typography.displaySmall)
                Text("Camp mail that flies in pockets.", style = MaterialTheme.typography.bodyMedium, color = Glacier)
            }
        }
        Text(
            "Send short, sealed letters to your group with no signal and no server. Phones pass them along over Bluetooth when people meet.",
            style = MaterialTheme.typography.bodyMedium,
            color = Mist,
        )
        Section("How it works")
        Panel {
            Step(1, "Start or join a trip", "One person starts it. Everyone else scans their flock QR.")
            Step(2, "Write a letter", "A note, a meetup, or a quick okay. Only the person it's for can read it.")
            Step(3, "It hops phone to phone", "Any trip member who walks past carries it closer. No tower needed.")
        }
        Spacer(Modifier.height(2.dp))
        Field(
            "Your name on this trip",
            state.displayName,
            vm::onDisplayName,
            supporting = "Shown to your group. No phone number or account needed.",
        )
        PrimaryButton("Start a trip", icon = Icons.Outlined.Add) { vm.go(Screen.CREATE) }
        SecondaryButton("Join with a flock QR", icon = Icons.Outlined.QrCodeScanner) { vm.go(Screen.JOIN) }
        Section("Safety", "Works with or without a trip.")
        SosRow(state, vm)
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Glacier.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", color = Glacier, style = MaterialTheme.typography.labelMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Mist)
        }
    }
}

@Composable
private fun Create(state: UiState, vm: TurturViewModel) {
    ScreenColumn {
        ScreenHeader(
            "Start a trip",
            "Your phone becomes the first loft. Others join by scanning your flock QR.",
            onBack = { vm.go(Screen.GATE) },
        )
        Field(
            "Trip name",
            state.tripName,
            vm::onTripName,
            supporting = "Everyone in the trip sees this, like \"Valley of Flowers\".",
        )
        Section("Habitat", "How busy the radio is. You can change it later.")
        HabitatPicker(state.habitat, vm::onHabitat)
        PrimaryButton("Create trip", icon = Icons.Outlined.Add) { vm.createTrip() }
    }
}

@Composable
private fun Join(state: UiState, vm: TurturViewModel, cameraGranted: Boolean, onNeedCamera: () -> Unit) {
    LaunchedEffect(Unit) { if (!cameraGranted) onNeedCamera() }
    ScreenColumn {
        ScreenHeader(
            "Join a trip",
            "Ask any trip member to open Flock QR on their phone, then point your camera at it.",
            onBack = { vm.go(Screen.GATE) },
        )
        if (cameraGranted) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Glacier.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            ) {
                QrScanner(onFound = vm::joinFromScan)
            }
        } else {
            Panel {
                Text("Camera is off", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Allow the camera to scan a flock QR, or paste an invite below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                )
                SecondaryButton("Allow camera", icon = Icons.Outlined.PhotoCamera, onClick = onNeedCamera)
            }
        }
        Section("No camera?")
        Field(
            "Paste invite",
            state.inviteText,
            vm::onInvite,
            supporting = "The text inside a flock QR, if someone sent it to you.",
        )
        PrimaryButton("Join trip") { vm.joinTrip() }
    }
}

@Composable
private fun Home(state: UiState, vm: TurturViewModel) {
    val trip = state.engine.trip ?: return
    val carrying = state.engine.pocket.count { it.destLoftId != trip.me.loftId }
    val onWing = state.engine.outbox.count { it.state == FlightState.ON_THE_WING }
    ScreenColumn {
        ScreenHeader(trip.name) {
            IconButton(onClick = { vm.go(Screen.ROSTER) }) {
                Icon(Icons.Outlined.QrCode2, contentDescription = "Flock QR", tint = Snow)
            }
            IconButton(onClick = { vm.go(Screen.SETTINGS) }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Trip settings", tint = Snow)
            }
        }
        Panel {
            val nearby = state.engine.inRange
            when {
                !state.loftLive -> StatusPill("Loft closed · allow Bluetooth", Rose)
                nearby > 0 -> StatusPill("Loft open · $nearby nearby", Pine)
                else -> StatusPill("Loft open · listening", Pine)
            }
            Text(
                "${trip.me.name} · band ${trip.me.band} · ${trip.members.size} in flock · ${trip.habitat.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
            )
            Text(
                state.engine.lastFeedNote
                    ?: "Nobody in range right now. That's normal on the trail; mail moves when you meet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.batteryHint.isNotBlank()) {
                Text(state.batteryHint, style = MaterialTheme.typography.bodySmall, color = Mist)
            }
        }
        PrimaryButton(
            if (state.feeding) "Feeding…" else "Feed the flock",
            icon = Icons.Outlined.Sync,
            enabled = !state.feeding,
        ) { vm.feed() }
        Text(
            "Tap when you're together at camp to swap all mail at once. It also happens on its own when people pass.",
            style = MaterialTheme.typography.bodySmall,
            color = Mist,
        )
        Section("Mail")
        TileRow {
            ActionTile(Icons.Outlined.Mail, "Note", "Private letter to one person", Modifier.weight(1f)) {
                vm.go(Screen.NOTE)
            }
            ActionTile(Icons.Outlined.Place, "Meetup", "Place and time for everyone", Modifier.weight(1f)) {
                vm.go(Screen.MEETUP)
            }
        }
        TileRow {
            ActionTile(Icons.Outlined.CheckCircle, "Okay", "Quick \"I'm fine\" or \"Delayed\"", Modifier.weight(1f)) {
                vm.go(Screen.OKAY)
            }
            ActionTile(
                Icons.Outlined.Inbox,
                "Arrivals",
                "Letters that reached you",
                Modifier.weight(1f),
                badge = state.engine.arrivals.size,
            ) { vm.go(Screen.ARRIVALS) }
        }
        TileRow {
            ActionTile(
                Icons.AutoMirrored.Outlined.Send,
                "On the wing",
                "Sent, waiting to land",
                Modifier.weight(1f),
                badge = onWing,
            ) { vm.go(Screen.WING) }
            ActionTile(
                Icons.Outlined.Backpack,
                "Pocket",
                "Sealed mail you carry for others",
                Modifier.weight(1f),
                badge = carrying,
            ) { vm.go(Screen.POCKET) }
        }
        Section("Safety", "Your SOS beacon and emergency contacts.")
        SosRow(state, vm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Note(state: UiState, vm: TurturViewModel) {
    val others = state.engine.trip?.members?.filter { it.loftId != state.engine.trip?.me?.loftId }.orEmpty()
    ScreenColumn {
        ScreenHeader(
            "Note",
            "Sealed so only the person you pick can read it. Phones that carry it can't see inside.",
            onBack = { vm.go(Screen.HOME) },
        )
        Section("To")
        if (others.isEmpty()) {
            InfoCard(
                Icons.Outlined.Info,
                "Nobody else is in your roster yet. Stand near another trip member for a minute so your phones can swap keys.",
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            others.forEach { member ->
                Choice("${member.name} · ${member.band}", state.destLoftId == member.loftId) {
                    vm.onDest(member.loftId)
                }
            }
        }
        Field(
            "Letter",
            state.noteText,
            vm::onNote,
            supporting = "Short is best: plans, places, times.",
            maxLength = 500,
            singleLine = false,
        )
        TtlPicker(state.ttlHours, vm::onTtlHours)
        PrimaryButton("Release", icon = Icons.AutoMirrored.Outlined.Send, enabled = state.destLoftId != null) {
            vm.releaseNote()
        }
    }
}

@Composable
private fun Meetup(state: UiState, vm: TurturViewModel) {
    ScreenColumn {
        ScreenHeader(
            "Meetup",
            "Goes to the whole flock. Good for \"regroup at the red banner at 18:00\".",
            onBack = { vm.go(Screen.HOME) },
        )
        Field("Place", state.meetupPlace, vm::onPlace, supporting = "Something easy to find: Hut 3, the red banner, the trailhead sign.")
        Field("Time", state.meetupTime, vm::onTime, supporting = "Local time, like 18:30 or \"after sunset\".")
        TtlPicker(state.ttlHours, vm::onTtlHours)
        PrimaryButton("Release to flock", icon = Icons.AutoMirrored.Outlined.Send) { vm.releaseMeetup() }
    }
}

@Composable
private fun Okay(vm: TurturViewModel) {
    ScreenColumn {
        ScreenHeader(
            "Okay",
            "A one-tap status for your flock. This does not reach rescue services.",
            onBack = { vm.go(Screen.HOME) },
        )
        ChoiceRow("I'm fine", "All good. Carry on with the plan.", icon = Icons.Outlined.CheckCircle, accent = Pine) {
            vm.releaseOkay(OkayStatus.FINE)
        }
        ChoiceRow("Delayed", "Running late but safe. Don't wait up.", icon = Icons.Outlined.Schedule) {
            vm.releaseOkay(OkayStatus.DELAYED)
        }
        ChoiceRow("Stay put", "Stay where you are. I'll come to you.", icon = Icons.Outlined.WrongLocation) {
            vm.releaseOkay(OkayStatus.STAY_PUT)
        }
    }
}

@Composable
private fun Arrivals(state: UiState, vm: TurturViewModel) {
    ScreenColumn {
        ScreenHeader("Arrivals", "Letters that opened on this phone.", onBack = { vm.go(Screen.HOME) })
        if (state.engine.arrivals.isEmpty()) {
            EmptyState(
                Icons.Outlined.Inbox,
                "Nothing has landed yet",
                "Mail arrives when someone carrying it comes into Bluetooth range. Feeding the flock at camp speeds it up.",
            )
        }
        state.engine.arrivals.forEach { item ->
            val sender = state.engine.trip?.members?.find { it.loftId == item.senderLoftId }?.name ?: "loft"
            ListRow(
                icon = kindIcon(item.kind),
                title = kindLabel(item.kind),
                body = item.summary,
                meta = "from $sender · ${prettyTime(item.landedUnix)}",
                accent = kindColor(item.kind),
            )
        }
    }
}

@Composable
private fun Pocket(state: UiState, vm: TurturViewModel) {
    val me = state.engine.trip?.me
    val carrying = state.engine.pocket.filter { me != null && it.destLoftId != me.loftId }
    ScreenColumn {
        ScreenHeader(
            "Pocket",
            "Sealed mail you're carrying for other trip members. You can't read it; you're the courier.",
            onBack = { vm.go(Screen.HOME) },
        )
        if (carrying.isEmpty()) {
            EmptyState(
                Icons.Outlined.Backpack,
                "Empty pocket",
                "When you meet trip members, your phone may pick up mail for others and carry it along.",
            )
        }
        carrying.forEach { env ->
            val dest = state.engine.trip?.members?.find { it.loftId == env.destLoftId }
            val leftH = ((env.ttlUnix - System.currentTimeMillis()) / 3_600_000L).coerceAtLeast(0)
            ListRow(
                icon = Icons.Outlined.Lock,
                title = "For ${dest?.name ?: "loft"} · ${dest?.band ?: "?"}",
                meta = "Priority ${env.priority} · about ${leftH}h left",
                trailing = {
                    TextButton(onClick = { vm.dropPigeon(env.pigeonId) }) { Text("Set down", color = Rose) }
                },
            )
        }
    }
}

@Composable
private fun Wing(state: UiState, vm: TurturViewModel) {
    ScreenColumn {
        ScreenHeader(
            "On the wing",
            "Everything you sent. \"Landed\" means the recipient's phone confirmed it.",
            onBack = { vm.go(Screen.HOME) },
        )
        if (state.engine.outbox.isEmpty()) {
            EmptyState(
                Icons.AutoMirrored.Outlined.Send,
                "Nothing sent yet",
                "Write a Note, Meetup, or Okay from Home.",
            )
        }
        state.engine.outbox.reversed().forEach { item ->
            val dest = state.engine.trip?.members?.find { it.loftId == item.destLoftId }?.name ?: "flock"
            val (label, color) = when (item.state) {
                FlightState.ON_THE_WING -> "On the wing" to Glacier
                FlightState.LANDED -> "Landed" to Pine
                FlightState.RETURNED -> "Returned" to Rose
                FlightState.LOST -> "Lost" to Rose
            }
            ListRow(
                icon = kindIcon(item.kind),
                title = label,
                body = item.summary,
                meta = "${kindLabel(item.kind)} to $dest",
                accent = color,
            )
        }
    }
}

@Composable
private fun Roster(state: UiState, vm: TurturViewModel) {
    val invite = remember(state.engine.trip) { LoftRuntime.engine.invite() }
    val bitmap: Bitmap? = remember(invite) { invite?.let { InviteCodec.qrBitmap(it) } }
    val members = state.engine.trip?.members.orEmpty()
    ScreenColumn {
        ScreenHeader(
            "Flock QR",
            "New members scan this to join. Anyone in the trip can show it.",
            onBack = { vm.go(Screen.HOME) },
        )
        bitmap?.let { QrPanel(it, "Flock QR", size = 240.dp) }
        InfoCard(
            Icons.Outlined.Lock,
            "Treat this QR like a house key while the trip is live. Anyone who scans it can join your trip.",
        )
        Section("Members (${members.size})")
        Panel {
            members.forEach { member ->
                val you = member.loftId == state.engine.trip?.me?.loftId
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Outlined.Person, if (you) Pine else Glacier, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(member.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        if (you) "band ${member.band} · you" else "band ${member.band}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist,
                    )
                }
            }
        }
        Text(
            "Roster epoch ${state.engine.trip?.rosterEpoch ?: 0}. New members' keys spread when people stand near each other.",
            style = MaterialTheme.typography.bodySmall,
            color = Mist,
        )
    }
}

@Composable
private fun Settings(state: UiState, vm: TurturViewModel) {
    val trip = state.engine.trip ?: return
    var confirm by remember { mutableStateOf(false) }
    ScreenColumn {
        ScreenHeader("Trip settings", onBack = { vm.go(Screen.HOME) })
        Section("Habitat", "Crowd moves mail faster but uses more battery and is easier to notice.")
        HabitatPicker(trip.habitat, vm::setHabitat)
        if (state.batteryHint.isNotBlank()) {
            Text(state.batteryHint, style = MaterialTheme.typography.bodySmall, color = Mist)
        }
        Section(
            "End trip",
            "Wipes this phone's trip keys, roster, and mail. Others keep the trip. Your SOS name and contacts stay.",
        )
        DangerButton("End trip on this phone") { confirm = true }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Slate,
            titleContentColor = Snow,
            textContentColor = Mist,
            title = { Text("End this trip?") },
            text = {
                Text("Mail you are carrying for others and letters you haven't read will be lost. This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.molt()
                }) { Text("End trip", color = Rose) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel", color = Glacier) }
            },
        )
    }
}

@Composable
private fun Warning(vm: TurturViewModel) {
    ScreenColumn {
        Spacer(Modifier.height(12.dp))
        ScreenHeader("Before you fly", "Turtur works differently from normal messaging. Five things to know:")
        Panel {
            WarnRow(Icons.Outlined.CloudOff, "No internet, no server", "Letters only move when trip members' phones come within Bluetooth range.")
            WarnRow(Icons.Outlined.Lock, "Sealed for one person", "Phones that carry your note can't read it. Only the recipient's phone can open it.")
            WarnRow(Icons.Outlined.Visibility, "Turtur is visible nearby", "Bluetooth devices around you can tell Turtur is running, but not what it carries.")
            WarnRow(Icons.Outlined.Schedule, "Mail can be late", "Some letters land hours later, or never. Don't rely on them for timing.")
            WarnRow(
                Icons.Outlined.Sos,
                "SOS is best effort",
                "It only reaches Turtur phones in Bluetooth range. If you have any signal, call 112 first.",
                accent = Signal,
            )
        }
        PrimaryButton("I understand") { vm.acknowledgeWarning() }
    }
}

@Composable
private fun WarnRow(icon: ImageVector, title: String, body: String, accent: Color = Glacier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        IconBubble(icon, accent)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Mist)
        }
    }
}

@Composable
private fun HabitatPicker(selected: Habitat, onSelect: (Habitat) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceRow(
            "Wild",
            "Trails and treks. Saves battery; mail moves a bit slower.",
            selected = selected == Habitat.WILD,
        ) { onSelect(Habitat.WILD) }
        ChoiceRow(
            "Crowd",
            "Festivals and busy camps. Faster sync; uses more battery.",
            selected = selected == Habitat.CROWD,
        ) { onSelect(Habitat.CROWD) }
    }
}

@Composable
private fun TtlPicker(selectedHours: Int?, onSelect: (Int?) -> Unit) {
    Section("Keep trying for", "If it hasn't landed by then, it comes back as returned.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null to "Trip default", 4 to "4h", 24 to "24h", 72 to "72h").forEach { (hours, label) ->
            Choice(label, selectedHours == hours) { onSelect(hours) }
        }
    }
}

private fun kindLabel(kind: PigeonKind) = when (kind) {
    PigeonKind.NOTE -> "Note"
    PigeonKind.MEETUP -> "Meetup"
    PigeonKind.OKAY -> "Okay"
    PigeonKind.ACK -> "Landed"
    PigeonKind.ROSTER -> "Roster"
    PigeonKind.SOS -> "SOS"
}

private fun kindIcon(kind: PigeonKind): ImageVector = when (kind) {
    PigeonKind.NOTE -> Icons.Outlined.Mail
    PigeonKind.MEETUP -> Icons.Outlined.Place
    PigeonKind.OKAY -> Icons.Outlined.CheckCircle
    PigeonKind.ACK -> Icons.Outlined.DoneAll
    PigeonKind.ROSTER -> Icons.Outlined.Groups
    PigeonKind.SOS -> Icons.Outlined.Sos
}

private fun kindColor(kind: PigeonKind): Color = when (kind) {
    PigeonKind.SOS -> Signal
    PigeonKind.OKAY, PigeonKind.ACK -> Pine
    else -> Glacier
}

internal fun prettyTime(unix: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(unix))
