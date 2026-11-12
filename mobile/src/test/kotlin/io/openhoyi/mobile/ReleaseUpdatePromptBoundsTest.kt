package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdatePromptBoundsTest {
    @Test fun portraitLandscapeAndNarrowWindowsKeepTheCardWithinVisibleArea() {
        for ((width, height) in listOf(360 to 720, 720 to 360, 800 to 1280, 1280 to 800, 240 to 320)) {
            for (density in listOf(1f, 2f)) {
                val w = (width * density).toInt(); val h = (height * density).toInt()
                val bounds = releaseUpdatePromptBounds(w, h, density)
                assertTrue(bounds.width > 0); assertTrue(bounds.maxHeight > 0)
                assertTrue(bounds.width + bounds.right * 2 <= w)
                assertTrue(bounds.maxHeight + bounds.top * 2 <= h)
                assertTrue(bounds.width <= (360 * density).toInt())
            }
        }
    }
}
