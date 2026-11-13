package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class HomePortraitBudgetTest {
    @Test fun readableWidthControlsInlineMachineRatherThanPixelResolution() {
        assertFalse(HomePortraitBudget.inlineMachine(519, 1f))
        assertTrue(HomePortraitBudget.inlineMachine(520, 1f))
        // A 568 dp tablet freeform window has 524 dp after home side padding.
        assertTrue(HomePortraitBudget.inlineMachine(524, 1f))
        assertFalse(HomePortraitBudget.inlineMachine(1000, 1.6f))
        assertFalse(HomePortraitBudget.inlineMachine(328, 1f))
        assertFalse(HomePortraitBudget.inlineMachine(532, 1.5f))
        assertTrue(HomePortraitBudget.compactPreset(248, 1f))
        assertFalse(HomePortraitBudget.compactPreset(160, 1f))
        assertFalse(HomePortraitBudget.compactPreset(248, 1.3f))
        assertTrue(HomePortraitBudget.inlineLastCurve(508, 1f))
        assertFalse(HomePortraitBudget.inlineLastCurve(304, 1f))
        assertFalse(HomePortraitBudget.inlineLastCurve(508, 1.5f))
    }

    @Test fun machineRetainsVisualWeightInInlineLayout() {
        assertEquals(180, HomePortraitBudget.machineWidth(532))
        assertTrue(HomePortraitBudget.machineWidth(532) >= HomePortraitBudget.fit(145, 560, 1f).dial)
        assertEquals(260, HomePortraitBudget.machineWidth(1000))
        for (width in 300..1000) for (scale in listOf(1f, 1.3f, 1.5f)) {
            if (HomePortraitBudget.inlineMachine(width, scale)) {
                val dialWidth = (width - HomePortraitBudget.machineWidth(width) - 64) / 2
                assertTrue(dialWidth >= (140 * scale).toInt())
                assertTrue(HomePortraitBudget.inlineDialLimit(width) >= (140 * scale).toInt())
            }
        }
    }

    @Test fun compactTypographyOnlyAffectsBroadNearSquareWindows() {
        assertTrue(HomePortraitBudget.compactTypography(568, 641))
        assertTrue(HomePortraitBudget.compactTypography(641, 568))
        assertFalse(HomePortraitBudget.compactTypography(360, 720))
        assertFalse(HomePortraitBudget.compactTypography(1000, 600))
        assertFalse(HomePortraitBudget.compactTypography(568, 0))
    }

    @Test fun squareScopePreservesOrdinaryPortraitAndLandscape() {
        assertFalse(HomePortraitBudget.compactTypography(711, 1138))
        assertFalse(HomePortraitBudget.compactTypography(1138, 711))
        assertFalse(HomePortraitBudget.compactTypography(479, 479))
        assertTrue(HomePortraitBudget.compactTypography(480, 600))
        assertFalse(HomePortraitBudget.compactTypography(480, 601))
        assertTrue(HomePortraitBudget.compactTypography(600, 480))
        assertFalse(HomePortraitBudget.compactTypography(601, 480))
        assertTrue(HomePortraitBudget.compactTypography(568, 640))
    }

    @Test fun broadShortViewportReturnsSpaceToQuickCurves() {
        val budget = HomePortraitBudget.fit(270, 560, 1f)
        assertTrue(budget.dial < 184)
        assertTrue(budget.hero >= budget.dial)
        assertTrue(budget.hero <= 222)
    }

    @Test fun narrowTallPhoneKeepsWidthBoundDialsAndHero() {
        assertEquals(HomePortraitBudget(136, 164), HomePortraitBudget.fit(136, 660, 1f))
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
