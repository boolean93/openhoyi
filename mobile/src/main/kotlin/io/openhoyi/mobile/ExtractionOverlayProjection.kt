package io.openhoyi.mobile

import kotlin.math.ceil

/** Same observed timeline, independent physical units. No synthetic future samples. */
internal data class ExtractionOverlayData(
    val pressure: List<Float?>, val flow: List<Float?>,
    val referencePressure: List<Float?>, val referenceFlow: List<Float?>,
    val pressureMaximum: Float, val flowMaximum: Float, val timeSeconds: Float,
)

internal object ExtractionOverlayProjection {
    fun build(points: List<ShotPoint>, reference: List<ShotPoint>): ExtractionOverlayData {
        val pressure = RealtimeShotProjection.values(points)
        val flow = CupFlowProjection.values(points)
        val oldPressure = RealtimeShotProjection.values(reference)
        val oldFlow = CupFlowProjection.values(reference)
        fun maximum(values: List<Float?>, floor: Float) = maxOf(floor, ceil(values.filterNotNull().maxOrNull() ?: 0f))
        val last = maxOf(points.lastOrNull()?.elapsedMs ?: 0L, reference.lastOrNull()?.elapsedMs ?: 0L)
        return ExtractionOverlayData(pressure, flow, oldPressure, oldFlow,
            maximum(pressure + oldPressure, 12f), maximum(flow + oldFlow, 3f), maxOf(30f, ceil(last / 10000f) * 10f))
    }
}
