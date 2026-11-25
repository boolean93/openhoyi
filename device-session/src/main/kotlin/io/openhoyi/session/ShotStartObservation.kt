package io.openhoyi.session

import io.openhoyi.protocol.ExtractionTelemetry

/** User-facing observation only: cannot authorize writes, prove curve identity or clear recovery. */
enum class ShotStartObservation { WAITING_FOR_MACHINE, MACHINE_EXTRACTION_OBSERVED, NOT_CONFIRMED }

internal class ShotStartEvidence(
    val owner:CoffeeStartContext,
    private val source: CoffeeReadbackSource,
    private val handedOffAt: Long,
) {
    private var firstAt: Long? = null
    private var elapsed: Int? = null
    private var observed = false
    private var latestIngressAt = handedOffAt

    fun observe(current: CoffeeReadbackSource, at: Long, frame: ExtractionTelemetry) {
        if (!acceptIngress(current,at)) return
        val sinceStart = at - handedOffAt
        if (sinceStart >= ObservationWindowMs) return
        if (!frame.valveOpen || frame.brewWait || frame.elapsedSeconds < 0 ||
            frame.elapsedSeconds.toLong() > sinceStart / 1000 + 1) {
            breakUnqualifiedEvidence(current)
            return
        }
        val previousAt = firstAt
        if (previousAt != null && at <= previousAt) return
        // A single delayed/repeated packet is not enough to show that a cup has begun.
        if (previousAt != null && at - previousAt <= 1500) {
            val elapsedStep = frame.elapsedSeconds.toLong() - requireNotNull(elapsed)
            if (elapsedStep in 1..((at - previousAt) / 1000 + 1)) observed = true
        }
        if (previousAt == null || at - previousAt > 1500 || frame.elapsedSeconds != elapsed) {
            firstAt = at
            elapsed = frame.elapsedSeconds
        }
    }

    fun observeIdle(current: CoffeeReadbackSource, at: Long) {
        if (acceptIngress(current,at)) breakUnqualifiedEvidence(current)
    }

    private fun acceptIngress(current: CoffeeReadbackSource, at: Long): Boolean {
        // Native receive time precedes the main-thread port; queued old frames cannot become fresh
        // at dequeue. Equal timestamps cannot prove ordering and are intentionally conservative.
        if (current !== source || handedOffAt < 0 || at <= latestIngressAt || observed) return false
        latestIngressAt = at
        return true
    }

    private fun breakUnqualifiedEvidence(current: CoffeeReadbackSource) {
        if (current === source && !observed) { firstAt = null; elapsed = null }
    }

    fun status(current: CoffeeReadbackSource?, now: Long): ShotStartObservation = when {
        current !== source || now < handedOffAt -> ShotStartObservation.NOT_CONFIRMED
        observed -> ShotStartObservation.MACHINE_EXTRACTION_OBSERVED
        now - handedOffAt >= ObservationWindowMs -> ShotStartObservation.NOT_CONFIRMED
        else -> ShotStartObservation.WAITING_FOR_MACHINE
    }

    companion object { private const val ObservationWindowMs = 10_000L }
}
