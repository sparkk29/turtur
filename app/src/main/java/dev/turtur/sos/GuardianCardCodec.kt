package dev.turtur.sos

import dev.turtur.model.EmergencyContact
import dev.turtur.model.GuardianCard
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

private val json = Json { ignoreUnknownKeys = true }

object GuardianCardCodec {
    const val PREFIX = "turtur-guardian1."

    fun encode(card: GuardianCard): String {
        val bytes = json.encodeToString(card).toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun isCard(raw: String): Boolean = raw.trim().startsWith(PREFIX)

    fun decode(raw: String): GuardianCard {
        val text = raw.trim()
        require(text.startsWith(PREFIX)) { "not a Turtur guardian card" }
        val bytes = Base64.getUrlDecoder().decode(text.removePrefix(PREFIX))
        val card = json.decodeFromString<GuardianCard>(String(bytes, Charsets.UTF_8))
        require(card.name.isNotBlank()) { "guardian card has no name" }
        require(SosRecords.b64d(card.signPkB64).size == 32) { "guardian card key is malformed" }
        return card.copy(
            name = card.name.trim().take(SosRecords.NAME_MAX),
            phone = Phones.normalize(card.phone),
        )
    }

    fun toContact(card: GuardianCard) = EmergencyContact(
        name = card.name,
        phone = card.phone,
        signPkB64 = card.signPkB64,
    )
}

object Phones {
    /** Keep digits and a leading +; anything else is typing noise. */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        val plus = if (trimmed.startsWith("+")) "+" else ""
        return (plus + digits).take(SosRecords.PHONE_MAX)
    }

    fun isDialable(phone: String): Boolean = phone.count { it.isDigit() } >= 3
}
