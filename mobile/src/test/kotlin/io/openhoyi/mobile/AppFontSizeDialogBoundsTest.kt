package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AppFontSizeDialogBoundsTest {
    @Test fun freeformUsesOwnerWidthAndKeepsMarginsAtEveryDensity() {
        for (density in listOf(1f, 1.875f, 2.5f)) for (widthDp in listOf(320, 400, 568, 800, 1280)) {
            val owner = (widthDp * density).toInt()
            val width = AppFontSizeDialogBounds.width(owner, density)
            assertTrue(width > 0 && width <= owner - (32 * density).toInt())
            assertTrue(width <= (560 * density).toInt())
        }
        assertEquals(690, AppFontSizeDialogBounds.width(750, 1.875f))
    }
    @Test fun resizingRecomputesWidthRatherThanKeepingTabletMinimum() {
        assertEquals(560, AppFontSizeDialogBounds.width(1200, 1f))
        assertEquals(368, AppFontSizeDialogBounds.width(400, 1f))
        assertEquals(536, AppFontSizeDialogBounds.width(568, 1f))
    }
}
