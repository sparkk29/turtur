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

    fun scanPeriodMs(): Long = if (this == WILD) 8_000 else 4_000
}

@Serializable
enum class OkayStatus { FINE, DELAYED, STAY_PUT }

@Serializable
enum class PigeonKind { NOTE, MEETUP, OKAY, ACK, ROSTER }

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
)

val BANDS = listOf("copper", "cedar", "dusk", "moss", "ash", "wine", "bone", "river")

fun newPigeonId(): String = UUID.randomUUID().toString()

fun OkayStatus.label(): String = when (this) {
    OkayStatus.FINE -> "I'm fine"
    OkayStatus.DELAYED -> "Delayed"
    OkayStatus.STAY_PUT -> "Stay put"
}
