package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class HomePortraitBudgetTest {
    @Test fun readableWidthControlsInlineMachineRatherThanPixelResolution() {
        assertFalse(HomePortraitBudget.inlineMachine(463, 1f))
        assertTrue(HomePortraitBudget.inlineMachine(464, 1f))
        assertTrue(HomePortraitBudget.inlineMachine(532, 1f))
        assertFalse(HomePortraitBudget.inlineMachine(328, 1f))
        assertFalse(HomePortraitBudget.inlineMachine(532, 1.5f))
        assertTrue(HomePortraitBudget.inlineLastCurve(508, 1f))
        assertFalse(HomePortraitBudget.inlineLastCurve(304, 1f))
        assertFalse(HomePortraitBudget.inlineLastCurve(508, 1.5f))
    }

    @Test fun broadShortViewportReturnsSpaceToQuickCurves() {
        val budget = HomePortraitBudget.fit(270, 560, 1f)
        assertTrue(budget.dial < 184)
        assertTrue(budget.hero < 156)
        assertTrue(216 + 156 - budget.dial - budget.hero >= 100)
    }

    @Test fun narrowTallPhoneKeepsWidthBoundDialsAndHero() {
        assertEquals(HomePortraitBudget(136, 156), HomePortraitBudget.fit(136, 660, 1f))
    }

    @Test fun largeTextCanGrowWithoutExceedingCardWidth() {
        val normal = HomePortraitBudget.fit(270, 560, 1f)
        val large = HomePortraitBudget.fit(270, 560, 1.5f)
        assertTrue(large.dial > normal.dial)
        assertEquals(136, HomePortraitBudget.fit(136, 560, 1.5f).dial)
    }

    @Test fun nearbyViewportsHaveNoResolutionSpecificJump() {
        val first = HomePortraitBudget.fit(270, 559, 1f)
        val next = HomePortraitBudget.fit(270, 561, 1f)
        assertTrue(kotlin.math.abs(first.dial - next.dial) <= 1)
        assertTrue(kotlin.math.abs(first.hero - next.hero) <= 2)
    }
}
