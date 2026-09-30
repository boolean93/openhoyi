package io.openhoyi.session

/** Advisory classification only. No command dispatch, recovery acknowledgement or device state mutation.
 * A lost link cannot establish a physical stop; reconnecting does not resolve an unknown outcome.
 */
object ShotSafetyPolicy {
    enum class Reason { UNKNOWN_AND_DISCONNECTED, OUTCOME_UNKNOWN, ACTIVE_AND_DISCONNECTED }

    fun reason(shot: ExtractionState, coffee: DeviceState): Reason? = when {
        shot == ExtractionState.OUTCOME_UNKNOWN && coffee != DeviceState.READY -> Reason.UNKNOWN_AND_DISCONNECTED
        shot == ExtractionState.OUTCOME_UNKNOWN -> Reason.OUTCOME_UNKNOWN
        coffee != DeviceState.READY && shot in setOf(ExtractionState.STARTING,
            ExtractionState.RUNNING, ExtractionState.STOP_REQUESTED) -> Reason.ACTIVE_AND_DISCONNECTED
        else -> null
    }
}
