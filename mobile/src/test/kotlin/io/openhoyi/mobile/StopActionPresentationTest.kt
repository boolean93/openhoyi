package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class StopActionPresentationTest {
    @Test fun activeShotKeepsStopVisibleAndEnabled() {
        val state = StopActionPresentation.describe(ExtractionState.RUNNING, DeviceState.READY, true)
        assertTrue(state.visible)
        assertTrue(state.enabled)
    }

    @Test fun stopInFlightRemainsVisibleWithoutDuplicateRequest() {
        val state = StopActionPresentation.describe(ExtractionState.STOP_REQUESTED, DeviceState.READY, true)
        assertTrue(state.visible)
        assertFalse(state.enabled)
        assertEquals("停止请求处理中", state.label)
    }

    @Test fun unknownDisconnectedOutcomeShowsReasonAndReconnectCanRestoreStop() {
        val disconnected = StopActionPresentation.describe(ExtractionState.OUTCOME_UNKNOWN,
            DeviceState.DISCONNECTED, true)
        assertTrue(disconnected.visible)
        assertFalse(disconnected.enabled)
        assertEquals("连接中断 · 请检查机器", disconnected.label)
        val reconnected = StopActionPresentation.describe(ExtractionState.OUTCOME_UNKNOWN,
            DeviceState.READY, true)
        assertTrue(reconnected.visible)
        assertTrue(reconnected.enabled)
    }

    @Test fun noAppShotHidesStopAndStoppedServiceCannotSend() {
        assertFalse(StopActionPresentation.describe(ExtractionState.IDLE, DeviceState.READY, true).visible)
        val notRunning = StopActionPresentation.describe(ExtractionState.RUNNING, DeviceState.READY, false)
        assertTrue(notRunning.visible)
        assertFalse(notRunning.enabled)
    }
}
