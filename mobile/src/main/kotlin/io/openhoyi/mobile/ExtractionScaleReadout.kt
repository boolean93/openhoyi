package io.openhoyi.mobile

import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.session.DeviceState
import java.util.Locale

/** Display only. Global tare state does not establish a baseline for this cup. */
internal class ExtractionScaleReadout {
    enum class Reason { NO_SCALE, DISCONNECTED, UNAVAILABLE, STALE, END_UNAVAILABLE, UNKNOWN }
    data class Value(val hundredthsGram: Int?, val reason: Reason? = null,
        val targetHundredthsGram: Int? = null, val ended: Boolean = false) {
        val number: String get() = hundredthsGram?.let { String.format(Locale.ROOT, "%.1f", it / 100.0) } ?: "—"
    }
    private var trace: String? = null
    private var target: Int? = null
    private var terminal: Value? = null
    private var endObserved = false
    private var endCandidate: Value? = null
    private var brewingObserved = false

    fun begin(id: String, targetHundredthsGram: Int?) {
        trace = id; target = targetHundredthsGram?.takeIf { it > 0 }
        terminal = null; endObserved = false; endCandidate = null; brewingObserved = false
    }
    fun brewing() {
        if (terminal != null) return
        brewingObserved = true
        // An idle blip followed by more brewing was not this cup's endpoint.
        endObserved = false; endCandidate = null
    }
    /** First idle notification after brewing owns the endpoint, even if its reading is missing. */
    fun observedIdle(sample: ScaleObservation?, state: DeviceState, now: Long) {
        if (trace == null || terminal != null || endObserved || !brewingObserved) return
        endObserved = true
        endCandidate = live(sample, state, now)
    }
    fun finish(observedEnd: Boolean) {
        if (trace == null || terminal != null) return
        terminal = if (!observedEnd) Value(null, Reason.UNKNOWN, target, true)
            else Value(endCandidate?.hundredthsGram,
                if (endCandidate?.hundredthsGram == null) Reason.END_UNAVAILABLE else null, target, true)
    }
    fun value(sample: ScaleObservation?, state: DeviceState, now: Long,
        unknown: Boolean = false): Value {
        terminal?.let { return it }
        if (unknown) return Value(null, Reason.UNKNOWN, target)
        return live(sample, state, now).copy(targetHundredthsGram = target)
    }
    companion object {
        fun live(sample: ScaleObservation?, state: DeviceState, now: Long): Value {
            val current = LiveTelemetry.scale(sample, state, now)
            if (current != null) return Value(current.hundredthsGram)
            return Value(null, when {
                state != DeviceState.READY -> Reason.DISCONNECTED
                sample == null -> Reason.NO_SCALE
                !sample.capabilities.weight -> Reason.UNAVAILABLE
                else -> Reason.STALE
            })
        }
    }
}
