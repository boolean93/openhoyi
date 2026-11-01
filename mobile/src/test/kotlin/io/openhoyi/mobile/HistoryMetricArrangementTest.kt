package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class HistoryMetricArrangementTest {
    private fun row(mask: Int, gap: Int, widths: List<Int>, heights: List<Int>) =
        HistoryMetricArrangement(mask, gap, widths, heights, widths, heights, mask, heights.maxOrNull() ?: 0)
    private val original = row(0, 54, listOf(226, 283, 252), listOf(214, 214, 156))
    private fun nativeCandidates(gap: Int): List<HistoryMetricArrangement> = (0..7).map { mask ->
        val widths = listOf(226 to 412, 283 to 553, 252 to 441).mapIndexed { index, (vertical, inline) ->
            if ((mask and (1 shl index)) != 0) inline else vertical }
        val heights = listOf(214 to 135, 214 to 135, 156 to 77).mapIndexed { index, (vertical, inline) ->
            if ((mask and (1 shl index)) != 0) inline else vertical }
        row(mask, gap, widths, heights)
    }
    @Test fun sufficientBudgetKeepsTheOriginalMeasuredLayout() {
        val result = selectHistoryMetricArrangement(false, 1299, original, nativeCandidates(36), 36)
        assertSame(original, result.arrangement)
        assertEquals("budget_sufficient", result.reason)
    }
    @Test fun retainedNativeWidthsRecoverTheActualFailingBudgetWithMixedCells() {
        val candidates = nativeCandidates(54) + nativeCandidates(36)
        val selected = selectHistoryMetricArrangement(true, 1299, original, candidates, 36)
        assertEquals(3, selected.arrangement.inlineMask)
        assertEquals(36, selected.arrangement.gapPx)
        assertEquals(1289L, selected.arrangement.totalNaturalWidthPx)
        assertEquals(156, selected.arrangement.measuredHeightPx)
        assertEquals("compact_spacing_shorter", selected.reason)
    }
    @Test fun fullWidthsAndBothGapsMustFitExactly() {
        val candidates = nativeCandidates(54) + nativeCandidates(36)
        assertEquals(3, selectHistoryMetricArrangement(true, 1289, original, candidates, 36).arrangement.inlineMask)
        assertSame(original, selectHistoryMetricArrangement(true, 1288, original, candidates, 36).arrangement)
    }
    @Test fun originalSpacingWinsBeforeCompactSpacingEvenWhenCompactCouldBeShorter() {
        val default = row(1, 54, listOf(300, 300, 300), listOf(170, 150, 150))
        val compact = row(3, 36, listOf(300, 300, 300), listOf(156, 150, 150))
        val result = selectHistoryMetricArrangement(true, 1299, original, listOf(compact, default), 36)
        assertSame(default, result.arrangement)
        assertEquals("original_spacing_shorter", result.reason)
    }
    @Test fun equalHeightUsesFewerModeChangesThenStableMaskOrder() {
        val one = row(1, 54, listOf(300, 300, 300), listOf(156, 150, 150))
        val two = one.copy(inlineMask = 2, resolvedInlineMask = 2)
        val three = one.copy(inlineMask = 3, resolvedInlineMask = 3)
        assertSame(one, selectHistoryMetricArrangement(true, 1299, original, listOf(three, two, one), 36).arrangement)
        assertSame(one, selectHistoryMetricArrangement(true, 1299, original, listOf(one, three, two), 36).arrangement)
    }
    @Test fun nativeMeasurementCannotBeReplacedByClippedOrShrunkCells() {
        val good = nativeCandidates(36)[3]
        for (bad in listOf(good.copy(measuredWidthsPx = listOf(400, 540, 240)),
            good.copy(measuredHeightsPx = listOf(120, 120, 150)), good.copy(resolvedInlineMask = 1),
            good.copy(measuredHeightPx = 155), good.copy(inlineMask = 8, resolvedInlineMask = 8))) {
            assertSame(original, selectHistoryMetricArrangement(true, 1299, original, listOf(bad), 36).arrangement)
        }
    }
    @Test fun narrowLongTextKeepsOriginalWrappingAndRtlDoesNotChangeWidthBudget() {
        val narrow = original.copy(measuredHeightPx = 450)
        val tooWide = row(3, 36, listOf(1200, 900, 400), listOf(156, 156, 156))
        assertSame(narrow, selectHistoryMetricArrangement(true, 400, narrow, listOf(tooWide), 36).arrangement)
        assertEquals(tooWide.totalNaturalWidthPx, tooWide.copy(naturalWidthsPx = tooWide.naturalWidthsPx.reversed()).totalNaturalWidthPx)
    }
    @Test fun unchangedHeightDoesNotSpendCompactSpacingAndWidthsDoNotOverflow() {
        val noBenefit = original.copy(gapPx = 36)
        assertSame(original, selectHistoryMetricArrangement(true, 1299, original, listOf(noBenefit), 36).arrangement)
        val huge = row(3, 36, listOf(Int.MAX_VALUE, Int.MAX_VALUE), listOf(1, 1))
        assertTrue(huge.totalNaturalWidthPx > Int.MAX_VALUE)
        assertFalse(huge.completeSingleRow(Int.MAX_VALUE))
    }
}
