package io.openhoyi.mobile

import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry

/** Observes machine-initiated extraction without ever issuing a device command. */
class PassiveShotDetector {
    data class TimedFrame(val frame: ExtractionTelemetry, val atMs: Long)
    sealed interface Event {
        data class Started(val first: TimedFrame, val second: TimedFrame) : Event
        data class Point(val value: TimedFrame) : Event
        data object Ended : Event
        data object Interrupted : Event
    }

    private var candidate: TimedFrame? = null
    private var lastActiveAtMs: Long? = null
    var active = false
        private set

    fun observe(frame: HoyiMessage, atMs: Long, appShotInProgress: Boolean): Event? {
        if (atMs < 0) return null
        if (appShotInProgress) {
            if (!active) candidate = null
            return null
        }
        if (frame is ExtractionTelemetry) {
            val timed = TimedFrame(frame, atMs)
            if (active) {
                if (frame.valveOpen) lastActiveAtMs = atMs
                return Event.Point(timed)
            }
            if (!frame.valveOpen || frame.brewWait) { candidate = null; return null }
            val first = candidate
            candidate = timed
            if (first == null || atMs <= first.atMs || atMs - first.atMs > 1500 ||
                frame.elapsedSeconds < first.frame.elapsedSeconds ||
                frame.totalWaterTenthsMl < first.frame.totalWaterTenthsMl ||
                (frame.elapsedSeconds == first.frame.elapsedSeconds &&
                    frame.totalWaterTenthsMl == first.frame.totalWaterTenthsMl)) return null
            active = true
            candidate = null
            lastActiveAtMs = atMs
            return Event.Started(first, timed)
        }
        if (frame is IdleTelemetry) {
            candidate = null
            if (active && lastActiveAtMs?.let { atMs > it && atMs - it > 2800 } == true) {
                active = false
                lastActiveAtMs = null
                return Event.Ended
            }
        }
        return null
    }

    fun disconnected(): Event? {
        candidate = null
        lastActiveAtMs = null
        if (!active) return null
        active = false
        return Event.Interrupted
    }
}
