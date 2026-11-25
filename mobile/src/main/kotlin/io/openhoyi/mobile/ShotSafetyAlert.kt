package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.ShotSafetyPolicy
import io.openhoyi.session.ShotRecoveryState

/** Maps the shared advisory reason to UI copy; it never grants a control or recovery permission. */
object ShotSafetyAlert {
    internal fun recoveryResource(recovery: ShotRecoveryState, shot: ExtractionState, coffee: DeviceState,
        appOwner: ShotRecoveryState.Ownership?, manualOwner: ShotRecoveryState.Ownership?,
        newManualRecord: Boolean): Int? {
        if (!recovery.pending) return null
        if (coffee == DeviceState.READY && shot != ExtractionState.OUTCOME_UNKNOWN) {
            val ownsAppShot = shot in setOf(ExtractionState.STARTING, ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED, ExtractionState.ENDED_OBSERVED) && recovery.owns(appOwner)
            val ownsManualShot = newManualRecord && recovery.owns(manualOwner)
            if (ownsAppShot || ownsManualShot) return null
        }
        return R.string.machine_recovery_shot_restart
    }

    fun resource(shot: ExtractionState, coffee: DeviceState): Int? = when (ShotSafetyPolicy.reason(shot, coffee)) {
        ShotSafetyPolicy.Reason.UNKNOWN_AND_DISCONNECTED -> R.string.shot_safety_unknown_disconnected
        ShotSafetyPolicy.Reason.OUTCOME_UNKNOWN -> R.string.shot_safety_unknown
        ShotSafetyPolicy.Reason.ACTIVE_AND_DISCONNECTED -> R.string.shot_safety_active_disconnected
        null -> null
    }
}
