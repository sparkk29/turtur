package dev.turtur.sos

import dev.turtur.crypto.TurturCrypto
import dev.turtur.model.MAX_SOS_CONTACTS
import dev.turtur.model.SosRecord
import java.util.Base64

object SosRecords {
    private const val DOMAIN = "turtur-sos-v1"
    const val NAME_MAX = 24
    const val PHONE_MAX = 16

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun b64d(text: String): ByteArray = Base64.getDecoder().decode(text)

    /** Field-by-field bytes so verification never depends on JSON formatting. */
    fun signingBytes(r: SosRecord): ByteArray = listOf(
        DOMAIN,
        r.beaconId,
        r.name,
        r.lat?.toString().orEmpty(),
        r.lon?.toString().orEmpty(),
        r.accuracyM?.toString().orEmpty(),
        r.fixUnix?.toString().orEmpty(),
        r.battery?.toString().orEmpty(),
        r.seq.toString(),
        r.contacts.joinToString(","),
        r.cancelled.toString(),
        r.signerPkB64,
    ).joinToString("|").toByteArray(Charsets.UTF_8)

    fun sign(unsigned: SosRecord, signSecret: ByteArray): SosRecord {
        val clean = unsigned.copy(
            name = unsigned.name.take(NAME_MAX),
            contacts = unsigned.contacts.map { it.take(PHONE_MAX) }.take(MAX_SOS_CONTACTS),
            sigB64 = "",
        )
        val sig = TurturCrypto.sign(signSecret, signingBytes(clean))
        return clean.copy(sigB64 = b64(sig))
    }

    /** Self-consistency only: proves the record was not altered, not who sent it. */
    fun verify(r: SosRecord): Boolean = runCatching {
        if (r.sigB64.isBlank() || r.signerPkB64.isBlank()) return false
        if (r.contacts.size > MAX_SOS_CONTACTS) return false
        TurturCrypto.verify(b64d(r.signerPkB64), signingBytes(r.copy(sigB64 = "")), b64d(r.sigB64))
    }.getOrDefault(false)

    /**
     * Accept a heard record only if it verifies and moves the same beacon forward.
     * Replays (same or lower seq) and a different key claiming an existing beacon are rejected.
     */
    fun isNewer(existing: SosRecord?, incoming: SosRecord): Boolean {
        if (!verify(incoming)) return false
        if (existing == null) return true
        if (existing.beaconId != incoming.beaconId) return true
        if (existing.signerPkB64 != incoming.signerPkB64) return false
        return incoming.seq > existing.seq
    }

    /** Next record for this beacon: bumps seq and re-signs. */
    fun next(
        current: SosRecord,
        signSecret: ByteArray,
        lat: Double? = current.lat,
        lon: Double? = current.lon,
        accuracyM: Float? = current.accuracyM,
        fixUnix: Long? = current.fixUnix,
        battery: Int? = current.battery,
        cancelled: Boolean = current.cancelled,
    ): SosRecord = sign(
        current.copy(
            lat = lat,
            lon = lon,
            accuracyM = accuracyM,
            fixUnix = fixUnix,
            battery = battery,
            cancelled = cancelled,
            seq = current.seq + 1,
        ),
        signSecret,
    )

    fun beaconIdBytes(beaconId: String): ByteArray =
        runCatching { beaconId.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }
            .getOrDefault(ByteArray(0))
            .copyOf(BEACON_ID_BYTES)

    fun beaconIdFromBytes(bytes: ByteArray): String =
        bytes.copyOf(BEACON_ID_BYTES).joinToString("") { "%02x".format(it) }

    const val BEACON_ID_BYTES = 8

    /** Scan-response payload: beaconId(8) + seq low 32 bits + status flags. */
    fun advertPayload(r: SosRecord): ByteArray {
        val out = ByteArray(BEACON_ID_BYTES + 5)
        beaconIdBytes(r.beaconId).copyInto(out, 0)
        val seq = r.seq.toInt()
        out[8] = (seq ushr 24).toByte()
        out[9] = (seq ushr 16).toByte()
        out[10] = (seq ushr 8).toByte()
        out[11] = seq.toByte()
        out[12] = if (r.cancelled) 1 else 0
        return out
    }

    data class AdvertInfo(val beaconId: String, val seq: Long, val cancelled: Boolean)

    fun parseAdvert(bytes: ByteArray?): AdvertInfo? {
        if (bytes == null || bytes.size < BEACON_ID_BYTES + 5) return null
        val seq = ((bytes[8].toLong() and 0xff) shl 24) or
            ((bytes[9].toLong() and 0xff) shl 16) or
            ((bytes[10].toLong() and 0xff) shl 8) or
            (bytes[11].toLong() and 0xff)
        return AdvertInfo(beaconIdFromBytes(bytes), seq, bytes[12].toInt() == 1)
    }
}
