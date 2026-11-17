package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class HomeLandscapeBudgetTest {
    @Test fun extremeHeightsRetainReadableMinimumInsteadOfNegativeOrCollapsedContent() {
        for (height in listOf(0, 1, 47, 80, 120, 180, 240, 359, 360, 361, 719, 720, 721)) {
            val presets = HomeLandscapeBudget.presets(height)
            assertEquals(48, presets.minimumHeight)
            assertTrue(presets.textSp in 14..17)
            val remaining = HomeLandscapeBudget.remainingHeight(height, 220)
            assertEquals(if (height <= 220) 0 else height - 220, remaining)
            for (scale in listOf(.9f, 1f, 1.1f)) {
                val dial = HomeLandscapeBudget.dial(240, remaining, 90, scale)
                assertTrue(dial >= (180 * scale.coerceAtLeast(1f)).toInt())
                assertTrue(dial <= 240)
                if (height < 270) assertTrue(dial + 90 > height)
            }
        }
        // This asserts the preserved natural-size budget, not that Android can scroll.
        // ScrollView reachability still needs the compiled component/device checks.
    }
    @Test fun presetSizesAdaptToActualHeightWithinReadableBounds() {
        val budgets = listOf(100, 240, 360, 480, 600, 720, 2000).map(HomeLandscapeBudget::presets)
        assertEquals(HomeLandscapeBudget.Presets(48, 14, 24), budgets.first())
        assertEquals(HomeLandscapeBudget.Presets(48, 17, 40), budgets.last())
        assertTrue(budgets.zipWithNext().all { (a,b) -> a.minimumHeight <= b.minimumHeight && a.textSp <= b.textSp })
        budgets.forEach { assertTrue(it.minimumHeight == 48 && it.textSp in 14..17 && it.previewHeight in 24..40) }
        // Bottom content takes priority; overflow is retained when readable hero no longer fits.
        assertEquals(380, HomeLandscapeBudget.remainingHeight(600, 220))
        assertEquals(0, HomeLandscapeBudget.remainingHeight(100, 220))
    }
    @Test fun measuredContentAndInsetsDriveBudgetAcrossAspectRatios() {
        // Static dp scenarios only; Android tests must verify actual measured pixels.
        for ((width, height) in listOf(1024 to 683, 960 to 600, 1280 to 720,
            720 to 360, 600 to 600, 393 to 851)) {
            for (scale in listOf(.9f, 1f, 1.1f)) for (lever in listOf(false, true)) {
                val viewport = height - 48 // representative system/shell allowance
                val other = (220 * scale).toInt() + if (lever) 20 else 0
                val remaining = HomeLandscapeBudget.remainingHeight(viewport, other)
                assertEquals(maxOf(0, viewport - other), remaining)
                val warningHeight = 72
                assertEquals(maxOf(0, remaining - warningHeight),
                    HomeLandscapeBudget.remainingHeight(viewport, other + warningHeight))
                assertTrue("$width/$height", remaining <= viewport)
            }
        }
        assertEquals(0, HomeLandscapeBudget.remainingHeight(120, 400))
    }

    @Test fun instrumentsUseRemainingViewportRatherThanFixedHeroHeight() {
        assertEquals(200, HomeLandscapeBudget.dial(250, 290, 90, 1f))
        assertEquals(216, HomeLandscapeBudget.dial(250, 450, 90, 1f))
    }
    @Test fun shortWindowsKeepReadableDialsAndAllowScrolling() {
        assertEquals(180, HomeLandscapeBudget.dial(250, 100, 90, 1f))
        assertEquals(198, HomeLandscapeBudget.dial(250, 100, 100, 1.1f))
        assertEquals(180, HomeLandscapeBudget.dial(250, 100, 90, .9f))
        assertEquals(160, HomeLandscapeBudget.dial(160, 100, 90, 1.1f))
    }
    @Test fun representativeHeightsAndAppFontSizesRespectReadabilityAndWidth() {
        for (scale in listOf(.9f, 1f, 1.1f)) for (height in listOf(284, 400, 520, 700)) {
            val supporting = (90 * scale).toInt()
            val dial = HomeLandscapeBudget.dial(250, height - 210, supporting, scale)
            val remaining = height - 210
            if (remaining >= supporting + (180 * scale.coerceAtLeast(1f)).toInt())
                assertTrue(dial + supporting <= remaining)
            assertTrue(dial <= 250)
            assertTrue(dial >= (180 * scale.coerceAtLeast(1f)).toInt())
        }
    }
    @Test fun commonLandscapeWidthsReserveReadableDialsBeforeDecorativeSpace() {
        val windows = listOf(800 to 480, 960 to 600, 1024 to 600, 1138 to 711, 1280 to 800)
        for ((width, _) in windows) for (scale in listOf(.9f, 1f, 1.1f)) {
            // Real Home width after the existing 72dp rail and 22dp page side paddings.
            val content = width - TaskLayoutPolicy.RAIL_WIDTH_DP - 44
            val budget = HomeLandscapeBudget.widths(content, scale)
            assertFalse("Unexpected stack at $width/$scale", budget.stacked)
            assertTrue(budget.image >= 160)
            val boilerWidth = content - 2 * budget.outer - budget.imageGap - budget.image
            val cardWidth = (boilerWidth - budget.cardGap) / 2
            val dialWidth = cardWidth - 2 * budget.cardPadding - 4
            assertTrue("Dial lost width at $width/$scale: $dialWidth", dialWidth >= (180 * scale.coerceAtLeast(1f)).toInt())
            assertTrue(budget.image <= ((content - 96) * .39f).toInt())
        }
    }
    @Test fun constrainedWidthsSpendWhitespaceAndThenUseVerticalScrollFallback() {
        val narrow = HomeLandscapeBudget.widths(684, 1.1f)
        assertEquals(8, narrow.outer)
        assertEquals(8, narrow.cardPadding)
        assertEquals(12, narrow.cardGap)
        assertFalse(narrow.stacked)
        assertTrue(HomeLandscapeBudget.widths(500, 1.1f).stacked)
        assertTrue(HomeLandscapeBudget.widths(320, 1.1f).stacked)
        assertTrue(TaskLayoutPolicy.compactHome(360, portrait = false))
    }
    @Test fun roomyWindowsKeepTheirOriginalMachineProportion() {
        val content = 1280 - TaskLayoutPolicy.RAIL_WIDTH_DP - 44
        val budget = HomeLandscapeBudget.widths(content, 1.1f)
        assertEquals(40, budget.outer)
        assertEquals(16, budget.cardPadding)
        assertEquals(24, budget.cardGap)
        assertEquals(((content - 96) * .39f).toInt(), budget.image)
    }
}
