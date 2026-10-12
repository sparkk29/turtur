package dev.turtur.sos

import dev.turtur.crypto.TurturCrypto
import dev.turtur.model.SosRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosRecordsTest {
    private val keys = TurturCrypto.generateSignPair()

    private fun record(seq: Long = 1) = SosRecords.sign(
        SosRecord(
            beaconId = "0123456789abcdef",
            name = "Maya",
            lat = 30.123456,
            lon = 79.654321,
            accuracyM = 12.5f,
            fixUnix = 1_760_000_000_000,
            battery = 54,
            seq = seq,
            contacts = listOf("+919876543210"),
            signerPkB64 = SosRecords.b64(keys.public),
        ),
        keys.secret,
    )

    @Test
    fun signedRecordVerifies() {
        assertTrue(SosRecords.verify(record()))
    }

    @Test
    fun tamperedRecordFails() {
        val r = record()
        assertFalse(SosRecords.verify(r.copy(lat = 30.2)))
        assertFalse(SosRecords.verify(r.copy(contacts = listOf("+10000000000"))))
        assertFalse(SosRecords.verify(r.copy(cancelled = true)))
        assertFalse(SosRecords.verify(r.copy(sigB64 = "")))
    }

    @Test
    fun foreignKeyCannotSignForAnother() {
        val other = TurturCrypto.generateSignPair()
        val forged = SosRecords.sign(record().copy(signerPkB64 = SosRecords.b64(keys.public)), other.secret)
        assertFalse(SosRecords.verify(forged))
    }

    @Test
    fun survivesJsonRoundTripLikeGatt() {
        val compact = Json { encodeDefaults = false }
        val lenient = Json { ignoreUnknownKeys = true }
        val bytes = compact.encodeToString(record())
        val back = lenient.decodeFromString<SosRecord>(bytes)
        assertTrue(SosRecords.verify(back))
        assertTrue("GATT attribute values cap at 512 bytes", bytes.toByteArray().size <= 512)
    }

    @Test
    fun replayAndOlderSeqAreRejected() {
        val r1 = record(seq = 5)
        assertTrue(SosRecords.isNewer(null, r1))
        assertFalse(SosRecords.isNewer(r1, r1))
        assertFalse(SosRecords.isNewer(r1, record(seq = 4)))
        assertTrue(SosRecords.isNewer(r1, record(seq = 6)))
    }

    @Test
    fun otherKeyCannotHijackBeacon() {
        val other = TurturCrypto.generateSignPair()
        val hijack = SosRecords.sign(
            record(seq = 99).copy(signerPkB64 = SosRecords.b64(other.public)),
            other.secret,
        )
        assertTrue(SosRecords.verify(hijack))
        assertFalse(SosRecords.isNewer(record(seq = 1), hijack))
    }

    @Test
    fun nextBumpsSeqAndCancelVerifies() {
        val r1 = record(seq = 1)
        val moved = SosRecords.next(r1, keys.secret, lat = 30.2, lon = 79.7)
        assertEquals(2, moved.seq)
        assertEquals(30.2, moved.lat!!, 0.0)
        assertTrue(SosRecords.isNewer(r1, moved))

        val safe = SosRecords.next(moved, keys.secret, cancelled = true)
        assertTrue(safe.cancelled)
        assertEquals(3, safe.seq)
        assertTrue(SosRecords.isNewer(moved, safe))
    }

    @Test
    fun signCapsNameAndContacts() {
        val r = SosRecords.sign(
            record().copy(
                name = "x".repeat(60),
                contacts = listOf("+1", "+2", "+3", "+4", "+5"),
            ),
            keys.secret,
        )
        assertEquals(SosRecords.NAME_MAX, r.name.length)
        assertEquals(3, r.contacts.size)
        assertTrue(SosRecords.verify(r))
    }

    @Test
    fun advertPayloadRoundTrips() {
        val r = record(seq = 70_000).copy(cancelled = true)
        val info = SosRecords.parseAdvert(SosRecords.advertPayload(r))
        assertNotNull(info)
        assertEquals("0123456789abcdef", info!!.beaconId)
        assertEquals(70_000L, info.seq)
        assertTrue(info.cancelled)
        assertEquals(null, SosRecords.parseAdvert(ByteArray(4)))
    }
}
