package io.openhoyi.mobile

import io.openhoyi.session.PreheatGate
import io.openhoyi.session.ExtractionState

/** Direct explicit starts retain the existing temperature gate and resolved stop mode. */
internal object DirectExtractionStart {
    fun showStopModePreview(active:Boolean,hasProfile:Boolean,state:ExtractionState):Boolean =
        !active && state!=ExtractionState.OUTCOME_UNKNOWN && hasProfile

    fun offerPreheat(reason:PreheatGate.StartBlock?,correctedHundredthsC:Int?,targetC:Int):Boolean =
        reason==PreheatGate.StartBlock.TEMPERATURE_NOT_READY && correctedHundredthsC!=null &&
            correctedHundredthsC<(targetC-1)*100

    fun stopMode(profile:CurveProfile):ResourceMessage = if(profile.targetHundredthsGram>0)
        ResourceMessage(R.string.extraction_weight_control_advice)
    else ResourceMessage(R.string.extraction_no_weight_advice)
}
