package dev.turtur.flock
import dev.turtur.crypto.TurturCrypto
import dev.turtur.invite.InviteCodec
import dev.turtur.model.Arrival
import dev.turtur.model.BANDS
import dev.turtur.model.Envelope
import dev.turtur.model.FlightState
import dev.turtur.model.Habitat
import dev.turtur.model.InnerPayload
import dev.turtur.model.Invite
import dev.turtur.model.MAX_FLOCK
import dev.turtur.model.Member
import dev.turtur.model.NOTE_MAX_CHARS
import dev.turtur.model.OkayStatus
import dev.turtur.model.OutboxRecord
import dev.turtur.model.PigeonKind
import dev.turtur.model.TripSnapshot
import dev.turtur.model.label
import dev.turtur.model.newPigeonId
import dev.turtur.store.TripStore
import dev.turtur.store.b64
import dev.turtur.store.b64d
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

private val json = Json { ignoreUnknownKeys = true }

data class EngineState(
    val trip: TripSnapshot? = null,
    val pocket: List<Envelope> = emptyList(),
    val outbox: List<OutboxRecord> = emptyList(),
    val arrivals: List<Arrival> = emptyList(),
    val inRange: Int = 0,
    val lastFeedUnix: Long? = null,
    val lastFeedNote: String? = null,
)

class FlockEngine(private val store: TripStore) {
    @Volatile
    var state: EngineState = EngineState()
        private set

    fun load() {
        val trip = store.loadTrip()
        state = EngineState(
            trip = trip,
            pocket = store.loadPocket(),
            outbox = store.loadOutbox(),
            arrivals = store.loadArrivals(),
        )
        expire()
    }

    fun invite(): Invite? {
        val trip = state.trip ?: return null
        return Invite(
            tripId = trip.tripId,
            joinKeyB64 = trip.joinKeyB64,
            name = trip.name,
            habitat = trip.habitat,
            creator = trip.members.first { it.loftId == trip.me.loftId },
        )
    }

    fun inviteText(): String? = invite()?.let(InviteCodec::encode)

    fun createTrip(tripName: String, displayName: String, habitat: Habitat) {
        check(state.trip == null) { "end the current trip before starting another" }
        val seal = TurturCrypto.generateSealPair()
        val sign = TurturCrypto.generateSignPair()
        val loftId = TurturCrypto.randomId()
        val member = Member(
            loftId = loftId,
            name = displayName.trim().ifBlank { "loft" }.take(24),
            band = BANDS.random(),
            sealPkB64 = seal.public.b64(),
            signPkB64 = sign.public.b64(),
        )
        val joinKey = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val trip = TripSnapshot(
            tripId = TurturCrypto.randomId(),
            joinKeyB64 = joinKey.b64(),
            name = tripName.trim().ifBlank { "untitled trip" }.take(40),
            habitat = habitat,
            me = member,
            sealSkB64 = seal.secret.b64(),
            signSkB64 = sign.secret.b64(),
            members = listOf(member),
        )
        persist(state.copy(trip = trip))
    }

    fun joinTrip(rawInvite: String, displayName: String) {
        check(state.trip == null) { "end the current trip before joining another" }
        val invite = InviteCodec.decode(rawInvite)
        val seal = TurturCrypto.generateSealPair()
        val sign = TurturCrypto.generateSignPair()
        val loftId = TurturCrypto.randomId()
        val me = Member(
            loftId = loftId,
            name = displayName.trim().ifBlank { "loft" }.take(24),
            band = BANDS.random(),
            sealPkB64 = seal.public.b64(),
            signPkB64 = sign.public.b64(),
        )
        val trip = TripSnapshot(
            tripId = invite.tripId,
            joinKeyB64 = invite.joinKeyB64,
            name = invite.name,
            habitat = invite.habitat,
            me = me,
            sealSkB64 = seal.secret.b64(),
            signSkB64 = sign.secret.b64(),
            members = distinctMembers(listOf(invite.creator, me)),
        )
        persist(state.copy(trip = trip))
    }

    fun acknowledgeWarning() {
        val trip = state.trip ?: return
        persist(state.copy(trip = trip.copy(warned = true)))
    }

