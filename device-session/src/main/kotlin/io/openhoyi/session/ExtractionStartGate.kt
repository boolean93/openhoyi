package io.openhoyi.session

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry

/** Product preflight decisions without curve catalogs or UI strings.
 * A null target means no curve was selected; validation remains the caller's proof.
 * This is not a dispatch permit: firmware, parameters, settings, tare and durable
 * recovery must still be checked by their existing owners before actual writes.
 */
object ExtractionStartGate {
    enum class Block {
        CURVE_MISSING, CURVE_UNVERIFIED, EXTRACTION_UNSETTLED, COFFEE_NOT_READY,
        IDLE_NOT_FRESH, ASLEEP, SLEEP_UNKNOWN, SCALE_NOT_READY, WEIGHT_NOT_FRESH, ALARM,
    }

    fun block(targetHundredthsGram: Int?, validated: Boolean, coffee: DeviceState,
        coffeeFrame: HoyiMessage?, coffeeAt: Long?, scale: DeviceState, weightAt: Long?,
        now: Long, shot: ExtractionState): Block? = when {
        targetHundredthsGram == null -> Block.CURVE_MISSING
        !validated -> Block.CURVE_UNVERIFIED
        DeviceConnectionGate.unsettled(shot) -> Block.EXTRACTION_UNSETTLED
        coffee != DeviceState.READY -> Block.COFFEE_NOT_READY
        coffeeFrame !is IdleTelemetry || coffeeAt == null || coffeeAt > now ||
            now - coffeeAt > 1500 -> Block.IDLE_NOT_FRESH
        coffeeFrame.sleepStateRaw == 1 -> Block.ASLEEP
        coffeeFrame.sleepStateRaw != 0 -> Block.SLEEP_UNKNOWN
        targetHundredthsGram > 0 && scale != DeviceState.READY -> Block.SCALE_NOT_READY
        targetHundredthsGram > 0 && (weightAt == null || weightAt > now ||
            now - weightAt > 1500) -> Block.WEIGHT_NOT_FRESH
        !CoffeeAlarmPolicy.permitsNewControl(coffeeFrame.alarmBits) -> Block.ALARM
        else -> null
    }
}
