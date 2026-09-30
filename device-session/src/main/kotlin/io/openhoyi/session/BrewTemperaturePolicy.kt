package io.openhoyi.session

import kotlin.math.abs

/** Same corrected-temperature rule used by the product UI and final studio dispatch gate. */
object BrewTemperaturePolicy {
    fun correctedTemperature(rawHundredthsC:Int,compensationTenthsC:Int):Int =
        rawHundredthsC - compensationTenthsC * 10
    fun isAtTarget(correctedHundredthsC:Int,targetC:Int):Boolean =
        abs(correctedHundredthsC.toLong() - targetC.toLong() * 100) <= 100
}
