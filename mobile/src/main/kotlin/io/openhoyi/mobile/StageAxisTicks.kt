package io.openhoyi.mobile

/** Axis labels only; stage pressure mapping remains 0–12 bar. */
internal object StageAxisTicks {
    fun forHeight(plotHeight: Float, minimumSpacing: Float): List<Int> {
        require(minimumSpacing.isFinite() && minimumSpacing > 0f)
        if (!plotHeight.isFinite() || plotHeight < minimumSpacing) return emptyList()
        return when {
            plotHeight / 4f >= minimumSpacing -> listOf(0, 3, 6, 9, 12)
            plotHeight / 2f >= minimumSpacing -> listOf(0, 6, 12)
            else -> listOf(0, 12)
        }
    }
}