    fun setHabitat(habitat: Habitat) {
        val trip = state.trip ?: return
        persist(state.copy(trip = trip.copy(habitat = habitat)))
    }

    fun molt() {
        store.molt()
        state = EngineState()
    }

    fun releaseNote(destLoftId: String, text: String, ttlMs: Long? = null) {
        val body = text.trim()
        require(body.isNotEmpty()) { "empty note" }
        require(body.length <= NOTE_MAX_CHARS) { "note too long" }
        val dest = member(destLoftId)
        val pigeonId = newPigeonId()
        val inner = payload(
            pigeonId = pigeonId,
            destLoftId = destLoftId,
            kind = PigeonKind.NOTE,
            extra = InnerPayload(
                senderLoftId = me().loftId,
                kind = PigeonKind.NOTE,
                note = body,
                sigB64 = "",
            ),
        )
        fly(pigeonId, dest, PigeonKind.NOTE, body.take(80), 0, inner, ttlMs)
    }

    fun releaseMeetup(place: String, timeLocal: String, ttlMs: Long? = null) {
        val meetupId = newPigeonId()
        val where = place.trim().ifBlank { "here" }.take(80)
        val whenText = timeLocal.trim().ifBlank { "soon" }.take(32)
        val summary = "$where · $whenText"
        others().forEach { dest ->
            val pigeonId = newPigeonId()
            val inner = payload(
                pigeonId = pigeonId,
                destLoftId = dest.loftId,
                kind = PigeonKind.MEETUP,
                extra = InnerPayload(
                    senderLoftId = me().loftId,
                    kind = PigeonKind.MEETUP,
                    meetupId = meetupId,
                    place = where,
                    timeLocal = whenText,
                    sigB64 = "",
                ),
            )
            fly(pigeonId, dest, PigeonKind.MEETUP, summary, 1, inner, ttlMs)
        }
    }

    fun releaseOkay(status: OkayStatus, ttlMs: Long? = null) {
        others().forEach { dest ->
            val pigeonId = newPigeonId()
            val inner = payload(
                pigeonId = pigeonId,
                destLoftId = dest.loftId,
                kind = PigeonKind.OKAY,
                extra = InnerPayload(
                    senderLoftId = me().loftId,
                    kind = PigeonKind.OKAY,
                    okay = status,
                    sigB64 = "",
                ),
            )
            fly(pigeonId, dest, PigeonKind.OKAY, status.label(), 2, inner, ttlMs)
        }
    }

    fun ingestEnvelope(env: Envelope): Boolean {
        val trip = state.trip ?: return false
        expire()
        if (env.tripId != trip.tripId) return false
        if (now() > env.ttlUnix) return false
        if (state.pocket.any { it.pigeonId == env.pigeonId }) return false
        if (state.arrivals.any { it.pigeonId == env.pigeonId }) return false
        if (env.destLoftId == trip.me.loftId) {
            runCatching { openLocal(env) }.getOrElse { return false }
            persist(state)
            return true
        }
        persist(state.copy(pocket = state.pocket + env))
        return true
    }

    fun envelopesForPeer(peerLoftId: String): List<Envelope> {
        expire()
        val me = state.trip?.me ?: return emptyList()
        return state.pocket.filter { env ->
            now() <= env.ttlUnix &&
                env.destLoftId != me.loftId &&
                (env.destLoftId == peerLoftId || isMember(env.destLoftId))
        }.sortedByDescending { it.priority }
    }

    fun mergeRoster(incoming: List<Member>) {
        val trip = state.trip ?: return
        val merged = distinctMembers(trip.members + incoming).take(MAX_FLOCK)
        persist(state.copy(trip = trip.copy(members = merged)))
    }

    fun onJoinHello(member: Member, joinMacHex: String): Boolean {
        val trip = state.trip ?: return false
        if (!verifyJoinMac(trip, member.loftId, joinMacHex)) return false
        if (trip.members.any { it.loftId == member.loftId }) return true
        if (trip.members.size >= MAX_FLOCK) return false
        persist(state.copy(trip = trip.copy(members = trip.members + member)))
        return true
    }

