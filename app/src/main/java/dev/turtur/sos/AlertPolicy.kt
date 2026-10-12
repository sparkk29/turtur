package dev.turtur.sos

import dev.turtur.model.SosRecord
import dev.turtur.model.SosSighting

object AlertPolicy {
    const val REPEAT_MS = 15 * 60_000L

    /**
     * Loud alert on the first sighting and when a beacon flips to "I'm safe". Otherwise
     * position updates refresh the notification quietly, at most one loud repeat per window.
     */
    fun shouldAlert(previous: SosSighting?, incoming: SosRecord, now: Long): Boolean {
        if (previous == null) return !incoming.cancelled
        if (incoming.cancelled != previous.record.cancelled) return true
        if (incoming.cancelled) return false
        return now - previous.lastAlertUnix >= REPEAT_MS
    }
}
