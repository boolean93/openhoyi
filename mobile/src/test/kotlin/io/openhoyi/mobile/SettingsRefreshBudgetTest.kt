package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class SettingsRefreshBudgetTest {
    @Test fun readOnlyActionAvailabilityTracksCooldownWithoutConsumingIt() {
        val b=SettingsRefreshBudget()
        repeat(4) { assertTrue(b.available(1,10,180_011,explicit=true)) }
        assertTrue(b.claim(1,10,180_011,explicit=true))
        assertFalse(b.available(1,10,210_010,explicit=true))
        assertTrue(b.available(1,10,210_011,explicit=true))
        assertTrue(b.available(2,10,180_012,explicit=true))
        assertTrue(b.available(1,11,180_012,explicit=true))
        assertFalse(b.available(1,10,180_012,explicit=true))
    }
    @Test fun boundedAndCooledDown() {
        val b = SettingsRefreshBudget()
        assertTrue(b.claim(1, 10, 180_011))
        assertFalse(b.claim(1, 10, 180_012))
        assertTrue(b.claim(1, 10, 210_011))
        assertTrue(b.claim(1, 10, 240_011))
        assertFalse(b.claim(1, 10, 900_000))
        assertTrue(b.claim(1, 10, 900_000, explicit = true))
        assertFalse(b.claim(1, 10, 900_001, explicit = true))
        assertFalse(b.claim(1, 10, 930_000))
    }
    @Test fun newConnectionOrObservationRestartsBudget() {
        val b = SettingsRefreshBudget()
        assertTrue(b.claim(1, null, 1))
        assertFalse(b.claim(1, null, 2))
        assertTrue(b.claim(2, null, 2))
        assertTrue(b.claim(2, 2, 3))
    }
}
