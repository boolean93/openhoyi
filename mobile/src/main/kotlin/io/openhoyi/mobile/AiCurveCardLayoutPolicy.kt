package io.openhoyi.mobile

/** Pixel decisions after the host/font measurements. Never changes font scale or recipe data. */
internal object AiCurveCardLayoutPolicy {
    fun cardWidth(availablePixels: Int, maximumPixels: Int): Int =
        minOf(maximumPixels.coerceAtLeast(0), (availablePixels.coerceAtLeast(0).toLong() * 78 / 100).toInt())
    fun diagramWidth(viewportPixels: Int, requiredPixels: Int): Int =
        maxOf(viewportPixels.coerceAtLeast(0), requiredPixels.coerceAtLeast(0))
    /** Two-line labels reserve the wider line only; caller supplies actual font measurements. */
    fun stageLabelWidth(targetPixels: Float, waterPixels: Float, paddingPixels: Int): Int {
        require(targetPixels.isFinite() && targetPixels >= 0 && waterPixels.isFinite() && waterPixels >= 0)
        return (kotlin.math.ceil(maxOf(targetPixels, waterPixels).toDouble()).toInt().toLong() +
            paddingPixels.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    /** A stage target is a centred short mark, never a duration or viewport-width line. */
    fun stageMarkerLength(slotPixels: Float, sideGapPixels: Float, maximumPixels: Float): Float {
        require(slotPixels.isFinite() && sideGapPixels.isFinite() && maximumPixels.isFinite())
        return minOf((slotPixels - 2 * sideGapPixels.coerceAtLeast(0f)).coerceAtLeast(0f), maximumPixels.coerceAtLeast(0f))
    }
    fun stackActions(availablePixels: Int, intrinsicWidths: List<Int>, gapPixels: Int): Boolean {
        val needed = intrinsicWidths.sumOf { it.coerceAtLeast(0).toLong() } +
            gapPixels.coerceAtLeast(0).toLong() * (intrinsicWidths.size - 1).coerceAtLeast(0)
        return needed > availablePixels.coerceAtLeast(0)
    }
}