    fun joinMacHex(): String {
        val trip = state.trip ?: return ""
        return TurturCrypto.hmacSha256(trip.joinKeyB64.b64d(), trip.me.loftId.toByteArray(Charsets.UTF_8))
            .copyOf(16)
            .b64()
    }

    fun tripAdvertHash(): ByteArray {
        val trip = state.trip ?: return ByteArray(8)
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(trip.tripId.toByteArray(Charsets.UTF_8)).copyOf(8)
    }

    fun tripMatchesAdvert(serviceData: ByteArray?): Boolean {
        if (serviceData == null || serviceData.size < 8) return false
        return serviceData.copyOf(8).contentEquals(tripAdvertHash())
    }

    fun markFed(peers: Int, given: Int, taken: Int) {
        val note = if (peers == 0) {
            "No flock in range. That's normal on the trail. Feed at camp."
        } else {
            "$peers nearby · $given given · $taken taken"
        }
        persist(
            state.copy(
                inRange = peers,
                lastFeedUnix = now(),
                lastFeedNote = note,
            ),
        )
    }

    fun setInRange(count: Int) {
        persist(state.copy(inRange = count), writeDisk = false)
        state = state.copy(inRange = count)
    }

    private fun fly(
        pigeonId: String,
        dest: Member,
        kind: PigeonKind,
        summary: String,
        priority: Int,
        inner: InnerPayload,
        ttlMs: Long?,
    ) {
        val trip = state.trip ?: error("no trip")
        val ttl = now() + (ttlMs ?: trip.habitat.defaultTtlMs())
        val env = sealTo(dest, inner, pigeonId, dest.loftId, priority, ttl)
        val out = OutboxRecord(
            pigeonId = pigeonId,
            destLoftId = dest.loftId,
            kind = kind,
            summary = summary,
            createdUnix = now(),
            ttlUnix = ttl,
        )
        persist(state.copy(pocket = state.pocket + env, outbox = state.outbox + out))
    }

    private fun payload(
        pigeonId: String,
        destLoftId: String,
        kind: PigeonKind,
        extra: InnerPayload,
    ): InnerPayload {
        val unsigned = extra.copy(sigB64 = "")
        val body = json.encodeToString(unsigned).toByteArray()
        val toSign = pigeonId.toByteArray() + destLoftId.toByteArray() + byteArrayOf(kind.ordinal.toByte()) + body
        val sig = TurturCrypto.sign(meSecretSign(), toSign)
        return extra.copy(sigB64 = sig.b64())
    }

    private fun openLocal(env: Envelope) {
        val trip = state.trip ?: return
        val padded = TurturCrypto.open(
            recipientSecret = trip.sealSkB64.b64d(),
            recipientPublic = trip.me.sealPkB64.b64d(),
            blob = env.sealedB64.b64d(),
        )
        val inner = json.decodeFromString<InnerPayload>(String(TurturCrypto.unpadPlaintext(padded), Charsets.UTF_8))
        val sender = trip.members.find { it.loftId == inner.senderLoftId }
            ?: return // unknown sender; fail closed until roster gossip
        val unsigned = inner.copy(sigB64 = "")
        val body = json.encodeToString(unsigned).toByteArray()
        val toSign = env.pigeonId.toByteArray() + env.destLoftId.toByteArray() +
            byteArrayOf(inner.kind.ordinal.toByte()) + body
        if (!TurturCrypto.verify(sender.signPkB64.b64d(), toSign, inner.sigB64.b64d())) return
        if (inner.kind == PigeonKind.ACK) {
            val ackId = inner.ackPigeonId ?: return
            persist(
                state.copy(
                    outbox = state.outbox.map {
                        if (it.pigeonId == ackId) it.copy(state = FlightState.LANDED) else it
                    },
                ),
            )
            return
        }
        val arrival = Arrival(
            pigeonId = env.pigeonId,
            senderLoftId = inner.senderLoftId,
            kind = inner.kind,
            summary = arrivalSummary(inner),
            landedUnix = now(),
            meetupId = inner.meetupId,
            place = inner.place,
            timeLocal = inner.timeLocal,
            okay = inner.okay,
            note = inner.note,
        )
        val withArrival = state.copy(arrivals = listOf(arrival) + state.arrivals)
        state = withArrival
        persist(state)
        if (inner.kind != PigeonKind.ROSTER) {
            queueAck(inner.senderLoftId, env.pigeonId)
        }
    }

