package io.openhoyi.mobile

import io.openhoyi.session.ShotStartObservation

/** Labels derived from machine observation, not a GATT callback; shared with the isolated QA view. */
internal object ExtractionStartObservationPresentation {
    fun label(observation: ShotStartObservation?): Int? = when (observation) {
        ShotStartObservation.WAITING_FOR_MACHINE -> R.string.extraction_start_sent_waiting
        ShotStartObservation.NOT_CONFIRMED -> R.string.extraction_start_not_confirmed
        ShotStartObservation.MACHINE_EXTRACTION_OBSERVED -> R.string.extraction_start_flow_observed
        null -> null
    }
}
