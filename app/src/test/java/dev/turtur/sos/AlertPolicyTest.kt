package dev.turtur.sos

import dev.turtur.model.SosRecord
import dev.turtur.model.SosSighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPolicyTest {
    private val record = SosRecord(beaconId = "b", name = "Maya", seq = 1, signerPkB64 = "pk")
    private val t0 = 1_000_000L

    private fun seen(r: SosRecord = record, lastAlert: Long = t0) =
        SosSighting(record = r, verified = false, firstSeenUnix = t0, lastSeenUnix = t0, lastAlertUnix = lastAlert)

    @Test
    fun firstSightingAlerts() {
        assertTrue(AlertPolicy.shouldAlert(null, record, t0))
    }

    @Test
    fun firstSightingOfCancelledBeaconStaysQuiet() {
        assertFalse(AlertPolicy.shouldAlert(null, record.copy(cancelled = true), t0))
    }

    @Test
    fun updatesInsideWindowAreQuiet() {
        val next = record.copy(seq = 2)
        assertFalse(AlertPolicy.shouldAlert(seen(), next, t0 + AlertPolicy.REPEAT_MS - 1))
        assertTrue(AlertPolicy.shouldAlert(seen(), next, t0 + AlertPolicy.REPEAT_MS))
    }

    @Test
    fun flipToSafeAlertsOnce() {
        val safe = record.copy(seq = 2, cancelled = true)
        assertTrue(AlertPolicy.shouldAlert(seen(), safe, t0 + 1))
        assertFalse(AlertPolicy.shouldAlert(seen(safe), safe.copy(seq = 3), t0 + AlertPolicy.REPEAT_MS * 4))
    }
}

class GeoTest {
    @Test
    fun distanceAndBearing() {
        val d = Geo.distanceM(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_195.0, d, 50.0)
        assertEquals(0.0, Geo.bearingDeg(0.0, 0.0, 1.0, 0.0), 0.01)
        assertEquals(90.0, Geo.bearingDeg(0.0, 0.0, 0.0, 1.0), 0.01)
        assertEquals("NE", Geo.cardinal(40.0))
        assertEquals("N", Geo.cardinal(350.0))
    }

    @Test
    fun rssiMeterSmooths() {
        val m = RssiMeter(alpha = 0.5)
        assertEquals(-80.0, m.add(-80), 0.0)
        assertEquals(-70.0, m.add(-60), 0.0)
        assertTrue(RssiMeter.band(-55.0).startsWith("Hot"))
        assertTrue(RssiMeter.band(-95.0).startsWith("Cold"))
    }
}
