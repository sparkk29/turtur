package dev.turtur.sos

import dev.turtur.model.SosRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object SmsText {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm d MMM zzz", Locale.US)

    fun coords(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f,%.5f", lat, lon)

    fun body(record: SosRecord, zone: ZoneId = ZoneId.systemDefault()): String {
        val lat = record.lat
        val lon = record.lon
        val where = if (lat != null && lon != null) {
            val acc = record.accuracyM?.let { " (±${it.toInt()} m)" }.orEmpty()
            val at = record.fixUnix?.let { " at ${timeFormat.format(Instant.ofEpochMilli(it).atZone(zone))}" }.orEmpty()
            val c = coords(lat, lon)
            "Last GPS $c$acc$at. Map: https://maps.google.com/?q=$c"
        } else {
            "Their beacon had no GPS fix; it was heard over Bluetooth by the person sending this."
        }
        return "TURTUR SOS: ${record.name} needs help. $where " +
            "Relayed by a Turtur user who heard their beacon. " +
            "Please call local emergency services (112) with this location."
    }

    /** smsto: with several recipients; most SMS apps split on ';'. */
    fun smsUri(phones: List<String>): String? {
        val dialable = phones.map(Phones::normalize).filter(Phones::isDialable)
        if (dialable.isEmpty()) return null
        return "smsto:" + dialable.joinToString(";")
    }
}
