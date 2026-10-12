package dev.turtur.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.turtur.invite.InviteCodec
import dev.turtur.model.EmergencyContact
import dev.turtur.model.MAX_SOS_CONTACTS
import dev.turtur.model.SosRecord
import dev.turtur.model.SosSighting
import dev.turtur.sos.Geo
import dev.turtur.sos.GuardianCardCodec
import dev.turtur.sos.RssiMeter
import dev.turtur.sos.SmsText
import dev.turtur.sos.SosNotifier
import dev.turtur.sos.SosRuntime
import dev.turtur.ui.theme.Glacier
import dev.turtur.ui.theme.Line
import dev.turtur.ui.theme.Mist
import dev.turtur.ui.theme.Night
import dev.turtur.ui.theme.Pine
import dev.turtur.ui.theme.Rose
import dev.turtur.ui.theme.Signal
import dev.turtur.ui.theme.SlateHigh
import dev.turtur.ui.theme.Snow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val EMERGENCY_NUMBER = "112"

@Composable
internal fun SosRow(state: UiState, vm: TurturViewModel) {
    val active = state.sos.active?.record?.cancelled == false
    val heard = state.sos.sightings.count { !it.record.cancelled }
    TileRow {
        ActionTile(
            Icons.Outlined.Sos,
            if (active) "SOS on" else "SOS",
            if (active) "Beacon is broadcasting" else "Call for help nearby",
            Modifier.weight(1f),
            accent = Signal,
        ) { vm.go(Screen.SOS) }
        ActionTile(
            Icons.Outlined.HealthAndSafety,
            "Safety",
            "Contacts and your card",
            Modifier.weight(1f),
        ) { vm.go(Screen.SAFETY) }
        ActionTile(
            Icons.Outlined.Hearing,
            "Heard",
            "SOS near you",
            Modifier.weight(1f),
            badge = heard,
            accent = if (heard > 0) Signal else Glacier,
        ) { vm.go(Screen.HEARD) }
    }
}

