package io.openhoyi.mobile

/** The vertical bands used by the real non-comparison Canvas bounds. */
internal object HistoryPlotBudget {
    fun top(line: Float, density: Float, live: Boolean) = line * 1.5f + (if (live) 8f else 16f) * density
    fun bottom(line: Float, density: Float, live: Boolean) = line * (if (live) 1.5f else 2.5f) + (if (live) 12f else 24f) * density
    fun minimumHeight(line: Float, density: Float, live: Boolean, plotDp: Float): Int {
        require(line.isFinite() && density.isFinite() && plotDp.isFinite() && line > 0f && density > 0f && plotDp > 0f)
        return kotlin.math.ceil(plotDp * density + top(line, density, live) + bottom(line, density, live)).toInt()
    }
}
