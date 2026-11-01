package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskLayoutPolicyTest {
    @Test
    fun userTabletLandscapeUsesRailAndTwoPanes() {
        val decision = TaskLayoutPolicy.decide(windowWidthDp = 1138, availableHeightDp = 711)

        assertEquals(TaskNavigationMode.RAIL, decision.navigation)
        assertTrue(decision.twoPaneAllowed)
        assertFalse(decision.toolsInTopBar)
    }

    @Test
    fun userTabletPortraitKeepsRailButNotTwoPanes() {
        val decision = TaskLayoutPolicy.decide(windowWidthDp = 711, availableHeightDp = 1138)

        assertEquals(TaskNavigationMode.RAIL, decision.navigation)
        assertFalse(decision.twoPaneAllowed)
    }

    @Test
    fun navigationChangesAt600NotAtOrientation() {
        val narrow = TaskLayoutPolicy.decide(windowWidthDp = 599, availableHeightDp = 800)
        val threshold = TaskLayoutPolicy.decide(windowWidthDp = 600, availableHeightDp = 800)

        assertEquals(TaskNavigationMode.BOTTOM, narrow.navigation)
        assertTrue(narrow.toolsInTopBar)
        assertEquals(TaskNavigationMode.RAIL, threshold.navigation)
    }

    @Test
    fun expandedWidthStillNeedsMeasuredContentSpace() {
        assertFalse(TaskLayoutPolicy.decide(839, 700).twoPaneAllowed)
        assertTrue(TaskLayoutPolicy.decide(840, 700).twoPaneAllowed)
        assertFalse(TaskLayoutPolicy.decide(900, 700, primaryMinimumWidthDp = 500, secondaryMinimumWidthDp = 400).twoPaneAllowed)
    }

    @Test
    fun keyboardHeightMovesToolsInsteadOfClippingThem() {
        val keyboard = TaskLayoutPolicy.decide(windowWidthDp = 1138, availableHeightDp = 320)
        val enoughHeight = TaskLayoutPolicy.decide(windowWidthDp = 1138, availableHeightDp = 360)

        assertEquals(TaskNavigationMode.RAIL, keyboard.navigation)
        assertTrue(keyboard.toolsInTopBar)
        assertFalse(keyboard.twoPaneAllowed)
        assertFalse(enoughHeight.toolsInTopBar)
    }

    @Test
    fun measuredAxesLeave320DpOrUseFocusedMode() {
        assertEquals(TaskChartAxisMode.ALL_SCALES, TaskLayoutPolicy.chartAxes(528, 208))
        assertEquals(TaskChartAxisMode.FOCUSED_SCALE, TaskLayoutPolicy.chartAxes(527, 208))
        assertEquals(TaskChartAxisMode.FOCUSED_SCALE, TaskLayoutPolicy.chartAxes(240, 208))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeDimensionsAreNotValidLayoutEvidence() {
        TaskLayoutPolicy.decide(-1, 600)
    }
}
