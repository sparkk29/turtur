package dev.turtur.store

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dev.turtur.model.Arrival
import dev.turtur.model.Envelope
import dev.turtur.model.OutboxRecord
import dev.turtur.model.TripSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true }

class TripStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = EncryptedSharedPreferences.create(
        "turtur-loft",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        appContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val pocketFile = File(appContext.filesDir, "pocket.json")
    private val outboxFile = File(appContext.filesDir, "outbox.json")
    private val arrivalsFile = File(appContext.filesDir, "arrivals.json")

    fun loadTrip(): TripSnapshot? {
        val raw = prefs.getString(KEY_TRIP, null) ?: return null
        return json.decodeFromString(raw)
    }

    fun saveTrip(trip: TripSnapshot) {
        prefs.edit().putString(KEY_TRIP, json.encodeToString(trip)).apply()
    }

    fun loadPocket(): MutableList<Envelope> = readList(pocketFile)
    fun loadOutbox(): MutableList<OutboxRecord> = readList(outboxFile)
    fun loadArrivals(): MutableList<Arrival> = readList(arrivalsFile)

    fun savePocket(items: List<Envelope>) = writeList(pocketFile, items)
    fun saveOutbox(items: List<OutboxRecord>) = writeList(outboxFile, items)
    fun saveArrivals(items: List<Arrival>) = writeList(arrivalsFile, items)

    fun molt() {
        prefs.edit().clear().apply()
        pocketFile.delete()
        outboxFile.delete()
        arrivalsFile.delete()
    }

    private inline fun <reified T> readList(file: File): MutableList<T> {
        if (!file.exists()) return mutableListOf()
        return json.decodeFromString(file.readText())
    }

    private inline fun <reified T> writeList(file: File, items: List<T>) {
        file.writeText(json.encodeToString(items))
    }

    companion object {
        private const val KEY_TRIP = "trip"
    }
}

fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

fun String.b64d(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
