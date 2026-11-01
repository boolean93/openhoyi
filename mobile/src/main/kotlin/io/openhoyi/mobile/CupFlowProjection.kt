package io.openhoyi.mobile

/** Display-only rolling weight derivative. Never replaces the controller's original scale samples. */
internal object CupFlowProjection {
    fun values(points: List<ShotPoint>): List<Float?> {
        val window = ArrayDeque<ShotPoint>()
        var previous: ShotPoint? = null
        return points.map { point ->
            val weight = point.weightHundredthsGram
            val last = previous
            previous = point
            if (point.brewing == false || weight == null || weight < 0) {
                window.clear()
                return@map null
            }
            if (last == null || last.weightHundredthsGram == null || last.weightHundredthsGram < 0 ||
                point.elapsedMs <= last.elapsedMs || point.elapsedMs - last.elapsedMs > 1500 ||
                weight < last.weightHundredthsGram) window.clear()
            // Retain only fresh history, then use the actual sample nearest one second ago.
            // Sparse (e.g. 800 ms) input must not retain a 1600 ms base and blank a valid flow.
            while (window.isNotEmpty() && point.elapsedMs - window.first().elapsedMs > 1500) window.removeFirst()
            val base = window.filter { point.elapsedMs - it.elapsedMs >= 250 }
                .minByOrNull { kotlin.math.abs(point.elapsedMs - it.elapsedMs - 1000) }
            window.addLast(point)
            val duration = base?.let { point.elapsedMs - it.elapsedMs } ?: 0
            if (base == null || duration < 250 || duration > 1500) null
            else ((weight.toLong() - requireNotNull(base.weightHundredthsGram)) * 10.0 / duration).toFloat()
        }
    }
}

internal object CurveUseEvidence {
    fun matches(frame: io.openhoyi.protocol.ExtractionTelemetry): Boolean = frame.valveOpen && !frame.brewWait
}
