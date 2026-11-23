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
}
