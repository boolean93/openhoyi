package io.openhoyi.mobile

import io.openhoyi.protocol.ExtractionTelemetry

/** Local display heuristic, never a measurement of cup mass or a device control decision. */
internal data class BrewFeedbackResult(
    val shotId: String,
    val machineSeconds: Int,
    val preinfusionSeconds: Int,
    val earlyWaterTenthsMl: Int,
    val finalWaterTenthsMl: Int,
    val flowHeuristic: Double,
    val level: BrewFeedbackClips.Level,
)

/** Owned by the same thread as the shot observations. Has no timer, BLE or Android dependency.
 * Only the existing extraction owner may supply an observed end; silence never finalizes here. */
internal class BrewFeedbackTracker {
    private class Cup(val id: String, val slot: Int, val preinfusionSeconds: Int) {
        var valid = true
        var lastAt: Long? = null
        var lastSeconds = 0
        var lastWater = 0
        var maxActiveSeconds = 0
        var earlyWater: Int? = null
    }
    private var cup: Cup? = null

    /** slot is the reported telemetry slot/phase, not the packed outbound start slot. */
    fun begin(shotId: String, slot: Int, preinfusionSeconds: Int) {
        require(shotId.isNotBlank() && slot in 1..8 && preinfusionSeconds in 0..127)
        cup = Cup(shotId, slot, preinfusionSeconds)
    }
    fun observe(shotId: String, frame: ExtractionTelemetry, atElapsedMs: Long) {
        val current = cup?.takeIf { it.id == shotId && it.valid } ?: return
        val lastAt = current.lastAt
        if (atElapsedMs < 0 || lastAt?.let { atElapsedMs <= it } == true) return
        if (frame.slotOrPhase != current.slot || frame.elapsedSeconds !in 0..65535 ||
            frame.totalWaterTenthsMl !in 0..65535 || frame.pressureTenthsBar !in 0..255 ||
            frame.flowTenthsMlPerSecond !in 0..255 || frame.statusBits !in 0..255 ||
            (lastAt != null && (frame.elapsedSeconds < current.lastSeconds ||
                frame.totalWaterTenthsMl < current.lastWater))) {
            current.valid = false
            return
        }
        // Preserve the old display phase fallback for valid packets lacking both valve flags.
        // This is not the passive-shot detector or permission to start/stop a shot.
        val active = frame.statusBits and 96 == 0 || frame.valveOpen
        val freshProgress = lastAt != null && lastAt > 0 && atElapsedMs - lastAt < 3000 &&
            (frame.elapsedSeconds > current.lastSeconds || frame.totalWaterTenthsMl > current.lastWater)
        val manualThreshold = frame.slotOrPhase == 6 || frame.slotOrPhase == 7
        val physicalPhase = if (manualThreshold)
            frame.pressureTenthsBar > 0 || frame.flowTenthsMlPerSecond > 0
        else frame.pressureTenthsBar > 10 || frame.flowTenthsMlPerSecond > 8
        val extracting = active && !frame.brewWait && (physicalPhase || freshProgress)
        if (active) {
            current.maxActiveSeconds = maxOf(current.maxActiveSeconds, frame.elapsedSeconds)
            if (extracting && frame.elapsedSeconds <= 10) current.earlyWater = frame.totalWaterTenthsMl
        }
        current.lastAt = atElapsedMs
        current.lastSeconds = frame.elapsedSeconds
        current.lastWater = frame.totalWaterTenthsMl
    }
    fun finish(shotId: String, observedEnd: Boolean): BrewFeedbackResult? {
        val current = cup?.takeIf { it.id == shotId } ?: return null
        cup = null
        val early = current.earlyWater ?: return null
        if (!observedEnd || !current.valid || current.maxActiveSeconds <= 14 || current.lastWater < early)
            return null
        val denominator = maxOf(1, current.maxActiveSeconds - current.preinfusionSeconds - 10)
        // Separate /10 conversions and subtraction match the original JS floating-point order.
        val rate = (current.lastWater / 10.0 - early / 10.0) / denominator
        if (!rate.isFinite()) return null
        val level = when {
            rate > 2.2 -> BrewFeedbackClips.Level.HIGH_FLOW
            rate <= 1.5 -> BrewFeedbackClips.Level.LOW_FLOW
            else -> BrewFeedbackClips.Level.BRAVO
        }
        return BrewFeedbackResult(current.id, current.maxActiveSeconds, current.preinfusionSeconds,
            early, current.lastWater, rate, level)
    }
}
