package io.openhoyi.mobile

import io.openhoyi.protocol.ScaleEvidence
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.session.DeviceState
import java.util.ArrayDeque

/** Read-only display history. Never grants permission to tare, capture a dose or stop a shot. */
internal class ScaleTrendWindow(private val maxPoints: Int = 2048) {
    data class Point(val atMs: Long, val hundredthsGram: Int, val segment: Long, val evidence: ScaleEvidence)
    private val samples = ArrayDeque<Point>()
    private var lastNow: Long? = null
    private var lastAt: Long? = null
    private var lastEvidence: ScaleEvidence? = null
    private var segment = 0L
    private var gap = true
    init { require(maxPoints > 0) }

    @Synchronized fun observe(sample: ScaleObservation?, state: DeviceState, now: Long) {
        if (!advance(now)) return
        val live = LiveTelemetry.scale(sample, state, now)
        if (live == null) { gap = true; return }
        val previous = lastAt
        if (previous != null && live.receivedAtMs <= previous) {
            if (live.receivedAtMs < previous) gap = true
            return
        }
        if (gap || previous == null || live.receivedAtMs - previous > 1500 || lastEvidence != live.evidence) segment++
        samples.addLast(Point(live.receivedAtMs, live.hundredthsGram, segment, live.evidence))
        while (samples.size > maxPoints) samples.removeFirst()
        lastAt = live.receivedAtMs
        lastEvidence = live.evidence
        gap = false
    }

    @Synchronized fun points(now: Long): List<Point> {
        if (!advance(now)) return emptyList()
        return samples.toList()
    }

    @Synchronized fun clear() {
        samples.clear()
        lastAt = null; lastNow = null; lastEvidence = null; gap = true
    }

    private fun advance(now: Long): Boolean {
        if (now < 0) { clear(); return false }
        if (lastNow?.let { now < it } == true) clear()
        lastNow = now
        while (samples.peekFirst()?.let { now - it.atMs > 10_000 } == true) samples.removeFirst()
        return true
    }
}
