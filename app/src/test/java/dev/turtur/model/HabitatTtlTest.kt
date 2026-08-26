package dev.turtur.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitatTtlTest {
    @Test
    fun wildDefaultsLongerThanCrowd() {
        assertTrue(Habitat.WILD.defaultTtlMs() > Habitat.CROWD.defaultTtlMs())
        assertEquals(48L * 3600_000, Habitat.WILD.defaultTtlMs())
        assertEquals(8L * 3600_000, Habitat.CROWD.defaultTtlMs())
    }

    @Test
    fun opportunisticRestIsLongerInWild() {
        assertTrue(Habitat.WILD.opportunisticRestMs() > Habitat.CROWD.opportunisticRestMs())
    }

    @Test
    fun okayLabelsAreHuman() {
        assertEquals("I'm fine", OkayStatus.FINE.label())
        assertEquals("Delayed", OkayStatus.DELAYED.label())
        assertEquals("Stay put", OkayStatus.STAY_PUT.label())
    }
}
