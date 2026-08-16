package dev.turtur.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.math.ec.rfc7748.X25519
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class KeyPairBytes(val public: ByteArray, val secret: ByteArray)

object TurturCrypto {
    const val VERSION: Byte = 1
    private val hkdfInfo = "turtur-seal-v1".toByteArray(Charsets.UTF_8)
    private val random = SecureRandom()

    fun randomId(bytes: Int = 16): String {
        val buf = ByteArray(bytes)
        random.nextBytes(buf)
        return buf.toHex()
    }

    fun generateSealPair(): KeyPairBytes {
        val sk = ByteArray(X25519.SCALAR_SIZE)
        val pk = ByteArray(X25519.POINT_SIZE)
        X25519.generatePrivateKey(random, sk)
        X25519.generatePublicKey(sk, 0, pk, 0)
        return KeyPairBytes(pk, sk)
    }

    fun generateSignPair(): KeyPairBytes {
        val private = Ed25519PrivateKeyParameters(random)
        return KeyPairBytes(private.generatePublicKey().encoded, private.encoded)
    }

    fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    fun sign(secret: ByteArray, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(secret, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun verify(public: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(public, 0))
        verifier.update(message, 0, message.size)
        return verifier.verifySignature(signature)
    }

    fun seal(recipientPublic: ByteArray, plaintext: ByteArray): ByteArray {
        val eph = generateSealPair()
        val shared = ByteArray(X25519.POINT_SIZE)
        check(X25519.calculateAgreement(eph.secret, 0, recipientPublic, 0, shared, 0)) {
            "X25519 agreement failed"
        }
        val key = hkdf(shared, eph.public + recipientPublic)
        val nonce = ByteArray(12)
        random.nextBytes(nonce)
        val ct = aead(encrypt = true, key = key, nonce = nonce, plaintext = plaintext)
        return byteArrayOf(VERSION) + eph.public + nonce + ct
    }

    fun open(recipientSecret: ByteArray, recipientPublic: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size > 1 + X25519.POINT_SIZE + 12) { "sealed box too short" }
        require(blob[0] == VERSION) { "unknown sealed box version" }
        val ephPublic = blob.copyOfRange(1, 1 + X25519.POINT_SIZE)
        val nonce = blob.copyOfRange(1 + X25519.POINT_SIZE, 1 + X25519.POINT_SIZE + 12)
        val ct = blob.copyOfRange(1 + X25519.POINT_SIZE + 12, blob.size)
        val shared = ByteArray(X25519.POINT_SIZE)
        check(X25519.calculateAgreement(recipientSecret, 0, ephPublic, 0, shared, 0)) {
            "X25519 agreement failed"
        }
        val key = hkdf(shared, ephPublic + recipientPublic)
        return aead(encrypt = false, key = key, nonce = nonce, plaintext = ct)
    }

    /** Pad JSON so note and okay pigeons fall into a few length bins. */
    fun padPlaintext(json: ByteArray): ByteArray {
        val target = if (json.size + 4 <= 400) 400 else 1800
        require(json.size + 4 <= target) { "payload too large for turtur bins" }
        val out = ByteArray(target)
        writeInt(out, json.size)
        json.copyInto(out, 4)
        return out
    }

    fun unpadPlaintext(padded: ByteArray): ByteArray {
        val len = readInt(padded)
        require(len >= 0 && len + 4 <= padded.size) { "bad padding" }
        return padded.copyOfRange(4, 4 + len)
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray): ByteArray {
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(ikm, salt, hkdfInfo))
        val out = ByteArray(32)
        gen.generateBytes(out, 0, out.size)
        return out
    }

    private fun aead(encrypt: Boolean, key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(encrypt, AEADParameters(KeyParameter(key), 128, nonce))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val n = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        val n2 = cipher.doFinal(out, n)
        return out.copyOf(n + n2)
    }

    private fun writeInt(buf: ByteArray, value: Int) {
        buf[0] = ((value ushr 24) and 0xff).toByte()
        buf[1] = ((value ushr 16) and 0xff).toByte()
        buf[2] = ((value ushr 8) and 0xff).toByte()
        buf[3] = (value and 0xff).toByte()
    }

    private fun readInt(buf: ByteArray): Int {
        return ((buf[0].toInt() and 0xff) shl 24) or
            ((buf[1].toInt() and 0xff) shl 16) or
            ((buf[2].toInt() and 0xff) shl 8) or
            (buf[3].toInt() and 0xff)
    }
}

fun ByteArray.toHex(): String = joinToString("") { b -> "%02x".format(b) }

fun String.hexToBytes(): ByteArray {
    check(length % 2 == 0)
    return ByteArray(length / 2) { i ->
        substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
