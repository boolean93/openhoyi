package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AppVisibilityTest {
    @Test fun recreationKeepsOneForegroundWindowUntilActualBackground() {
        val visibility = AppVisibility()
        val events = mutableListOf<Boolean>()
        visibility.observe("service", events::add)
        visibility.started("home")
        repeat(3) {
            visibility.stopped("home", changingConfiguration = true)
            visibility.destroyed("home", changingConfiguration = true)
            // No elapsed-time limit: a slow replacement never fabricates a background edge.
            assertTrue(visibility.visible)
            visibility.started("home")
        }
        visibility.stopped("home", changingConfiguration = false)
        visibility.destroyed("home", changingConfiguration = false)
        assertEquals(listOf(false, true, false), events)
    }

    @Test fun everyPageAndOverlappingInstancesParticipateWithoutDuplicateEdges() {
        val visibility = AppVisibility()
        val events = mutableListOf<Boolean>()
        visibility.observe("service", events::add)
        visibility.started("home")
        visibility.started("curve")
        visibility.stopped("home", false)
        visibility.started("history")
        visibility.stopped("curve", false)
        visibility.started("history")
        visibility.stopped("curve", false)
        assertTrue(visibility.visible)
        visibility.stopped("history", false)
        assertEquals(listOf(false, true, false), events)
    }

    @Test fun recreationOfBackgroundOrNeverStartedPageCannotMakeAppVisible() {
        val visibility = AppVisibility()
        visibility.stopped("not-started", true)
        visibility.destroyed("not-started", true)
        assertFalse(visibility.visible)
        visibility.started("home")
        visibility.stopped("home", false)
        visibility.stopped("home", true)
        visibility.destroyed("home", true)
        assertFalse(visibility.visible)
        // Restoring a saved owner in a new process is not itself a started callback.
        val restartedProcess = AppVisibility()
        assertFalse(restartedProcess.visible)
        restartedProcess.started("home")
        assertTrue(restartedProcess.visible)
    }

    @Test fun abandonedReplacementReleasesRetainedVisibility() {
        val visibility = AppVisibility()
        visibility.started("home")
        visibility.stopped("home", true)
        visibility.destroyed("home", true)
        assertTrue(visibility.visible)
        visibility.destroyed("home", false)
        assertFalse(visibility.visible)
    }

    @Test fun serviceObserversReceiveCurrentStateAndDetachIndependently() {
        val visibility = AppVisibility()
        visibility.started("curve")
        val old = mutableListOf<Boolean>()
        val replacement = mutableListOf<Boolean>()
        visibility.observe("old-service", old::add)
        visibility.observe("new-service", replacement::add)
        visibility.unobserve("old-service")
        visibility.stopped("curve", false)
        assertEquals(listOf(true), old)
        assertEquals(listOf(true, false), replacement)
        visibility.unobserve("new-service")
        visibility.started("home")
        assertEquals(listOf(true, false), replacement)
    }

    @Test fun oneRebuildingPageDoesNotLoseAnotherVisiblePage() {
        val visibility = AppVisibility()
        visibility.started("home")
        visibility.started("settings")
        visibility.stopped("home", true)
        visibility.stopped("settings", false)
        assertTrue(visibility.visible)
        visibility.started("home")
        visibility.stopped("home", false)
        assertFalse(visibility.visible)
    }
}