@Composable
internal fun SosScreen(state: UiState, vm: TurturViewModel, onNeedLocation: () -> Unit) {
    val context = LocalContext.current
    val active = state.sos.active
    ScreenColumn {
        ScreenHeader(
            "SOS",
            "If you have any mobile signal, call emergency services first. Turtur only reaches Turtur phones within Bluetooth range.",
            onBack = { vm.go(vm.homeScreen()) },
        )
        PrimaryButton("Call $EMERGENCY_NUMBER", icon = Icons.Outlined.Call) { dial(context) }
        when {
            active == null || active.record.cancelled && active.cancelUntilUnix == null -> {
                IdleSos(state, vm, onNeedLocation)
            }
            !active.record.cancelled -> {
                Panel(accent = Signal) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill("Beacon on", Signal)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "since ${prettyTime(active.startedUnix)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Mist,
                        )
                    }
                    InfoLine(Icons.Outlined.MyLocation, fixLine(active.record))
                    active.record.battery?.let { InfoLine(Icons.Outlined.BatteryFull, "Battery $it%") }
                    InfoLine(Icons.Outlined.Sync, "Update #${active.record.seq} · GPS refreshes every 10 min")
                }
                if (!state.locationGranted) {
                    SecondaryButton("Allow location so finders get your GPS", icon = Icons.Outlined.MyLocation) {
                        onNeedLocation()
                    }
                }
                InfoCard(
                    Icons.Outlined.Info,
                    "Screen off is fine. Any Turtur phone that passes within Bluetooth range is alerted and can walk to you. Whoever hears you is asked to text your emergency contacts once they have signal.",
                )
                HoldButton("Hold 3 s to say I'm safe", color = Pine, icon = Icons.Outlined.Check) { vm.cancelSos() }
            }
            else -> {
                Panel(accent = Pine) {
                    StatusPill("Telling nearby phones you're safe", Pine)
                    active.cancelUntilUnix?.let {
                        Text(
                            "The beacon stops at ${prettyTime(it)}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mist,
                        )
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
        SecondaryButton("Allow location so finders get your GPS", icon = Icons.Outlined.MyLocation) {
            onNeedLocation()
        }
    }
    HoldButton("Hold 3 s to start SOS beacon") { vm.startSos() }
    Text(
        "Holding for 3 seconds keeps a pocket tap from setting it off.",
        style = MaterialTheme.typography.bodySmall,
        color = Mist,
    )
    Section("What happens")
    Panel {
        InfoLine(
            Icons.Outlined.Bluetooth,
            "Your phone becomes a Bluetooth beacon. Turtur phones in range are alerted, even with the app closed.",
        )
        InfoLine(Icons.Outlined.Groups, "If you're on a trip, your flock also gets it as top-priority mail.")
        if (dialable == 0) {
            InfoLine(Icons.Outlined.Sms, "No emergency contacts yet, so nobody can be texted for you.", color = Rose)
        } else {
            InfoLine(
                Icons.Outlined.Sms,
                "Whoever hears you is asked to text your $dialable emergency contact${if (dialable == 1) "" else "s"}.",
            )
        }
    }
    InfoCard(
        Icons.Outlined.Visibility,
        "While the beacon is on, any Turtur phone in range can read your name, GPS position, and your emergency contacts' numbers.",
        tint = Mist,
    )
    SecondaryButton("Set up emergency contacts", icon = Icons.Outlined.HealthAndSafety) { vm.go(Screen.SAFETY) }
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
    val phoneKeys = KeyboardOptions(keyboardType = KeyboardType.Phone)
    ScreenColumn {
        ScreenHeader(
            "Safety",
            "Set this up before you head out. It's only used if you start an SOS or hear someone else's.",
            onBack = { vm.go(vm.homeScreen()) },
        )

        Section("Your SOS identity")
        Panel {
            Field("Your name", state.sosName, vm::onSosName, supporting = "Shown to anyone who hears your SOS.")
            Field(
                "Your phone (optional)",
                state.sosPhone,
                vm::onSosPhone,
                supporting = "Only goes on your guardian card, so partners can call you.",
                keyboard = phoneKeys,
            )
            PrimaryButton("Save", icon = Icons.Outlined.Check) { vm.saveIdentity() }
        }

        Section("Good Samaritan")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Listen for SOS from anyone", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Alert me when any Turtur user's SOS is in Bluetooth range, even with Turtur closed. Low battery cost.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = profile.samaritan,
                    onCheckedChange = vm::setSamaritan,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Night,
                        checkedTrackColor = Glacier,
                        uncheckedThumbColor = Mist,
                        uncheckedTrackColor = SlateHigh,
                        uncheckedBorderColor = Line,
                    ),
                )
            }
            Text("Trip members always hear each other's SOS.", style = MaterialTheme.typography.bodySmall, color = Mist)
            if (!state.locationGranted) {
                LinkButton("Allow location so the finder can show distance", onClick = onNeedLocation)
            }
        }

        Section(
            "Emergency contacts",
            "The first $MAX_SOS_CONTACTS with a phone number get texted by whoever hears your SOS.",
        )
        Panel {
            if (profile.contacts.isEmpty()) {
                Text("No contacts yet.", style = MaterialTheme.typography.bodyMedium, color = Mist)
            }
            profile.contacts.forEachIndexed { i, c -> ContactRow(c) { vm.removeContact(i) } }
            if (profile.contacts.isNotEmpty()) HorizontalDivider(color = Line)
            Field("Contact name", state.contactName, vm::onContactName)
            Field(
                "Contact phone",
                state.contactPhone,
                vm::onContactPhone,
                supporting = "Include the country code, like +91 98111 22233.",
                keyboard = phoneKeys,
            )
            PrimaryButton("Add contact", icon = Icons.Outlined.PersonAdd) { vm.addContact() }
            SecondaryButton(
                if (state.scanningCard) "Stop scanning" else "Scan a guardian card",
                icon = Icons.Outlined.QrCodeScanner,
            ) { vm.toggleCardScan() }
            if (state.scanningCard) {
                LaunchedEffect(Unit) { if (!cameraGranted) onNeedCamera() }
                if (cameraGranted) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    ) { QrScanner(onFound = vm::addContactFromCard) }
                }
            }
        }

        Section(
            "Your guardian card",
            "Swap cards with hiking partners: scan theirs, and let them scan yours. Your SOS then shows as verified on their phone.",
        )
        if (card != null) {
            QrPanel(card, "Guardian card QR")
        } else {
            Text("Save your name above to make a card.", style = MaterialTheme.typography.bodyMedium, color = Mist)
        }
    }
}

