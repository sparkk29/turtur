package dev.turtur.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.turtur.invite.InviteCodec
import dev.turtur.model.MAX_SOS_CONTACTS
import dev.turtur.model.SosRecord
import dev.turtur.model.SosSighting
import dev.turtur.sos.Geo
import dev.turtur.sos.GuardianCardCodec
import dev.turtur.sos.RssiMeter
import dev.turtur.sos.SmsText
import dev.turtur.sos.SosNotifier
import dev.turtur.sos.SosRuntime
import dev.turtur.ui.theme.Copper
import dev.turtur.ui.theme.Ink
import dev.turtur.ui.theme.Moss
import dev.turtur.ui.theme.Paper
import dev.turtur.ui.theme.Wine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val EMERGENCY_NUMBER = "112"

@Composable
internal fun SosRow(state: UiState, vm: TurturViewModel) {
    val active = state.sos.active?.record?.cancelled == false
    val heard = state.sos.sightings.count { !it.record.cancelled }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(if (active) "SOS on" else "SOS", color = Wine) { vm.go(Screen.SOS) }
        Tile("Safety") { vm.go(Screen.SAFETY) }
        Tile("Heard ($heard)") { vm.go(Screen.HEARD) }
    }
}

@Composable
internal fun SosScreen(state: UiState, vm: TurturViewModel, onNeedLocation: () -> Unit) {
    val context = LocalContext.current
    val active = state.sos.active
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Back(if (vm.homeScreen() == Screen.HOME) "Home" else "Gate") { vm.go(vm.homeScreen()) }
        Text("SOS", style = MaterialTheme.typography.headlineMedium, color = Paper)
        Text(
            "If you have any mobile signal, call emergency services first. Turtur only reaches Turtur phones within Bluetooth range.",
            color = Paper.copy(alpha = 0.8f),
        )
        CopperButton("Call $EMERGENCY_NUMBER") { dial(context) }
        when {
            active == null || active.record.cancelled && active.cancelUntilUnix == null -> {
                IdleSos(state, vm, onNeedLocation)
            }
            !active.record.cancelled -> {
                Card {
                    Text("Beacon on since ${prettyTime(active.startedUnix)}", color = Wine)
                    Text(fixLine(active.record), color = Paper)
                    active.record.battery?.let { Text("Battery $it%", color = Paper.copy(alpha = 0.7f)) }
                    Text("Update #${active.record.seq}", color = Paper.copy(alpha = 0.55f))
                }
                if (!state.locationGranted) {
                    CopperButton("Allow location so finders get your GPS") { onNeedLocation() }
                }
                Text(
                    "Screen off is fine. Any Turtur phone that passes within Bluetooth range is alerted and can walk to you. Whoever hears you is asked to text your emergency contacts once they have signal.",
                    color = Paper.copy(alpha = 0.7f),
                )
                HoldButton("Hold to say I'm safe", color = Moss) { vm.cancelSos() }
            }
            else -> {
                Card {
                    Text("Telling nearby phones you are safe", color = Moss)
                    active.cancelUntilUnix?.let {
                        Text("The beacon stops at ${prettyTime(it)}.", color = Paper.copy(alpha = 0.7f))
                    }
                }
                HoldButton("Hold 3 s to start a new SOS") { vm.startSos() }
            }
        }
    }
}

@Composable
private fun IdleSos(state: UiState, vm: TurturViewModel, onNeedLocation: () -> Unit) {
    val dialable = state.sos.profile.contacts.count { it.phone.isNotBlank() }.coerceAtMost(MAX_SOS_CONTACTS)
    if (!state.locationGranted) {
        CopperButton("Allow location so finders get your GPS") { onNeedLocation() }
    }
    HoldButton("Hold 3 s to start SOS beacon") { vm.startSos() }
    Text(
        "While the beacon is on, any Turtur phone in range can read your name, GPS position, and the phone numbers of your emergency contacts.",
        color = Paper.copy(alpha = 0.7f),
    )
    if (dialable == 0) {
        Text("No emergency contacts yet, so nobody can be texted for you.", color = Wine)
    } else {
        Text("$dialable emergency contact(s) will be texted by whoever carries your SOS.", color = Paper.copy(alpha = 0.7f))
    }
    TextButton(onClick = { vm.go(Screen.SAFETY) }) { Text("Emergency contacts", color = Copper) }
}

