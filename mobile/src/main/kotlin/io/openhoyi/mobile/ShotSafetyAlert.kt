package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.ShotSafetyPolicy

/** Maps the shared advisory reason to UI copy; it never grants a control or recovery permission. */
object ShotSafetyAlert {
    fun resource(shot: ExtractionState, coffee: DeviceState): Int? = when (ShotSafetyPolicy.reason(shot, coffee)) {
        ShotSafetyPolicy.Reason.UNKNOWN_AND_DISCONNECTED -> R.string.shot_safety_unknown_disconnected
        ShotSafetyPolicy.Reason.OUTCOME_UNKNOWN -> R.string.shot_safety_unknown
        ShotSafetyPolicy.Reason.ACTIVE_AND_DISCONNECTED -> R.string.shot_safety_active_disconnected
        null -> null
    }
}
