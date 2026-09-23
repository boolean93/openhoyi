package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class VisibleScreensTest {
    @Test fun onePageLeavingDoesNotHideAnotherPage() {
        val screens = VisibleScreens()
        assertTrue(screens.set("home", true))
        assertTrue(screens.visible)
        assertFalse(screens.set("settings", true))
        assertFalse(screens.set("home", false))
        assertTrue(screens.visible)
        assertFalse(screens.set("home", false))
        assertTrue(screens.set("settings", false))
        assertFalse(screens.visible)
    }
}
