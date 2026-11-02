package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TaskShellDeviceStatusTest {
    @Test fun unknownResultCannotBeHiddenByFreshAwakeOrAsleepTelemetry() {
        for (raw in listOf(0, 1)) {
            assertEquals(R.string.task_shell_machine_unknown, TaskShellDeviceStatus.coffee(
                false, SleepNowTracker.State.UNKNOWN, null, false, true, DeviceState.READY, raw))
            assertEquals(R.string.task_shell_machine_unknown, TaskShellDeviceStatus.coffee(
                false, null, SettingsWriteTracker.State.UNKNOWN, false, true, DeviceState.READY, raw))
        }
    }

    @Test fun everyConnectionPhaseBlocksRepeatedReconnect() {
        for (state in listOf(DeviceState.CONNECTING, DeviceState.DISCOVERING,
            DeviceState.SUBSCRIBING, DeviceState.INITIALIZING, DeviceState.SYNCHRONIZING)) {
            assertFalse(TaskShellDeviceStatus.canReconnect(state, false))
            assertEquals(R.string.device_state_connecting, TaskShellDeviceStatus.scale(state, true))
        }
        assertFalse(TaskShellDeviceStatus.canReconnect(DeviceState.DISCONNECTED, true))
    }

    @Test fun suppressionOnlyDescribesAnActuallyDisconnectedScale() {
        assertEquals(R.string.task_shell_scale_manually_disconnected,
            TaskShellDeviceStatus.scale(DeviceState.DISCONNECTED, true))
        assertEquals(R.string.device_state_disconnected,
            TaskShellDeviceStatus.scale(DeviceState.DISCONNECTED, false))
        for (state in DeviceState.entries.filter { it != DeviceState.DISCONNECTED }) {
            assertEquals(TaskShellDeviceStatus.scale(state, false), TaskShellDeviceStatus.scale(state, true))
        }
    }

    @Test fun connectionAndMissingSleepEvidenceCannotClaimAwake() {
        assertEquals(R.string.device_state_connecting, TaskShellDeviceStatus.coffee(
            false, null, null, false, true, DeviceState.CONNECTING, 0))
        assertEquals(R.string.task_shell_machine_unconfirmed, TaskShellDeviceStatus.coffee(
            false, null, null, false, true, DeviceState.READY, null))
    }
}
