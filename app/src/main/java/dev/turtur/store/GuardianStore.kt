package dev.turtur.store

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dev.turtur.crypto.TurturCrypto
import dev.turtur.model.ActiveSos
import dev.turtur.model.GuardianProfile
import dev.turtur.model.SosSighting
import dev.turtur.sos.SosRecords
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * SOS identity, emergency contacts, and heard beacons. Lives apart from the trip store
 * so End trip does not erase who to text when you are hurt.
 */
class GuardianStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        "turtur-guardian",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun loadProfile(): GuardianProfile {
        val raw = prefs.getString(KEY_PROFILE, null)
        val profile = raw?.let { json.decodeFromString<GuardianProfile>(it) } ?: GuardianProfile()
        if (profile.signPkB64.isNotBlank() && prefs.contains(KEY_SIGN_SK)) return profile
        val keys = TurturCrypto.generateSignPair()
        val withKey = profile.copy(signPkB64 = SosRecords.b64(keys.public))
        prefs.edit()
            .putString(KEY_SIGN_SK, SosRecords.b64(keys.secret))
            .putString(KEY_PROFILE, json.encodeToString(withKey))
            .apply()
        return withKey
    }

    fun saveProfile(profile: GuardianProfile) {
        prefs.edit().putString(KEY_PROFILE, json.encodeToString(profile)).apply()
    }

    fun signSecret(): ByteArray {
        loadProfile()
        return SosRecords.b64d(prefs.getString(KEY_SIGN_SK, null) ?: error("no guardian key"))
    }

    fun loadActive(): ActiveSos? =
        prefs.getString(KEY_ACTIVE, null)?.let { json.decodeFromString<ActiveSos>(it) }

    fun saveActive(active: ActiveSos?) {
        val edit = prefs.edit()
        if (active == null) edit.remove(KEY_ACTIVE) else edit.putString(KEY_ACTIVE, json.encodeToString(active))
        edit.apply()
    }

    fun loadSightings(): List<SosSighting> =
        prefs.getString(KEY_SIGHTINGS, null)?.let { json.decodeFromString<List<SosSighting>>(it) }.orEmpty()

    fun saveSightings(items: List<SosSighting>) {
        prefs.edit().putString(KEY_SIGHTINGS, json.encodeToString(items)).apply()
    }

    companion object {
        private const val KEY_PROFILE = "profile"
        private const val KEY_SIGN_SK = "sign_sk"
        private const val KEY_ACTIVE = "active_sos"
        private const val KEY_SIGHTINGS = "sightings"
    }
}