@Composable
internal fun SafetyScreen(
    state: UiState,
    vm: TurturViewModel,
    cameraGranted: Boolean,
    onNeedCamera: () -> Unit,
    onNeedLocation: () -> Unit,
) {
    val profile = state.sos.profile
    val card = remember(profile.name, profile.phone, profile.signPkB64) {
        SosRuntime.card()?.let { InviteCodec.qrBitmap(GuardianCardCodec.encode(it)) }
    }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Back(if (vm.homeScreen() == Screen.HOME) "Home" else "Gate") { vm.go(vm.homeScreen()) }
        Text("Safety", style = MaterialTheme.typography.headlineMedium, color = Paper)

        Text("You, in an SOS", color = Copper)
        Field("Your name for SOS", state.sosName, vm::onSosName)
        Field("Your phone (optional, on your card)", state.sosPhone, vm::onSosPhone)
        CopperButton("Save") { vm.saveIdentity() }

        Text("Good Samaritan", color = Copper)
        FilterChip(
            selected = profile.samaritan,
            onClick = { vm.setSamaritan(!profile.samaritan) },
            label = { Text("Listen for SOS from any Turtur user") },
            colors = chipColors(),
        )
        Text(
            "Your phone alerts you when a Turtur SOS beacon is in Bluetooth range, even when Turtur is closed. Trip members always hear each other. Low battery cost.",
            color = Paper.copy(alpha = 0.7f),
        )
        if (!state.locationGranted) {
            TextButton(onClick = onNeedLocation) { Text("Allow location for the finder", color = Copper) }
        }

        Text("Emergency contacts (first $MAX_SOS_CONTACTS with a phone get texted)", color = Copper)
        if (profile.contacts.isEmpty()) {
            Text("None yet.", color = Paper.copy(alpha = 0.7f))
        }
        profile.contacts.forEachIndexed { i, c ->
            Card {
                Text(c.name, color = Paper)
                Text(c.phone.ifBlank { "no phone" }, color = Paper.copy(alpha = 0.7f))
                if (c.signPkB64 != null) Text("Card scanned · their SOS shows as verified", color = Moss)
                TextButton(onClick = { vm.removeContact(i) }) { Text("Remove", color = Wine) }
            }
        }
        Field("Contact name", state.contactName, vm::onContactName)
        Field("Contact phone (+country code)", state.contactPhone, vm::onContactPhone)
        CopperButton("Add contact") { vm.addContact() }
        CopperButton(if (state.scanningCard) "Stop scanning" else "Scan a guardian card") { vm.toggleCardScan() }
        if (state.scanningCard) {
            LaunchedEffect(Unit) { if (!cameraGranted) onNeedCamera() }
            if (cameraGranted) {
                Box(Modifier.height(220.dp)) { QrScanner(onFound = vm::addContactFromCard) }
            }
        }

        Text("Your guardian card", color = Copper)
        Text(
            "Hiking partners scan this so your SOS shows as verified on their phones. Scan theirs too.",
            color = Paper.copy(alpha = 0.7f),
        )
        if (card != null) {
            Image(bitmap = card.asImageBitmap(), contentDescription = "Guardian card QR")
        } else {
            Text("Save your name to make a card.", color = Paper.copy(alpha = 0.7f))
        }
    }
}

@Composable
internal fun HeardScreen(state: UiState, vm: TurturViewModel) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Back(if (vm.homeScreen() == Screen.HOME) "Home" else "Gate") { vm.go(vm.homeScreen()) }
        Text("SOS heard", style = MaterialTheme.typography.headlineMedium, color = Paper)
        if (state.sos.sightings.isEmpty()) {
            Text(
                if (state.sos.profile.samaritan) "No SOS beacons heard." else "No SOS beacons heard. Turn on Good Samaritan in Safety to hear strangers too.",
                color = Paper.copy(alpha = 0.7f),
            )
        }
        state.sos.sightings.forEach { s -> SightingCard(s, vm) }
    }
}

