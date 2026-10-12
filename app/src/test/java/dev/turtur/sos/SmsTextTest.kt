package dev.turtur.sos

import dev.turtur.model.SosRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class SmsTextTest {
    private val base = SosRecord(
        beaconId = "0123456789abcdef",
        name = "Maya",
        lat = 30.1234567,
        lon = 79.7654321,
        accuracyM = 8.4f,
        fixUnix = 1_760_268_000_000,
        seq = 3,
        contacts = listOf("+919876543210", "+14155550123"),
        signerPkB64 = "pk",
    )

    @Test
    fun bodyCarriesLocationMapAndTime() {
        val body = SmsText.body(base, ZoneId.of("UTC"))
        assertTrue(body.startsWith("TURTUR SOS: Maya needs help."))
        assertTrue(body.contains("30.12346,79.76543"))
        assertTrue(body.contains("(±8 m)"))
        assertTrue(body.contains("https://maps.google.com/?q=30.12346,79.76543"))
        assertTrue(body.contains("at 11:20 12 Oct UTC"))
        assertTrue(body.contains("112"))
    }

    @Test
    fun bodyWithoutFixSaysSo() {
        val body = SmsText.body(base.copy(lat = null, lon = null, fixUnix = null), ZoneId.of("UTC"))
        assertTrue(body.contains("no GPS fix"))
        assertFalse(body.contains("maps.google.com"))
    }

    @Test
    fun smsUriJoinsDialableNumbers() {
        assertEquals("smsto:+919876543210;+14155550123", SmsText.smsUri(base.contacts))
        assertEquals("smsto:+14155550123", SmsText.smsUri(listOf("+1", "+1 415 555 0123")))
        assertNull(SmsText.smsUri(emptyList()))
    }
}
