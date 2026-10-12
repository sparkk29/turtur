package dev.turtur.sos

import dev.turtur.crypto.TurturCrypto
import dev.turtur.model.GuardianCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianCardCodecTest {
    private val pk = SosRecords.b64(TurturCrypto.generateSignPair().public)

    @Test
    fun cardRoundTrips() {
        val card = GuardianCard(name = "Chris", phone = "+91 98765-43210", signPkB64 = pk)
        val text = GuardianCardCodec.encode(card)
        assertTrue(GuardianCardCodec.isCard(text))
        val back = GuardianCardCodec.decode(text)
        assertEquals("Chris", back.name)
        assertEquals("+919876543210", back.phone)
        assertEquals(pk, back.signPkB64)
        val contact = GuardianCardCodec.toContact(back)
        assertEquals(pk, contact.signPkB64)
    }

    @Test
    fun tripInviteIsNotACard() {
        assertFalse(GuardianCardCodec.isCard("turtur1.eyJ2IjoxfQ"))
        assertTrue(runCatching { GuardianCardCodec.decode("turtur1.eyJ2IjoxfQ") }.isFailure)
    }

    @Test
    fun malformedKeyIsRejected() {
        val bad = GuardianCardCodec.encode(GuardianCard(name = "Chris", signPkB64 = SosRecords.b64(ByteArray(5))))
        assertTrue(runCatching { GuardianCardCodec.decode(bad) }.isFailure)
    }

    @Test
    fun phonesNormalize() {
        assertEquals("+14155550123", Phones.normalize(" +1 (415) 555-0123 "))
        assertEquals("09876543210", Phones.normalize("098765 43210"))
        assertFalse(Phones.isDialable("+1"))
        assertTrue(Phones.isDialable("112"))
    }
}