    private fun queueAck(senderLoftId: String, pigeonId: String) {
        val dest = state.trip?.members?.find { it.loftId == senderLoftId } ?: return
        val ackId = newPigeonId()
        val inner = payload(
            pigeonId = ackId,
            destLoftId = dest.loftId,
            kind = PigeonKind.ACK,
            extra = InnerPayload(
                senderLoftId = me().loftId,
                kind = PigeonKind.ACK,
                ackPigeonId = pigeonId,
                sigB64 = "",
            ),
        )
        val ttl = now() + (state.trip?.habitat?.defaultTtlMs() ?: Habitat.WILD.defaultTtlMs())
        val env = sealTo(dest, inner, ackId, dest.loftId, 2, ttl)
        persist(state.copy(pocket = state.pocket + env))
    }

    private fun sealTo(
        dest: Member,
        inner: InnerPayload,
        pigeonId: String,
        destLoftId: String,
        priority: Int,
        ttl: Long,
    ): Envelope {
        val trip = state.trip ?: error("no trip")
        val padded = TurturCrypto.padPlaintext(json.encodeToString(inner).toByteArray())
        val sealed = TurturCrypto.seal(dest.sealPkB64.b64d(), padded)
        return Envelope(
            pigeonId = pigeonId,
            tripId = trip.tripId,
            destLoftId = destLoftId,
            priority = priority,
            ttlUnix = ttl,
            sealedB64 = sealed.b64(),
        )
    }

    private fun expire() {
        val t = now()
        if (state.trip?.me == null) return
        val keptPocket = state.pocket.filter { env -> env.ttlUnix >= t }
        val outbox = state.outbox.map { record ->
            when {
                record.state != FlightState.ON_THE_WING -> record
                record.ttlUnix < t -> record.copy(state = FlightState.RETURNED)
                else -> record
            }
        }
        state = state.copy(pocket = keptPocket, outbox = outbox)
        store.savePocket(keptPocket)
        store.saveOutbox(outbox)
    }

    private fun persist(next: EngineState, writeDisk: Boolean = true) {
        state = next
        if (!writeDisk) return
        next.trip?.let(store::saveTrip)
        store.savePocket(next.pocket)
        store.saveOutbox(next.outbox)
        store.saveArrivals(next.arrivals)
    }

    private fun member(loftId: String) =
        state.trip?.members?.find { it.loftId == loftId } ?: error("unknown loft")

    private fun isMember(loftId: String) = state.trip?.members?.any { it.loftId == loftId } == true

    private fun me() = state.trip?.me ?: error("no trip")

    private fun meSecretSign() = state.trip!!.signSkB64.b64d()

    private fun others() = state.trip!!.members.filter { it.loftId != state.trip!!.me.loftId }

    private fun now() = System.currentTimeMillis()

    private fun verifyJoinMac(trip: TripSnapshot, loftId: String, macB64: String): Boolean {
        val expected = TurturCrypto.hmacSha256(trip.joinKeyB64.b64d(), loftId.toByteArray(Charsets.UTF_8)).copyOf(16)
        val got = runCatching { macB64.b64d() }.getOrNull() ?: return false
        return expected.contentEquals(got)
    }

    companion object {
        fun arrivalSummary(inner: InnerPayload): String = when (inner.kind) {
            PigeonKind.NOTE -> inner.note?.take(80) ?: "note"
            PigeonKind.MEETUP -> "${inner.place} · ${inner.timeLocal}"
            PigeonKind.OKAY -> inner.okay?.label() ?: "okay"
            PigeonKind.ACK -> "landed"
            PigeonKind.ROSTER -> "roster"
        }

        fun distinctMembers(list: List<Member>): List<Member> =
            list.associateBy { it.loftId }.values.toList()
    }
}
