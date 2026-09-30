package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState

/** One stop affordance policy shared by Home and Extraction; transport checks remain in MobileService. */
object StopActionPresentation {
    data class State(val visible: Boolean, val enabled: Boolean, val labelResource: Int)

    fun describe(shot: ExtractionState, coffee: DeviceState, serviceRunning: Boolean): State {
        val visible = ShotGate.active(shot)
        val enabled = serviceRunning && shot in setOf(ExtractionState.STARTING, ExtractionState.RUNNING,
            ExtractionState.OUTCOME_UNKNOWN) &&
            (shot != ExtractionState.OUTCOME_UNKNOWN || coffee == DeviceState.READY)
        val labelResource = when {
            shot == ExtractionState.STOP_REQUESTED -> R.string.stop_processing
            shot == ExtractionState.OUTCOME_UNKNOWN && coffee != DeviceState.READY -> R.string.stop_interrupted
            else -> R.string.home_stop
        }
        return State(visible, enabled, labelResource)
    }
}
