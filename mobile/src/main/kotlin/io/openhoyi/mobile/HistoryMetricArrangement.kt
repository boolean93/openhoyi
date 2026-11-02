package io.openhoyi.mobile

/** Native measurements only: the selector never estimates glyph widths or changes text size. */
internal data class HistoryMetricArrangement(
    val inlineMask: Int,
    val gapPx: Int,
    val naturalWidthsPx: List<Int>,
    val naturalHeightsPx: List<Int>,
    val measuredWidthsPx: List<Int>,
    val measuredHeightsPx: List<Int>,
    val resolvedInlineMask: Int,
    val measuredHeightPx: Int
) {
    val totalNaturalWidthPx: Long get() = naturalWidthsPx.sumOf { it.toLong() } +
        gapPx.toLong() * (naturalWidthsPx.size - 1).coerceAtLeast(0)
    fun completeSingleRow(availableWidthPx: Int): Boolean = gapPx >= 0 && naturalWidthsPx.size in 0..30 &&
        inlineMask >= 0 && inlineMask < (1 shl naturalWidthsPx.size) &&
        naturalWidthsPx.size == naturalHeightsPx.size && naturalWidthsPx.all { it >= 0 } &&
        naturalHeightsPx.all { it >= 0 } && measuredWidthsPx == naturalWidthsPx &&
        measuredHeightsPx == naturalHeightsPx && resolvedInlineMask == inlineMask &&
        totalNaturalWidthPx <= availableWidthPx && measuredHeightPx == (naturalHeightsPx.maxOrNull() ?: 0)
}

internal data class HistoryMetricSelection(val arrangement: HistoryMetricArrangement, val reason: String)

internal fun selectHistoryMetricArrangement(insufficientBudget: Boolean, availableWidthPx: Int,
    original: HistoryMetricArrangement, candidates: List<HistoryMetricArrangement>, compactGapPx: Int
): HistoryMetricSelection {
    if (!insufficientBudget) return HistoryMetricSelection(original, "budget_sufficient")
    fun best(gap: Int): HistoryMetricArrangement? = candidates.filter {
        it.gapPx == gap && it.naturalWidthsPx.size == original.naturalWidthsPx.size &&
            it.completeSingleRow(availableWidthPx) && it.measuredHeightPx < original.measuredHeightPx
    }.minWithOrNull(compareBy<HistoryMetricArrangement> { it.measuredHeightPx }
        .thenBy { Integer.bitCount(it.inlineMask xor original.inlineMask) }.thenBy { it.inlineMask })
    best(original.gapPx)?.let { return HistoryMetricSelection(it, "original_spacing_shorter") }
    best(compactGapPx)?.let { return HistoryMetricSelection(it, "compact_spacing_shorter") }
    return HistoryMetricSelection(original, "no_complete_shorter_candidate")
}
