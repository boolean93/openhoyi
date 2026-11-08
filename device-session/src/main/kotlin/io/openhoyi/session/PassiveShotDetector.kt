package io.openhoyi.session

import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry

/**
 * Observes machine-initiated extraction without ever issuing a device command.
 *
 * [Event.Started] is emitted as soon as two consistent valve-open frames are observed, so the
 * machine-in-motion gates stay armed and a realtime chart can begin immediately. While the
 * observation is active and no App shot is in progress, every frame is also surfaced as
 * [Event.Point]; the observation only becomes a cup at
 * [Event.Committed] once the extraction lasts at least [minimumShotSeconds]. Shorter passive
 * water releases (for example flushing the brew head before an App shot) still report Started,
 * Points and Ended, yet never create history or keep a manual identity after they end.
 */
class PassiveShotDetector(
    private val minimumShotSeconds: Int = MIN_MANUAL_CUP_SECONDS,
) {
    init {
        require(minimumShotSeconds > 0) { "minimumShotSeconds must be positive" }
    }

    data class TimedFrame(val frame: ExtractionTelemetry, val atMs: Long)

    sealed interface Event {
        data class Started(val first: TimedFrame, val second: TimedFrame) : Event
        /**
         * At most one per active observation, emitted after the [Point] for the threshold frame.
         * The caller already recorded the realtime points, so no frames are replayed here.
         */
        data object Committed : Event
        data class Point(val value: TimedFrame) : Event
        data object Ended : Event
        data object Interrupted : Event
    }

    private var candidate: TimedFrame? = null
    private var lastActiveAtMs: Long? = null
    private var lastObservedAtMs: Long? = null
    private var first: TimedFrame? = null
    private var committed = false
    var active = false
        private set

    /** True once the current observation was long enough to count as a cup. */
    val isCommitted: Boolean get() = committed

    /**
     * Empty means no transition. A threshold frame returns Point then Committed; a frame that
     * yields Started in a long-running observation may append Committed as well.
     */
    fun observe(frame: HoyiMessage, atMs: Long, appShotInProgress: Boolean): List<Event> {
        if (atMs < 0 || lastObservedAtMs?.let { atMs <= it } == true) return emptyList()
        lastObservedAtMs = atMs
        if (appShotInProgress) {
            if (!active) candidate = null
            return emptyList()
        }
        if (frame is ExtractionTelemetry) {
            val timed = TimedFrame(frame, atMs)
            if (active) {
                if (frame.valveOpen) lastActiveAtMs = atMs
                val events = mutableListOf<Event>(Event.Point(timed))
                // Only an open valve can promote the observation to a cup; a closing frame is
                // still a realtime point but must not cross the threshold on its own.
                if (!committed && frame.valveOpen && due(frame, atMs)) {
                    committed = true
                    events += Event.Committed
                }
                return events
            }
            if (!frame.valveOpen || frame.brewWait) { candidate = null; return emptyList() }
            val previous = candidate
            candidate = timed
            if (previous == null || atMs <= previous.atMs || atMs - previous.atMs > 1500 ||
                frame.elapsedSeconds < previous.frame.elapsedSeconds ||
                frame.totalWaterTenthsMl < previous.frame.totalWaterTenthsMl ||
                (frame.elapsedSeconds == previous.frame.elapsedSeconds &&
                    frame.totalWaterTenthsMl == previous.frame.totalWaterTenthsMl)) return emptyList()
            active = true
            candidate = null
            lastActiveAtMs = atMs
            first = previous
            committed = false
            val events = mutableListOf<Event>(Event.Started(previous, timed))
            if (due(frame, atMs)) {
                committed = true
                events += Event.Committed
            }
            return events
        }
        if (frame is IdleTelemetry) {
            candidate = null
            if (active && lastActiveAtMs?.let { atMs > it && atMs - it > 2800 } == true) {
                reset()
                return listOf(Event.Ended)
            }
        }
        return emptyList()
    }

    fun disconnected(): Event? {
        lastObservedAtMs = null
        candidate = null
        lastActiveAtMs = null
        if (!active) { reset(); return null }
        reset()
        return Event.Interrupted
    }

    /**
     * Only progress observed after [first] counts: machine-reported seconds must advance by the
     * threshold and the observed span is a fallback for a frozen elapsed counter. An absolute
     * elapsedSeconds check would instantly commit valve-open tail frames left over from the
     * previous App cup, which already report 20-30 seconds.
     */
    private fun due(frame: ExtractionTelemetry, atMs: Long): Boolean {
        val startedFirst = first ?: return false
        return frame.elapsedSeconds - startedFirst.frame.elapsedSeconds >= minimumShotSeconds ||
            atMs - startedFirst.atMs >= minimumShotSeconds * 1000L
    }

    private fun reset() {
        active = false
        first = null
        committed = false
        lastActiveAtMs = null
    }

    companion object {
        /**
         * 空放等短时被动出水不计为一杯；真机验收后可调。
         * Flushing the brew head normally releases water for less than ten seconds, so a cup is
         * only committed after twelve seconds of passive extraction.
         */
        const val MIN_MANUAL_CUP_SECONDS = 12
    }
}
