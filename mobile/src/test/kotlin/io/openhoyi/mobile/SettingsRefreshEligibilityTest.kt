package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class SettingsRefreshEligibilityTest {
    @Test fun finalLeverUnknownDoesNotPreventSyncOrBecomeAControlPermit() {
        // Final lever UNKNOWN is not an active transport; its control journal is untouched.
        assertTrue(SettingsRefreshEligibility.allowed(true, true, false, false, false))
        assertFalse(SettingsRefreshEligibility.allowed(true, true, false, true, false))
    }
    @Test fun everySafetyBoundaryBlocksSync() {
        assertFalse(SettingsRefreshEligibility.allowed(false, true, false, false, false))
        assertFalse(SettingsRefreshEligibility.allowed(true, false, false, false, false))
        assertFalse(SettingsRefreshEligibility.allowed(true, true, true, false, false))
        assertFalse(SettingsRefreshEligibility.allowed(true, true, false, false, true))
    }
}
