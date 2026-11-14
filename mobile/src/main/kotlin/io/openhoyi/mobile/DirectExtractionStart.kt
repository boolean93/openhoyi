package io.openhoyi.mobile

import io.openhoyi.session.PreheatGate

/** Direct explicit starts retain the existing temperature gate and resolved stop mode. */
internal object DirectExtractionStart {
    fun offerPreheat(reason:PreheatGate.StartBlock?,correctedHundredthsC:Int?,targetC:Int):Boolean =
        reason==PreheatGate.StartBlock.TEMPERATURE_NOT_READY && correctedHundredthsC!=null &&
            correctedHundredthsC<(targetC-1)*100

}