@Composable
private fun SightingCard(s: SosSighting, vm: TurturViewModel) {
    val r = s.record
    Card {
        Text(
            if (r.cancelled) "${r.name} is safe" else "SOS · ${r.name}",
            color = if (r.cancelled) Moss else Wine,
        )
        Text(
            if (s.verified) "Verified contact" else "Unverified Turtur SOS. Could be a mistake or a prank; still worth a careful look.",
            color = if (s.verified) Moss else Paper.copy(alpha = 0.7f),
        )
        Text(fixLine(r), color = Paper)
        val signal = s.lastRssi?.let { " · signal $it dBm" }.orEmpty()
        Text("Last heard ${prettyTime(s.lastSeenUnix)}$signal", color = Paper.copy(alpha = 0.55f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!r.cancelled) TextButton(onClick = { vm.openFinder(r.beaconId) }) { Text("Find", color = Copper) }
            if (!r.cancelled && r.contacts.isNotEmpty()) {
                TextButton(onClick = { vm.textContacts(r.beaconId) }) { Text("Text contacts", color = Copper) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!r.cancelled && r.contacts.isNotEmpty() && !s.smsDone) {
                TextButton(onClick = { vm.markSmsDone(r.beaconId) }) { Text("I texted them", color = Copper) }
            }
            if (s.smsDone) Text("Contacts texted", color = Moss, modifier = Modifier.align(Alignment.CenterVertically))
            TextButton(onClick = { vm.dismissSighting(r.beaconId) }) { Text("Dismiss", color = Wine) }
        }
    }
}

@Composable
internal fun FinderScreen(state: UiState, vm: TurturViewModel, onNeedLocation: () -> Unit) {
    val context = LocalContext.current
    val beaconId = state.finder.beaconId
    val sighting = state.sos.sightings.find { it.record.beaconId == beaconId }
    val r = sighting?.record
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Back("Heard") { vm.go(Screen.HEARD) }
        Text("Finder", style = MaterialTheme.typography.headlineMedium, color = Paper)
        if (r == null) {
            Text("SOS beacon nearby. Details not read yet; keep walking toward a stronger signal.", color = Wine)
        } else {
            Text(if (r.cancelled) "${r.name} says they are safe" else "Looking for ${r.name}", color = if (r.cancelled) Moss else Wine)
            Text(if (sighting.verified) "Verified contact" else "Unverified Turtur SOS", color = Paper.copy(alpha = 0.7f))
        }

        val rssi = state.finder.rssi
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color(0xFF2A211C), RoundedCornerShape(4.dp)),
        ) {
            if (rssi != null) {
                Box(
                    Modifier
                        .fillMaxWidth(RssiMeter.fill(rssi))
                        .fillMaxHeight()
                        .background(Copper, RoundedCornerShape(4.dp)),
                )
            }
        }
        Text(
            rssi?.let { "${RssiMeter.band(it)} · ${it.toInt()} dBm" } ?: "Listening for the beacon…",
            color = Paper,
        )
        Text(
            "Signal jumps around and your body blocks it. Walk 10–20 m, turn slowly, and follow the trend.",
            color = Paper.copy(alpha = 0.6f),
        )

        if (r?.lat != null && r.lon != null) {
            val myLat = state.finder.myLat
            val myLon = state.finder.myLon
            val age = SosNotifier.minutesAgo(r.fixUnix)
            if (myLat != null && myLon != null) {
                val d = Geo.distanceM(myLat, myLon, r.lat, r.lon)
                val b = Geo.bearingDeg(myLat, myLon, r.lat, r.lon)
                Text(
                    "Their last GPS fix is ${Geo.prettyDistance(d)} ${Geo.cardinal(b)} (${b.toInt()}°) of you · $age min old",
                    color = Paper,
                )
            } else {
                Text("Their last GPS fix: ${SmsText.coords(r.lat, r.lon)} · $age min old", color = Paper)
                if (!state.locationGranted) {
                    TextButton(onClick = onNeedLocation) { Text("Allow location to see distance and direction", color = Copper) }
                } else {
                    Text("Waiting for your own GPS fix…", color = Paper.copy(alpha = 0.6f))
                }
            }
            CopperButton("Open in maps") { openMap(context, r) }
        } else if (r != null) {
            Text("Their beacon has no GPS fix. Use the signal meter.", color = Paper.copy(alpha = 0.7f))
        }
        if (r != null && !r.cancelled && r.contacts.isNotEmpty()) {
            CopperButton("Text their emergency contacts") { vm.textContacts(r.beaconId) }
        }
        CopperButton("Call $EMERGENCY_NUMBER") { dial(context) }
    }
}

/** Long-press guard so a pocket tap cannot start or stop an SOS. */
@Composable
private fun HoldButton(text: String, color: Color = Wine, holdMs: Long = 3_000, onFire: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val fire by rememberUpdatedState(onFire)
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .pointerInput(holdMs) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        val start = System.currentTimeMillis()
                        while (true) {
                            val p = (System.currentTimeMillis() - start).toFloat() / holdMs
                            progress = p.coerceAtMost(1f)
                            if (p >= 1f) {
                                fire()
                                break
                            }
                            delay(16)
                        }
                    }
                    tryAwaitRelease()
                    job.cancel()
                    progress = 0f
                })
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(color, RoundedCornerShape(6.dp)),
        )
        Text(
            text,
            color = if (progress > 0.5f) Ink else Paper,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private fun fixLine(r: SosRecord): String {
    val lat = r.lat
    val lon = r.lon
    if (lat == null || lon == null) return "Waiting for a GPS fix…"
    val acc = r.accuracyM?.let { " ±${it.toInt()} m" }.orEmpty()
    return "GPS ${SmsText.coords(lat, lon)}$acc · ${SosNotifier.minutesAgo(r.fixUnix)} min ago"
}

private fun dial(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$EMERGENCY_NUMBER")))
    }
}

private fun openMap(context: Context, r: SosRecord) {
    val lat = r.lat ?: return
    val lon = r.lon ?: return
    val c = SmsText.coords(lat, lon)
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$c?q=$c(${Uri.encode(r.name)})")))
    }
}
