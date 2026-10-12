package dev.turtur.sos

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_M = 6_371_000.0

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial bearing in degrees, 0 = north, clockwise. */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun cardinal(bearing: Double): String {
        val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[((bearing / 45.0).roundToInt()) % 8]
    }

    fun prettyDistance(m: Double): String =
        if (m < 1_000) "${m.roundToInt()} m" else "%.1f km".format(m / 1_000)
}

/** Exponential smoothing for jumpy phone RSSI, plus a coarse "how close" band. */
class RssiMeter(private val alpha: Double = 0.25) {
    var smoothed: Double? = null
        private set

    fun add(rssi: Int): Double {
        val next = smoothed?.let { it + alpha * (rssi - it) } ?: rssi.toDouble()
        smoothed = next
        return next
    }

    fun reset() {
        smoothed = null
    }

    companion object {
        fun band(rssi: Double): String = when {
            rssi >= -60 -> "Hot · within ~10 m"
            rssi >= -75 -> "Warm · ~10–30 m"
            rssi >= -88 -> "Cool · ~30–80 m"
            else -> "Cold · edge of range"
        }

        /** 0..1 for a meter bar; -100 dBm is empty, -45 dBm is full. */
        fun fill(rssi: Double): Float = ((rssi + 100.0) / 55.0).coerceIn(0.0, 1.0).toFloat()
    }
}
