package io.openhoyi.mobile

import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import java.util.Locale

/** Display only. Global tare state does not establish a baseline for this cup. */
internal class ExtractionScaleReadout {
    enum class Source { RAW_SCALE, BEVERAGE }
    data class Owner(val hub:Any,val controller:Any,val shotSerial:Long,val scaleEpoch:Long,val address:String)
    data class Proof(val shotSerial:Long,val baselineHundredthsGram:Int,val confirmedAtMs:Long)
    enum class Reason { NO_SCALE, DISCONNECTED, UNAVAILABLE, STALE, END_UNAVAILABLE, UNKNOWN }
    data class Value(val hundredthsGram: Int?, val reason: Reason? = null,
        val targetHundredthsGram: Int? = null, val ended: Boolean = false, val source: Source = Source.RAW_SCALE) {
        val number: String get() = hundredthsGram?.let { String.format(Locale.ROOT, "%.1f", it / 100.0) } ?: "—"
    }
    private var trace: String? = null
    private var target: Int? = null
    private var terminal: Value? = null
    private var endObserved = false
    private var endCandidate: Value? = null
    private var brewingObserved = false
    private var owner:Owner? = null
    private var proof:Proof? = null
    private var proofRevoked = false

    fun begin(id: String, targetHundredthsGram: Int?, appOwner:Owner? = null) {
        trace = id; target = targetHundredthsGram?.takeIf { it > 0 }
        terminal = null; endObserved = false; endCandidate = null; brewingObserved = false
        owner = appOwner?.takeIf { target != null }; proof = null; proofRevoked = false
    }
    /** A mismatch revokes net display for this cup, even if the same address later reconnects. */
    fun observeProof(current:Owner?, confirmed:Proof?, state:ExtractionState) {
        if (terminal != null || proofRevoked) return
        val bound = owner ?: return // Passive/Mock/global tare never establish an App-shot baseline.
        val matches = current != null && current.hub === bound.hub && current.controller === bound.controller &&
            current.shotSerial == bound.shotSerial && current.scaleEpoch == bound.scaleEpoch &&
            current.address.equals(bound.address, ignoreCase = true)
        if (!matches || state in setOf(ExtractionState.IDLE, ExtractionState.OUTCOME_UNKNOWN)) {
            proof = null; proofRevoked = true; return
        }
        if (state == ExtractionState.STARTING) return // A baseline is not display authority before start success.
        if (confirmed == null) {
            if (state != ExtractionState.STARTING) { proof = null; proofRevoked = true }
            return
        }
        if (confirmed.shotSerial != bound.shotSerial || confirmed.confirmedAtMs < 0 ||
            confirmed.baselineHundredthsGram !in -50..50 || (proof != null && proof != confirmed)) {
            proof = null; proofRevoked = true; return
        }
        proof = confirmed
    }
    private fun reading(sample:ScaleObservation?, state:DeviceState, now:Long):Value {
        val raw = live(sample,state,now)
        val baseline = proof?.takeUnless { proofRevoked } ?: return raw
        return if (raw.hundredthsGram != null && sample != null && sample.receivedAtMs >= baseline.confirmedAtMs)
            raw.copy(hundredthsGram = raw.hundredthsGram - baseline.baselineHundredthsGram, source = Source.BEVERAGE)
        else raw
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
        endCandidate = reading(sample, state, now)
    }
    fun finish(observedEnd: Boolean) {
        if (trace == null || terminal != null) return
        terminal = if (!observedEnd) Value(null, Reason.UNKNOWN, target, true)
            else Value(endCandidate?.hundredthsGram,
                if (endCandidate?.hundredthsGram == null) Reason.END_UNAVAILABLE else null, target, true, endCandidate?.source ?: Source.RAW_SCALE)
    }
    fun value(sample: ScaleObservation?, state: DeviceState, now: Long,
        unknown: Boolean = false): Value {
        terminal?.let { return it }
        if (unknown) return Value(null, Reason.UNKNOWN, target)
        return reading(sample, state, now).copy(targetHundredthsGram = target)
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
