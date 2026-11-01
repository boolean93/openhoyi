package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class HistoryActionsBudgetTest {
    @Test fun fullLabelsAndBothGapsMustFitBeforeAnyActionCanMoveInline() {
        val measuredFullLabels = listOf(260, 220, 400)
        val required = historyActionsNaturalWidth(measuredFullLabels, 20)
        assertEquals(920, required)
        assertFalse(required <= 919)
        assertTrue(required <= 920)
        assertTrue(required <= 1560)
    }
    @Test fun longNarrowLabelsKeepVerticalFallbackAndRtlDoesNotChangeBudget() {
        val longLabels = listOf(420, 260, 640)
        assertTrue(historyActionsNaturalWidth(longLabels, 20) > 400)
        assertEquals(historyActionsNaturalWidth(longLabels, 20), historyActionsNaturalWidth(longLabels.reversed(), 20))
    }
    @Test fun singleOrAbsentActionsHaveNoPhantomGap() {
        assertEquals(0, historyActionsNaturalWidth(emptyList(), 20))
        assertEquals(180, historyActionsNaturalWidth(listOf(180), 20))
    }
}
