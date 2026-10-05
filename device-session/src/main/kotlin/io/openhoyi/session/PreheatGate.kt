package io.openhoyi.session

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.Settings

/** Product preheat decisions, without UI strings or curve-catalog dependencies.
 * Callers still own sample freshness, durable recovery and final dispatch authorization.
 */
object PreheatGate {
    enum class StartBlock { SETTINGS_MISSING, MODE_CHANGED, PREPARATION_MISMATCH, TEMPERATURE_NOT_READY }
    enum class CancelBlock { EXTRACTION_UNSETTLED, COFFEE_NOT_READY, IDLE_MISSING, IDLE_STALE, NOT_AWAKE }

    fun startBlock(settings: Settings?, correctedHundredthsC: Int?, profileId: String,
        targetC: Int, preparation: BrewPreparation): StartBlock? {
        if (settings == null) return StartBlock.SETTINGS_MISSING
        val studio = settings.flags and 0x04 != 0
        if (preparation.active) {
            if (!studio) return StartBlock.MODE_CHANGED
            if (!preparation.matches(profileId, targetC)) return StartBlock.PREPARATION_MISMATCH
        }
        if (studio && (correctedHundredthsC == null ||
                !BrewPreparation.isAtTarget(correctedHundredthsC, targetC)))
            return StartBlock.TEMPERATURE_NOT_READY
        return null
    }

    fun cancelBlock(coffeeState: DeviceState, frame: HoyiMessage?, receivedAtMs: Long?, nowMs: Long,
        shotState: ExtractionState, unresolvedShot: Boolean): CancelBlock? {
        if (unresolvedShot || DeviceConnectionGate.unsettled(shotState))
            return CancelBlock.EXTRACTION_UNSETTLED
        if (coffeeState != DeviceState.READY) return CancelBlock.COFFEE_NOT_READY
        val idle = frame as? IdleTelemetry ?: return CancelBlock.IDLE_MISSING
        if (!TelemetryFreshness.isFresh(receivedAtMs, nowMs))
            return CancelBlock.IDLE_STALE
        if (idle.sleepStateRaw != 0) return CancelBlock.NOT_AWAKE
        return null
    }
}
