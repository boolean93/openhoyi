package io.openhoyi.mobile

import kotlin.math.abs
import kotlin.math.roundToLong

/** Summary of recorded evidence only; never use chart interpolation or raw scale readings. */
internal object ComparisonDifferences {
    enum class Metric(val decimals: Int) { PRESSURE(1), WATER(1), TEMPERATURE(1), NET_WEIGHT(2) }
    enum class Unavailable { NOT_ENDED, NO_SAMPLES, NO_BREWING, WATER_RESET, NO_NET_WEIGHT, ESTIMATED, NET_SEGMENTS }

    sealed interface Reading {
        // Integer in the displayed precision, so equality and subtraction match the UI.
        data class Value(val displayUnits: Long) : Reading
        data class Missing(val reason: Unavailable) : Reading
    }

    data class Difference(val firstHigher: Boolean, val displayUnits: Long)

    fun difference(first: Reading, second: Reading): Difference? =
        if (first is Reading.Value && second is Reading.Value)
            Difference(first.displayUnits > second.displayUnits, abs(first.displayUnits - second.displayUnits))
        else null

    fun summarize(status: String, points: List<ShotPoint>): Map<Metric, Reading> {
        fun missing(reason: Unavailable) = Metric.entries.associateWith { Reading.Missing(reason) }
        if (status != "ENDED") return missing(Unavailable.NOT_ENDED)
        if (points.isEmpty()) return missing(Unavailable.NO_SAMPLES)
        val start = points.indexOfFirst { it.brewing == true }
        if (start < 0) return missing(Unavailable.NO_BREWING)
        val fromStart = points.subList(start, points.size)
        val brewing = fromStart.filter { it.brewing == true }
        val endpoint = fromStart.last()
        val pressure: Reading
        val temperature: Reading
        if (brewing.size < 2) {
            pressure = Reading.Missing(Unavailable.NO_BREWING)
            temperature = pressure
        } else {
            pressure = Reading.Value(brewing.maxOf { it.pressureTenthsBar }.toLong())
            val temperatures = brewing.map { it.temperatureHundredthsC }.sorted()
            val middle = temperatures.size / 2
            val median = if (temperatures.size % 2 == 0)
                (temperatures[middle - 1].toLong() + temperatures[middle]) / 2.0 else temperatures[middle].toDouble()
            temperature = Reading.Value((median / 10).roundToLong())
        }
        val water = if (fromStart.any { it.waterTenthsMl < 0 } ||
            fromStart.zipWithNext().any { (a, b) -> b.waterTenthsMl < a.waterTenthsMl })
            Reading.Missing(Unavailable.WATER_RESET)
        else Reading.Value(endpoint.waterTenthsMl.toLong())
        val weight = when {
            // Even if no earlier readings survived, a positive segment ID proves a reset or source change.
            fromStart.any { (it.beverageSegmentId ?: 0) > 0 } -> Reading.Missing(Unavailable.NET_SEGMENTS)
            endpoint.beverageHundredthsGram == null || endpoint.beverageSegmentId == null ->
                Reading.Missing(Unavailable.NO_NET_WEIGHT)
            endpoint.beverageSource == BeverageWeightSource.ESTIMATED -> Reading.Missing(Unavailable.ESTIMATED)
            endpoint.beverageSource == BeverageWeightSource.CONFIRMED ->
                Reading.Value(endpoint.beverageHundredthsGram.toLong())
            else -> Reading.Missing(Unavailable.NO_NET_WEIGHT)
        }
        return mapOf(Metric.PRESSURE to pressure, Metric.WATER to water, Metric.TEMPERATURE to temperature, Metric.NET_WEIGHT to weight)
    }
}
