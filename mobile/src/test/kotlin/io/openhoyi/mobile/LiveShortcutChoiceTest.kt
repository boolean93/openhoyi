package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class LiveShortcutChoiceTest {
    @Test fun scopeIsOnlyTheFourFavorites() {
        assertEquals(listOf(1, 2, 3, 4), LiveShortcutChoice.slots.toList())
    }

    @Test fun everyConflictingOperationIndependentlyBlocksSwitching() {
        assertNull(LiveShortcutChoice.operationBlock("busy", false, false, false, false, false, null, null))
        assertEquals("tare", LiveShortcutChoice.operationBlock("busy", false, false, false, false, false, "tare", null))
        assertEquals("unknown tare", LiveShortcutChoice.operationBlock("busy", false, false, false, false, false, "unknown tare", null))
        for (i in 0..4) {
            val s = List(5) { it == i }
            assertEquals("busy", LiveShortcutChoice.operationBlock("busy", s[0], s[1], s[2], s[3], s[4], null, null))
        }
    }

    @Test fun identicalCurveWithNewBindingIsNotTheDisplayedChoice() {
        val route = PresetSlots.Route.Ready("draft-a", 7, true)
        val shown = LiveShortcutChoice(1, "draft-a#old", "draft-a", null, route)
        assertNotEquals(shown, shown.copy(binding = "draft-a#new"))
        assertNotEquals(shown, shown.copy(slot = 4))
    }

    @Test fun openedChooserDoesNotTransferToReconnectedOrReplacementMachine() {
        val owner = Any()
        val device = Triple(Any(), "coffee", 3L)
        val opened = LiveShortcutSession(owner, device, 1, 2, 7)
        assertTrue(opened.matches(owner, device.copy(), 1, 2, 7))
        assertFalse(opened.matches(null, device, 1, 2, 7))
        assertFalse(opened.matches(owner, null, 1, 2, 7))
        assertFalse(opened.matches(Any(), device, 1, 2, 7))
        assertFalse(opened.matches(owner, device.copy(third = 4L), 1, 2, 7))
        assertFalse(opened.matches(owner, device, 2, 2, 7)) // Same service returned after unbind
        assertFalse(opened.matches(owner, device, 1, 4, 7)) // A -> B -> A preset churn
        assertFalse(opened.matches(owner, device, 1, 2, 8))
    }

    @Test fun pendingQueryDoesNotRunDispatchCheckOrClaimAnotherStart() {
        var now = 1000L
        var checks = 0
        val permit = ShortcutStartPermit(now, { now }) { checks++; true }
        assertFalse(permit.pending())
        assertTrue(permit.claim())
        assertTrue(permit.pending())
        assertFalse(permit.claim())
        assertEquals(1, checks)
        now += ShortcutStartPolicy.REQUEST_LIFETIME_MS
        assertFalse(permit.pending())
        assertFalse(permit.allows())
        assertEquals(1, checks)
    }
}
