package io.openhoyi.mobile

import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.TelemetryFreshness
import java.util.Locale

/** Display only. Global tare state does not establish a baseline for this cup. */
internal class ExtractionScaleReadout {
    enum class Source { RAW_SCALE, BEVERAGE }
    data class ScaleOrigin(val connectionEpoch: Long, val tareGeneration: Long)
    data class OpeningWeight(val frameAt: Long, val hundredthsGram: Int, val origin: ScaleOrigin)
    data class CompletionWeight(val hundredthsGram: Int, val source: Source)
    data class Owner(val hub:Any,val controller:Any,val shotSerial:Long,val scaleEpoch:Long,val address:String)
    data class Proof(val shotSerial:Long,val baselineHundredthsGram:Int,val confirmedAtMs:Long)
    enum class Reason { NO_SCALE, DISCONNECTED, UNAVAILABLE, STALE, END_UNAVAILABLE, UNKNOWN }
    data class Value(val hundredthsGram: Int?, val reason: Reason? = null,
        val targetHundredthsGram: Int? = null, val ended: Boolean = false, val source: Source = Source.RAW_SCALE) {
        fun confirmedBeverageSample(): Int? = hundredthsGram?.takeIf {
            source == Source.BEVERAGE && !ended && reason == null
        }?.coerceAtLeast(0)
        val number: String get() = hundredthsGram?.let { String.format(Locale.ROOT, "%.1f", it / 100.0) } ?: "—"
    }
    private var trace: String? = null
    private var target: Int? = null
    private var terminal: Value? = null
    private var opening: OpeningWeight? = null
    private var endDelta: Int? = null
    private var endRawHundredthsGram: Int? = null
    private var endCandidate: Value? = null
    private var brewingObserved = false
    private var owner:Owner? = null
    private var proof:Proof? = null
    private var proofRevoked = false
    private var lastFrameAt: Long? = null

    fun begin(id: String, targetHundredthsGram: Int?, appOwner:Owner? = null, openingWeight: OpeningWeight? = null) {
        opening = openingWeight
        endDelta = null
        trace = id; target = targetHundredthsGram?.takeIf { it > 0 }
        terminal = null; endRawHundredthsGram = null; endCandidate = null; brewingObserved = false
        owner = appOwner?.takeIf { target != null }; proof = null; proofRevoked = false
        lastFrameAt = null
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
        endDelta = null; endRawHundredthsGram = null; endCandidate = null
    }
    /** First idle notification after brewing owns the endpoint, even if its reading is missing. */
    fun observedIdle(sample: ScaleObservation?, state: DeviceState, now: Long, origin: ScaleOrigin?) {
        if (trace == null || terminal != null || endCandidate != null || !brewingObserved) return
        endCandidate = reading(sample, state, now)
        // Keep the original endpoint too: tare proof can be revoked before finish.
        val raw = sample?.hundredthsGram?.takeIf { endCandidate?.hundredthsGram != null }
        endRawHundredthsGram = raw
        val start = opening
        if (raw != null && start != null && start.origin == origin) {
            endDelta = (raw - start.hundredthsGram).takeIf { it >= 0 }
        }
    }
    /** The live readout and result card share the first qualified idle reading after brewing. */
    fun observeFrame(
        frame: HoyiMessage, at: Long, now: Long, currentShot: Boolean,
        sample: ScaleObservation?, scaleState: DeviceState, origin: ScaleOrigin?,
    ) {
        if (trace == null || terminal != null || !currentShot || !TelemetryFreshness.isFresh(at, now) ||
            lastFrameAt?.let { at <= it } == true) return
        if (frame !is ExtractionTelemetry && frame !is IdleTelemetry) return
        lastFrameAt = at
        if (frame is ExtractionTelemetry && frame.valveOpen && !frame.brewWait) {
            if (opening == null) opening = openingWeight(frame, at, sample, scaleState, origin)
            brewing()
        }
        if (frame is IdleTelemetry) observedIdle(sample, scaleState, at, origin)
    }

    fun finish(observedEnd: Boolean) {
        if (trace == null || terminal != null) return
        terminal = if (!observedEnd) Value(null, Reason.UNKNOWN, target, true)
            else Value(endCandidate?.hundredthsGram,
                if (endCandidate?.hundredthsGram == null) Reason.END_UNAVAILABLE else null, target, true, endCandidate?.source ?: Source.RAW_SCALE)
    }
    fun completionWeight(shotId: String): CompletionWeight? {
        val end = terminal?.takeIf { trace == shotId } ?: return null
        val grams = end.hundredthsGram ?: return null
        endDelta?.let { return CompletionWeight(it, Source.BEVERAGE) }
        return if (end.source == Source.BEVERAGE && !proofRevoked && grams >= 0)
            CompletionWeight(grams, Source.BEVERAGE)
        else endRawHundredthsGram?.let { CompletionWeight(it, Source.RAW_SCALE) }
    }

    fun value(sample: ScaleObservation?, state: DeviceState, now: Long,
        unknown: Boolean = false): Value {
        terminal?.let { return it }
        if (unknown) return Value(null, Reason.UNKNOWN, target)
        return reading(sample, state, now).copy(targetHundredthsGram = target)
    }
    companion object {
        fun openingWeight(frame: ExtractionTelemetry, at: Long, sample: ScaleObservation?, state: DeviceState,
            origin: ScaleOrigin?): OpeningWeight? {
            if (origin == null || !frame.valveOpen || frame.brewWait || frame.elapsedSeconds !in 0..1) return null
            val raw = LiveTelemetry.scale(sample, state, at) ?: return null
            return OpeningWeight(at, raw.hundredthsGram, origin)
        }

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