@Composable
private fun ContactRow(c: EmergencyContact, onRemove: () -> Unit) {
    val verified = c.signPkB64 != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(if (verified) Icons.Outlined.Verified else Icons.Outlined.Person, if (verified) Pine else Glacier)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.titleSmall)
            Text(
                if (verified) "${c.phone.ifBlank { "no phone" }} · card scanned" else c.phone.ifBlank { "no phone" },
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Delete, contentDescription = "Remove ${c.name}", tint = Rose)
        }
    }
}

@Composable
internal fun HeardScreen(state: UiState, vm: TurturViewModel) {
    ScreenColumn {
        ScreenHeader(
            "SOS heard",
            "Distress beacons your phone picked up. Check on them if you safely can, and pass the message on.",
            onBack = { vm.go(vm.homeScreen()) },
        )
        if (state.sos.sightings.isEmpty()) {
            if (state.sos.profile.samaritan) {
                EmptyState(
                    Icons.Outlined.Hearing,
                    "No SOS heard",
                    "Your phone is listening. If a Turtur SOS comes into range, you'll get a loud alert.",
                )
            } else {
                EmptyState(
                    Icons.Outlined.Hearing,
                    "No SOS heard",
                    "Trip members' SOS always reaches you. Turn on Good Samaritan in Safety to hear anyone's.",
                )
                SecondaryButton("Open Safety", icon = Icons.Outlined.HealthAndSafety) { vm.go(Screen.SAFETY) }
            }
        }
        state.sos.sightings.forEach { s -> SightingCard(s, vm) }
    }
}

@Composable
private fun SightingCard(s: SosSighting, vm: TurturViewModel) {
    val r = s.record
    Panel(accent = if (r.cancelled) Pine else Signal) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill(if (r.cancelled) "Safe" else "SOS", if (r.cancelled) Pine else Signal)
            StatusPill(if (s.verified) "Verified" else "Unverified", if (s.verified) Pine else Mist)
            Spacer(Modifier.weight(1f))
            Text(prettyTime(s.lastSeenUnix), style = MaterialTheme.typography.bodySmall, color = Mist)
        }
        Text(if (r.cancelled) "${r.name} says they're safe" else r.name, style = MaterialTheme.typography.titleMedium)
        if (!s.verified && !r.cancelled) {
            Text(
                "Not one of your guardian cards. Could be a mistake or a prank, but still worth a careful look.",
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
            )
        }
        InfoLine(Icons.Outlined.MyLocation, fixLine(r))
        s.lastRssi?.let { InfoLine(Icons.Outlined.Bluetooth, "Signal $it dBm when last heard") }
        if (s.smsDone) InfoLine(Icons.Outlined.Check, "You texted their contacts", color = Pine)
        if (!r.cancelled) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Find", icon = Icons.Outlined.Explore, color = Signal, modifier = Modifier.weight(1f)) {
                    vm.openFinder(r.beaconId)
                }
                if (r.contacts.isNotEmpty()) {
                    SecondaryButton("Text contacts", icon = Icons.Outlined.Sms, modifier = Modifier.weight(1f)) {
                        vm.textContacts(r.beaconId)
                    }
                }
            }
        }
        Row {
            if (!r.cancelled && r.contacts.isNotEmpty() && !s.smsDone) {
                LinkButton("I texted them") { vm.markSmsDone(r.beaconId) }
            }
            Spacer(Modifier.weight(1f))
            LinkButton("Dismiss", color = Rose) { vm.dismissSighting(r.beaconId) }
        }
    }
}

