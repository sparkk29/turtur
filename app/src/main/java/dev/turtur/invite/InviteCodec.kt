package dev.turtur.invite

import android.graphics.Bitmap
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.turtur.model.Invite
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

object InviteCodec {
    private const val PREFIX = "turtur1."

    fun encode(invite: Invite): String {
        val bytes = json.encodeToString(invite).toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    fun decode(raw: String): Invite {
        val text = raw.trim()
        val body = when {
            text.startsWith(PREFIX) -> text.removePrefix(PREFIX)
            else -> text
        }
        val bytes = Base64.decode(body, Base64.URL_SAFE or Base64.NO_WRAP)
        return json.decodeFromString(String(bytes, Charsets.UTF_8))
    }

    fun qrBitmap(invite: Invite, size: Int = 720): Bitmap = qrBitmap(encode(invite), size)

    fun qrBitmap(payload: String, size: Int = 720): Bitmap {
        val bitMatrix = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val pixels = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            if (bitMatrix.get(x, y)) 0xFF1C1410.toInt() else 0xFFF3EBDD.toInt()
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
