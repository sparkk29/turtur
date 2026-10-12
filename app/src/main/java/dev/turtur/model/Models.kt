package dev.turtur.model

import kotlinx.serialization.Serializable
import java.util.UUID

const val MAX_FLOCK = 12
const val NOTE_MAX_CHARS = 500

@Serializable
enum class Habitat {
    WILD,
    CROWD,
    ;

    fun defaultTtlMs(): Long = if (this == WILD) 48L * 3600_000 else 8L * 3600_000

    fun scanPeriodMs(): Long = if (this == WILD) 8_000 else 6_000

    /** Extra settle time after scan for GATT transfers during Feed. */
    fun feedSettleMs(): Long = if (this == WILD) 7_000 else 8_000

    fun opportunisticScanMs(): Long = if (this == WILD) 4_000 else 5_000

    fun opportunisticRestMs(): Long = if (this == WILD) 45_000 else 18_000
}

@Serializable
enum class OkayStatus { FINE, DELAYED, STAY_PUT }

@Serializable
enum class PigeonKind { NOTE, MEETUP, OKAY, ACK, ROSTER, SOS }

@Serializable
enum class FlightState { ON_THE_WING, LANDED, RETURNED, LOST }

@Serializable
data class Member(
    val loftId: String,
    val name: String,
    val band: String,
    val sealPkB64: String,
    val signPkB64: String,
)

@Serializable
data class Invite(
    val v: Int = 1,
    val tripId: String,
    val joinKeyB64: String,
    val name: String,
    val habitat: Habitat,
    val creator: Member,
)

@Serializable
data class InnerPayload(
    val senderLoftId: String,
    val kind: PigeonKind,
    val note: String? = null,
    val meetupId: String? = null,
    val place: String? = null,
    val timeLocal: String? = null,
    val okay: OkayStatus? = null,
    val ackPigeonId: String? = null,
    val members: List<Member>? = null,
    val sos: SosRecord? = null,
    val sigB64: String,
)

@Serializable
data class Envelope(
    val pigeonId: String,
    val tripId: String,
    val destLoftId: String,
    val priority: Int,
    val ttlUnix: Long,
    val sealedB64: String,
)

@Serializable
data class OutboxRecord(
    val pigeonId: String,
    val destLoftId: String,
    val kind: PigeonKind,
    val summary: String,
    val createdUnix: Long,
    val ttlUnix: Long,
    var state: FlightState = FlightState.ON_THE_WING,
)

@Serializable
data class Arrival(
    val pigeonId: String,
    val senderLoftId: String,
    val kind: PigeonKind,
    val summary: String,
    val landedUnix: Long,
    val meetupId: String? = null,
    val place: String? = null,
    val timeLocal: String? = null,
    val okay: OkayStatus? = null,
    val note: String? = null,
    val sos: SosRecord? = null,
)

@Serializable
data class TripSnapshot(
    val tripId: String,
    val joinKeyB64: String,
    val name: String,
    val habitat: Habitat,
    val me: Member,
    val sealSkB64: String,
    val signSkB64: String,
    val members: List<Member>,
    val warned: Boolean = false,
    val rosterEpoch: Long = 1,
)

data class SignedRoster(
    val members: List<Member>,
    val epoch: Long,
    val signerLoftId: String,
    val sigB64: String,
)

const val MAX_SOS_CONTACTS = 3

@Serializable
data class EmergencyContact(
    val name: String,
    val phone: String = "",
    val signPkB64: String? = null,
)

/** What one phone shows as a QR so others can trust its SOS signatures and text its owner. */
@Serializable
data class GuardianCard(
    val v: Int = 1,
    val name: String,
    val phone: String = "",
    val signPkB64: String,
)

@Serializable
data class GuardianProfile(
    val name: String = "",
    val phone: String = "",
    val signPkB64: String = "",
    val contacts: List<EmergencyContact> = emptyList(),
    val samaritan: Boolean = false,
    val trustedPks: List<String> = emptyList(),
)

/**
 * Signed distress beacon payload. Readable by any Turtur phone in range: it carries a
 * position and phone numbers on purpose, so strangers can find the hiker and text help.
 */
@Serializable
data class SosRecord(
    val beaconId: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val fixUnix: Long? = null,
    val battery: Int? = null,
    val seq: Long,
    val contacts: List<String> = emptyList(),
    val cancelled: Boolean = false,
    val signerPkB64: String,
    val sigB64: String = "",
)

@Serializable
data class ActiveSos(
    val record: SosRecord,
    val startedUnix: Long,
    val cancelUntilUnix: Long? = null,
    val flockNotified: Boolean = false,
)

@Serializable
data class SosSighting(
    val record: SosRecord,
    val verified: Boolean,
    val firstSeenUnix: Long,
    val lastSeenUnix: Long,
    val lastAlertUnix: Long = 0,
    val lastRssi: Int? = null,
    val smsDone: Boolean = false,
)

val BANDS = listOf("copper", "cedar", "dusk", "moss", "ash", "wine", "bone", "river")

fun newPigeonId(): String = UUID.randomUUID().toString()

fun OkayStatus.label(): String = when (this) {
    OkayStatus.FINE -> "I'm fine"
    OkayStatus.DELAYED -> "Delayed"
    OkayStatus.STAY_PUT -> "Stay put"
}