@Composable
internal fun FinderScreen(state: UiState, vm: TurturViewModel, onNeedLocation: () -> Unit) {
    val context = LocalContext.current
    val beaconId = state.finder.beaconId
    val sighting = state.sos.sightings.find { it.record.beaconId == beaconId }
    val r = sighting?.record
    ScreenColumn {
        ScreenHeader(
            "Finder",
            "Walk slowly and watch the meter. A stronger signal means you're getting closer.",
            onBack = { vm.go(Screen.HEARD) },
        )
        if (r == null) {
            InfoCard(
                Icons.Outlined.Info,
                "An SOS beacon is nearby, but its details haven't been read yet. Keep walking toward a stronger signal.",
                tint = Signal,
            )
        } else {
            Panel(accent = if (r.cancelled) Pine else Signal) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(if (r.cancelled) "Safe" else "SOS", if (r.cancelled) Pine else Signal)
                    StatusPill(if (sighting.verified) "Verified" else "Unverified", if (sighting.verified) Pine else Mist)
                }
                Text(
                    if (r.cancelled) "${r.name} says they're safe" else "Looking for ${r.name}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Section("Bluetooth signal")
        Panel {
            val rssi = state.finder.rssi
            val fill = rssi?.let { RssiMeter.fill(it) } ?: 0f
            val heat = lerp(Glacier, Signal, fill)
            Text(
                rssi?.let { RssiMeter.band(it) } ?: "Listening for the beacon…",
                style = MaterialTheme.typography.headlineMedium,
                color = if (rssi != null) heat else Mist,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(SlateHigh, RoundedCornerShape(50)),
            ) {
                if (rssi != null) {
                    Box(
                        Modifier
                            .fillMaxWidth(fill)
                            .fillMaxHeight()
                            .background(heat, RoundedCornerShape(50)),
                    )
                }
            }
            rssi?.let { Text("${it.toInt()} dBm", style = MaterialTheme.typography.bodySmall, color = Mist) }
            Text(
                "Signal jumps around and your body blocks it. Walk 10–20 m, turn slowly, and follow the trend.",
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
            )
        }

        if (r?.lat != null && r.lon != null) {
            val myLat = state.finder.myLat
            val myLon = state.finder.myLon
            val age = SosNotifier.minutesAgo(r.fixUnix)
            Section("Their last GPS fix")
            Panel {
                if (myLat != null && myLon != null) {
                    val d = Geo.distanceM(myLat, myLon, r.lat, r.lon)
                    val b = Geo.bearingDeg(myLat, myLon, r.lat, r.lon)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Outlined.Explore, Glacier, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "${Geo.prettyDistance(d)} ${Geo.cardinal(b)}",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                "Bearing ${b.toInt()}° from you · fix is $age min old",
                                style = MaterialTheme.typography.bodySmall,
                                color = Mist,
                            )
                        }
                    }
                } else {
                    InfoLine(Icons.Outlined.MyLocation, "${SmsText.coords(r.lat, r.lon)} · $age min old")
                    if (!state.locationGranted) {
                        LinkButton("Allow location to see distance and direction", onClick = onNeedLocation)
                    } else {
                        Text("Waiting for your own GPS fix…", style = MaterialTheme.typography.bodySmall, color = Mist)
                    }
                }
                SecondaryButton("Open in maps", icon = Icons.Outlined.Map) { openMap(context, r) }
            }
        } else if (r != null) {
            InfoCard(Icons.Outlined.Info, "Their beacon has no GPS fix yet. Use the signal meter.")
        }
        if (r != null && !r.cancelled && r.contacts.isNotEmpty()) {
            PrimaryButton("Text their emergency contacts", icon = Icons.Outlined.Sms) { vm.textContacts(r.beaconId) }
        }
        SecondaryButton("Call $EMERGENCY_NUMBER", icon = Icons.Outlined.Call) { dial(context) }
    }
}

/** Long-press guard so a pocket tap cannot start or stop an SOS. */
@Composable
private fun HoldButton(
    text: String,
    color: Color = Signal,
    icon: ImageVector = Icons.Outlined.Sos,
    holdMs: Long = 3_000,
    onFire: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val fire by rememberUpdatedState(onFire)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.16f))
            .border(1.5.dp, color.copy(alpha = 0.6f), shape)
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
                .background(color),
        )
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val ink = if (progress > 0.5f) Night else Snow
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
            Text(text, color = ink, style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun fixLine(r: SosRecord): String {
    val lat = r.lat
    val lon = r.lon
    if (lat == null || lon == null) return "Waiting for a GPS fix…"
    val acc = r.accuracyM?.let { " ±${it.toInt()} m" }.orEmpty()
    return "${SmsText.coords(lat, lon)}$acc · ${SosNotifier.minutesAgo(r.fixUnix)} min ago"
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
