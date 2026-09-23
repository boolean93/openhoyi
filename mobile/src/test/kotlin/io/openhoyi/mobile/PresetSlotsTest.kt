package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class PresetSlotsTest {
    @Test fun defaultsToFirstFiveFactoryCurvesAndRejectsInvalidStorage() {
        for (slot in 1..5) {
            assertEquals("factory-v3-00$slot", PresetSlots.curveId(slot, null))
            assertEquals("factory-v3-00$slot", PresetSlots.curveId(slot, "capture-1"))
        }
        assertEquals("factory-v3-100", PresetSlots.curveId(2, "factory-v3-100"))
        assertThrows(IllegalArgumentException::class.java) { PresetSlots.curveId(7, null) }
    }
}
