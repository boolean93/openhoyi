package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ShortcutStartPolicyTest {
    @Test fun safeDefaultsAndCancelledPromptDoNotConfigureOrStart() {
        val initial = ShortcutStartPolicy.Preference()
        assertTrue(initial.showPrompt)
        assertFalse(initial.startsOnTap)
        assertFalse(initial.directStart)
        // Checking the draft checkbox changes nothing until a valid action is chosen.
        val uncheckedStoredValue = initial
        assertEquals(initial, uncheckedStoredValue)
        assertFalse(ShortcutStartPolicy.Preference(false, true).startsOnTap)
    }

    @Test fun bothValidActionsAndSettingsStoreAnExplicitChoice() {
        val initial = ShortcutStartPolicy.Preference()
        assertEquals(ShortcutStartPolicy.Preference(true, false), initial.choose(false))
        val on = initial.choose(true)
        assertTrue(on.startsOnTap); assertFalse(on.showPrompt)
        val off = on.choose(false)
        assertFalse(off.startsOnTap); assertFalse(off.showPrompt)
    }

    @Test fun oneTapClaimsOnlyOnceButKeepsCheckingUntilDispatch() {
        var now = 100L; var valid = true
        val permit = ShortcutStartPermit(now, { now }, { valid })
        assertTrue(permit.claim()); assertFalse(permit.claim())
        assertTrue(permit.allows())
        valid = false
        assertFalse(permit.allows())
        valid = true
        assertFalse(permit.allows()) // recovery/temperature readiness must never resurrect a request
    }

    @Test fun backgroundNavigationAndRecreationCannotResumeAnOldPermit() {
        val permit = ShortcutStartPermit(0, { 0 }, { true })
        assertTrue(permit.claim())
        permit.revoke()
        assertFalse(permit.allows()); assertFalse(permit.claim())
        // A new screen never receives this object through saved state or Intent.
        val fresh = ShortcutStartPermit(0, { 0 }, { true })
        assertTrue(fresh.claim()); assertFalse(permit.allows())
    }

    @Test fun fixedDeadlineDoesNotExtendOnRepeatedChecksAndClockRollbackFailsClosed() {
        var now = 0L
        val permit = ShortcutStartPermit(now, { now }, { true })
        assertTrue(permit.claim())
        now = 4_999; assertTrue(permit.allows())
        now = 5_000; assertFalse(permit.allows())
        now = 1; assertFalse(permit.allows())
        val rollback = ShortcutStartPermit(100, { 99 }, { true })
        assertFalse(rollback.claim())
    }

    @Test fun failedConditionOrExceptionIsPermanentAndCannotQueueForLaterReadiness() {
        var ready = false
        val permit = ShortcutStartPermit(0, { 0 }, { ready })
        assertFalse(permit.claim())
        ready = true; assertFalse(permit.claim()); assertFalse(permit.allows())
        val throwing = ShortcutStartPermit(0, { 0 }, { error("unavailable session") })
        assertFalse(throwing.claim()); assertFalse(throwing.allows())
    }
    @Test fun configurationTransitionsCannotDisappearBetweenDispatchChecks() {
        val context = ShortcutContentRevision("A")
        val expected = context.revision
        val permit = ShortcutStartPermit(0, { 0 }, { context.revision == expected })
        assertTrue(permit.claim())
        context.observe("A"); assertEquals(expected, context.revision)
        context.observe("B"); context.observe("A")
        assertEquals(expected + 2, context.revision)
        assertFalse(permit.allows())
    }

    @Test fun saveFailureSuppressesAllInstancesEvenWhenBothWritesFailUntilExplicitSuccess() {
        var current = ShortcutStartPolicy.Preference(true, true)
        val safety = ShortcutPreferenceSafety()
        var successful = false
        val write: (ShortcutStartPolicy.Preference) -> Boolean = { value ->
            // Model SharedPreferences: memory may change even when disk commit fails.
            current = value; successful
        }
        val prefs = ShortcutStartPreferences({ current }, write, safety)
        val otherScreen = ShortcutStartPreferences({ ShortcutStartPolicy.Preference(true, true) }, write, safety)
        assertFalse(prefs.choose(false)); assertTrue(prefs.suppressed)
        assertFalse(prefs.read().startsOnTap); assertFalse(otherScreen.read().startsOnTap)
        assertFalse(prefs.read().showPrompt)
        assertFalse(prefs.choose(true)); assertFalse(otherScreen.read().startsOnTap)
        successful = true
        assertTrue(prefs.choose(false)); assertFalse(prefs.suppressed)
        assertFalse(prefs.read().startsOnTap)
        assertTrue(prefs.choose(true)); assertTrue(prefs.read().startsOnTap)
    }

    @Test fun thrownPersistenceFailureAlsoKeepsOldEnabledValueSuppressed() {
        val safety = ShortcutPreferenceSafety()
        val prefs = ShortcutStartPreferences({ ShortcutStartPolicy.Preference(true, true) }, { error("disk") }, safety)
        assertFalse(prefs.choose(false)); assertTrue(prefs.suppressed)
        assertFalse(prefs.read().startsOnTap)
    }

}
