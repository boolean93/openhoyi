package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ShortcutStartPolicyTest {
    @Test fun defaultOffAndStoredOnUseOnlyTheCurrentSwitchChoice() {
        val initial = ShortcutStartPolicy.Preference()
        assertFalse(initial.startsOnTap)
        assertFalse(initial.directStart)
        assertTrue(ShortcutStartPolicy.Preference(true).startsOnTap)
    }

    @Test fun eitherSwitchCanEnableAndRevokeTheSharedChoice() {
        val initial = ShortcutStartPolicy.Preference()
        assertEquals(ShortcutStartPolicy.Preference(false), initial.choose(false))
        val on = initial.choose(true)
        assertTrue(on.startsOnTap)
        val off = on.choose(false)
        assertFalse(off.startsOnTap)
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
        var current = ShortcutStartPolicy.Preference(true)
        val safety = ShortcutPreferenceSafety()
        var successful = false
        val write: (ShortcutStartPolicy.Preference) -> Boolean = { value ->
            // Model SharedPreferences: memory may change even when disk commit fails.
            current = value; successful
        }
        val prefs = ShortcutStartPreferences({ current }, write, safety)
        val otherScreen = ShortcutStartPreferences({ ShortcutStartPolicy.Preference(true) }, write, safety)
        assertFalse(prefs.choose(false)); assertTrue(prefs.suppressed)
        assertFalse(prefs.read().startsOnTap); assertFalse(otherScreen.read().startsOnTap)
        assertFalse(prefs.choose(true)); assertFalse(otherScreen.read().startsOnTap)
        successful = true
        assertTrue(prefs.choose(false)); assertFalse(prefs.suppressed)
        assertFalse(prefs.read().startsOnTap)
        assertTrue(prefs.choose(true)); assertTrue(prefs.read().startsOnTap)
    }

    @Test fun thrownPersistenceFailureAlsoKeepsOldEnabledValueSuppressed() {
        val safety = ShortcutPreferenceSafety()
        val prefs = ShortcutStartPreferences({ ShortcutStartPolicy.Preference(true) }, { error("disk") }, safety)
        assertFalse(prefs.choose(false)); assertTrue(prefs.suppressed)
        assertFalse(prefs.read().startsOnTap)
    }

    @Test fun failedAndRecoveredWritesNotifyEveryScreenWithoutDependingOnStoredKeyChanges() {
        var stored = ShortcutStartPolicy.Preference(true)
        var diskWorks = false
        val safety = ShortcutPreferenceSafety()
        val writer = ShortcutStartPreferences({ stored }, { value ->
            if (diskWorks) stored = value
            diskWorks
        }, safety)
        val reader = ShortcutStartPreferences({ stored }, { false }, safety)
        var homeOn = reader.read().startsOnTap
        var settingsOn = writer.read().startsOnTap
        val stopHome = reader.observe { homeOn = reader.read().startsOnTap }
        val stopSettings = writer.observe { settingsOn = writer.read().startsOnTap }
        val stopBrokenObserver = reader.observe { error("one screen cannot block the others") }
        assertFalse(writer.choose(false))
        assertTrue(stored.startsOnTap) // Both disk writes failed; there was no key-change callback.
        assertFalse(homeOn); assertFalse(settingsOn)
        diskWorks = true
        assertTrue(writer.choose(true))
        assertTrue(homeOn); assertTrue(settingsOn)
        stopHome(); stopSettings(); stopBrokenObserver()
        assertTrue(writer.choose(false))
        assertTrue(homeOn); assertTrue(settingsOn) // Detached views receive no more callbacks.
    }

    @Test fun removingHomeObserverDuringNavigationDoesNotRevokeANewExtractionPermit() {
        val safety = ShortcutPreferenceSafety()
        val prefs = ShortcutStartPreferences({ ShortcutStartPolicy.Preference(true) }, { true }, safety)
        var notifications = 0
        val stop = prefs.observe { notifications++ }
        val revision = prefs.revision
        stop()
        val permit = ShortcutStartPermit(0, { 0 }, { prefs.revision == revision && prefs.read().startsOnTap })
        assertTrue(permit.claim()); assertEquals(0, notifications)
        assertTrue(prefs.choose(false)); assertEquals(0, notifications)
        assertFalse(permit.allows()) // Cleanup is inert; a later real choice revokes the old tap.
    }

}
