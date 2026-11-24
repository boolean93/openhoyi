package io.openhoyi.mobile

import io.openhoyi.session.ExtractionState
import io.openhoyi.session.ShotStartObservation

/** Labels derived from machine observation, not a GATT callback; shared with the isolated QA view. */
internal object ExtractionStartObservationPresentation {
    fun displayState(state: ExtractionState, observation: ShotStartObservation?, liveFlow: Boolean): ExtractionState {
        if (state != ExtractionState.STARTING && state != ExtractionState.RUNNING) return state
        return when (observation) {
            ShotStartObservation.WAITING_FOR_MACHINE -> ExtractionState.STARTING
            ShotStartObservation.NOT_CONFIRMED -> ExtractionState.OUTCOME_UNKNOWN
            ShotStartObservation.MACHINE_EXTRACTION_OBSERVED -> ExtractionState.RUNNING
            null -> if (state == ExtractionState.RUNNING && !liveFlow) ExtractionState.STARTING else state
        }
    }

    fun stateLabel(state: ExtractionState) = when (state) {
        ExtractionState.IDLE -> R.string.machine_settings_tab_standby
        ExtractionState.STARTING -> R.string.extraction_state_starting
        ExtractionState.RUNNING -> R.string.extraction_state_running
        ExtractionState.STOP_REQUESTED -> R.string.extraction_state_stopping
        ExtractionState.ENDED_OBSERVED -> R.string.extraction_state_ended
        ExtractionState.OUTCOME_UNKNOWN -> R.string.extraction_state_unknown
    }

    fun label(observation: ShotStartObservation?): Int? = when (observation) {
        ShotStartObservation.WAITING_FOR_MACHINE -> R.string.extraction_start_sent_waiting
        ShotStartObservation.NOT_CONFIRMED -> R.string.extraction_start_not_confirmed
        ShotStartObservation.MACHINE_EXTRACTION_OBSERVED -> R.string.extraction_start_flow_observed
        null -> null
    }
}
