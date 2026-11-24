package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AutomaticSettingsRefreshTest {
    @Test fun oneAfterPassiveGraceAndNoTimedOrForegroundRetry() {
        val gate=AutomaticSettingsRefresh()
        val current=Any()
        assertFalse(gate.claim(current,1,5,180_006,true))
        assertFalse(gate.claim(current,1,5,180_355,true))
        assertTrue(gate.claim(current,1,5,180_356,true))
        for(i in 1..12) {
            assertFalse(gate.claim(current,1,5,185_000L+i*1000,false))
            assertFalse(gate.claim(current,1,5,185_500L+i*1000,true))
        }
    }
    @Test fun explicitRecipeRetryUsesSameBaselineAfterCooldownWithoutRearmingAutomaticReads() {
        val gate = AutomaticSettingsRefresh()
        val device = Any()
        assertFalse(gate.claim(device, 1, 5, 0, true))
        assertTrue(gate.claim(device, 1, 5, 350, true))
        assertFalse(gate.claim(device, 1, 5, 1000, true, explicit = true))
        assertFalse(gate.claim(device, 1, 5, 30_350, true))
        assertFalse(gate.claim(device, 1, 5, 30_350, false, explicit = true))
        assertTrue(gate.claim(device, 1, 5, 30_350, true, explicit = true))
        assertFalse(gate.claim(device, 1, 5, 30_351, true, explicit = true))
        assertFalse(gate.claim(device, 1, 5, 61_000, true))
    }
    @Test fun changedObservationOrConnectionStartsNewGraceButUnsafeIntervalCannotCount() {
        val gate=AutomaticSettingsRefresh()
        val current=Any()
        assertFalse(gate.claim(current,1,5,181_000,true))
        assertFalse(gate.claim(current,1,5,181_349,false))
        assertFalse(gate.claim(current,1,5,181_350,true))
        assertTrue(gate.claim(current,1,5,181_700,true))
        assertFalse(gate.claim(current,1,6,183_000,false))
        assertFalse(gate.claim(current,1,6,363_006,true))
        assertTrue(gate.claim(current,1,6,363_356,true))
        val replacement=Any()
        assertFalse(gate.claim(replacement,2,6,500_000,true))
        assertTrue(gate.claim(replacement,2,6,500_350,true))
    }
    @Test fun lateOrdinaryReportUpdatesOnlyTheSameSourceNotice() {
        val source = Any()
        val replacement = Any()
        val notice = AutomaticSettingsSyncNotice(source, 4, 1000, 7000)
        val waiting = AutomaticSettingsSyncNotice.Status.WAITING_FOR_NEW_REPORT
        assertEquals(waiting, notice.status(source, source, 4, 1001, true, 4000))
        assertEquals(waiting, notice.status(source, source, 5, 999, true, 4000))
        assertEquals(waiting, notice.status(source, replacement, 5, 3500, true, 4000))
        assertEquals(waiting, notice.status(source, source, 5, 3500, false, 4000))
        assertEquals(AutomaticSettingsSyncNotice.Status.OBSERVED,
            notice.status(source, source, 5, 4001, true, 4001))
        assertNull(notice.status(replacement, replacement, 5, 4001, true, 4001))
        assertNull(notice.status(source, source, 5, 4001, true, 7000))
    }
}
