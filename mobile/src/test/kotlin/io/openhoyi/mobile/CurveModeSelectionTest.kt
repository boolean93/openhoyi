package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class CurveModeSelectionTest {
    @Test fun loadingAndUnchangedSelectionNeverNormalizeExistingBits() {
        for (flow in listOf(false, true)) for (pressure in listOf(false, true)) {
            val loaded = CurveModeSelection(flow, pressure)
            assertEquals(flow, loaded.flow)
            assertEquals(pressure, loaded.pressure)
            assertEquals(loaded, loaded.selectFlow(flow))
        }
    }
    @Test fun constantToFlowStartsFastAndReturnsConstant() {
        val flow = CurveModeSelection(false, false).selectFlow(true)
        assertTrue(flow.flow); assertTrue(flow.pressure)
        assertEquals(CurveModeSelection(false, false), flow.selectFlow(false))
    }
    @Test fun followToFastThenSlowReturnsConstantNotCachedFollow() {
        val flow = CurveModeSelection(false, true).selectFlow(true)
        assertTrue(flow.pressure); assertTrue(flow.pressureBeforeFlow)
        val restoredDraft = flow.copy(pressure = false).copy()
        val pressure = restoredDraft.selectFlow(false)
        assertFalse(pressure.flow); assertFalse(pressure.pressure)
        assertTrue(pressure.selectFlow(true).pressure)
    }
    @Test fun followToFastThenBackWithoutChangingLogicKeepsFollow() {
        val pressure = CurveModeSelection(false, true).selectFlow(true).selectFlow(false)
        assertFalse(pressure.flow); assertTrue(pressure.pressure)
    }
}
