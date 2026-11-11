package io.openhoyi.mobile

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.DeviceConnectionGate
import io.openhoyi.session.ExtractionStartGate
import io.openhoyi.session.ScaleReadingPolicy

/** Host-side preflight; session also checks readiness and allowed wire frames at send time. */
class ShotGate(
    private val resolve: (Int, Array<out Any>) -> String,
    private val mockMode: Boolean = BuildConfig.MOCK_MODE,
) {
    constructor(context: android.content.Context) : this({ id, args -> context.getString(id, *args) })
    private val alarms = MachineAlarms(resolve)

    companion object {
        /** Unknown outcome must permit reconnection so a user can explicitly retry Stop. */
        fun mayReconnectCoffee(state: ExtractionState): Boolean = state !in setOf(
            ExtractionState.STARTING, ExtractionState.RUNNING, ExtractionState.STOP_REQUESTED,
        )
        fun active(state: ExtractionState): Boolean = DeviceConnectionGate.unsettled(state)
    }

    fun startBlock(profile: CurveProfile?, coffee: DeviceState, coffeeFrame: HoyiMessage?, coffeeAt: Long?,
        scale: DeviceState, scaleObservation: ScaleObservation?, now: Long, shot: ExtractionState,
        validated: Boolean = profile?.let(CurveCatalog::validated) == true,
    ): String? =
        startBlockMessage(profile, coffee, coffeeFrame, coffeeAt, scale, scaleObservation, now, shot, validated)?.render { id, args ->
            resolve(id, args.map { requireNotNull(it) }.toTypedArray())
        }

    fun startBlockMessage(profile: CurveProfile?, coffee: DeviceState, coffeeFrame: HoyiMessage?, coffeeAt: Long?,
        scale: DeviceState, scaleObservation: ScaleObservation?, now: Long, shot: ExtractionState,
        validated: Boolean = profile?.let(CurveCatalog::validated) == true,
    ): ResourceMessage? =
        when (ExtractionStartGate.block(profile?.targetHundredthsGram, validated, coffee,
            // Real scales share one observation for capabilities, value validity and receive time.
            coffeeFrame, coffeeAt, scale, scaleObservation?.takeIf {
                ScaleReadingPolicy.supportedWeight(it.hundredthsGram)
            }?.receivedAtMs, now, shot, scaleTargetSupported(scaleObservation))) {
            ExtractionStartGate.Block.CURVE_MISSING -> ResourceMessage(R.string.start_block_curve_missing)
            ExtractionStartGate.Block.CURVE_UNVERIFIED -> ResourceMessage(R.string.start_block_curve_unverified)
            ExtractionStartGate.Block.EXTRACTION_UNSETTLED -> ResourceMessage(R.string.start_block_extraction_unsettled)
            ExtractionStartGate.Block.COFFEE_NOT_READY -> ResourceMessage(R.string.start_block_coffee_not_ready)
            ExtractionStartGate.Block.IDLE_NOT_FRESH -> ResourceMessage(R.string.start_block_idle_not_fresh)
            ExtractionStartGate.Block.ASLEEP -> ResourceMessage(R.string.start_block_asleep)
            ExtractionStartGate.Block.SLEEP_UNKNOWN -> ResourceMessage(R.string.start_block_sleep_unknown)
            ExtractionStartGate.Block.SCALE_NOT_READY -> ResourceMessage(R.string.start_block_scale_not_ready)
            ExtractionStartGate.Block.SCALE_CONTROL_UNVERIFIED -> ResourceMessage(R.string.scale_control_unverified)
            ExtractionStartGate.Block.WEIGHT_NOT_FRESH -> ResourceMessage(R.string.start_block_weight_not_fresh)
            ExtractionStartGate.Block.ALARM -> alarms.startBlockMessage((coffeeFrame as IdleTelemetry).alarmBits)
            null -> null
        }

    /** Mock builds have no real scale evidence. Keep the bypass in one place so UI and service cannot diverge. */
    private fun scaleTargetSupported(observation: ScaleObservation?): Boolean =
        mockMode || observation?.eligibleForTargetWeight == true
}
