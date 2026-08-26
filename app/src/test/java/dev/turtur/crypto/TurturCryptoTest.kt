package dev.turtur.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurturCryptoTest {
    @Test
    fun sealOpenRoundTrip() {
        val recipient = TurturCrypto.generateSealPair()
        val plain = "taking high pass, 2h late".toByteArray()
        val sealed = TurturCrypto.seal(recipient.public, plain)
        val opened = TurturCrypto.open(recipient.secret, recipient.public, sealed)
        assertArrayEquals(plain, opened)
    }

    @Test
    fun wrongRecipientCannotOpen() {
        val alice = TurturCrypto.generateSealPair()
        val bob = TurturCrypto.generateSealPair()
        val sealed = TurturCrypto.seal(alice.public, "secret".toByteArray())
        var failed = false
        try {
            TurturCrypto.open(bob.secret, bob.public, sealed)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun padUnpadPreservesJson() {
        val json = """{"kind":"NOTE","note":"hello camp"}""".toByteArray()
        val padded = TurturCrypto.padPlaintext(json)
        assertTrue(padded.size == 400 || padded.size == 1800)
        assertArrayEquals(json, TurturCrypto.unpadPlaintext(padded))
    }

    @Test
    fun signVerify() {
        val keys = TurturCrypto.generateSignPair()
        val msg = "roster|1|loftA".toByteArray()
        val sig = TurturCrypto.sign(keys.secret, msg)
        assertTrue(TurturCrypto.verify(keys.public, msg, sig))
        assertFalse(TurturCrypto.verify(keys.public, "tampered".toByteArray(), sig))
    }

    @Test
    fun randomIdsAreHex() {
        val id = TurturCrypto.randomId(16)
        assertEquals(32, id.length)
        assertTrue(id.all { it in "0123456789abcdef" })
    }
}
