package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.SettingsWriteTracker.State as Setting
import io.openhoyi.session.SleepNowTracker.State as Sleep
import org.junit.Assert.*
import org.junit.Test

class SleepReconnectPolicyTest {
    private fun allowed(connection: DeviceState = DeviceState.READY, sleep: Sleep = Sleep.CONFIRMED,
        fresh: Int? = 1, last: Int? = 1, active: Boolean = false, setting: Setting = Setting.IDLE,
        preparing: Boolean = false, pending: Boolean = false, running: Boolean = true) =
        SleepReconnectPolicy.allowed(running, connection, sleep, fresh, last, active, setting, preparing, pending)

    @Test fun confirmedSleepCanRetryButAwakeOrUnprovenSleepCannot() {
        assertTrue(allowed())
        assertTrue(allowed(fresh = null)) // Same session's confirmed sleep can recover stale telemetry.
        assertTrue(allowed(sleep = Sleep.IDLE)) // Physical sleep confirmed directly by current telemetry.
        assertFalse(allowed(fresh = 0))
        assertFalse(allowed(fresh = 2))
        assertFalse(allowed(fresh = null, last = null))
        assertFalse(allowed(fresh = null, last = 0))
        assertFalse(allowed(sleep = Sleep.IDLE, fresh = null))
    }
    @Test fun guardRejectsBusyOperationsAndNonReadyConnections() {
        assertFalse(allowed(active = true))
        assertFalse(allowed(preparing = true))
        assertFalse(allowed(pending = true))
        assertFalse(allowed(running = false))
        for (state in listOf(Setting.WRITING, Setting.WAITING_READBACK, Setting.UNKNOWN))
            assertFalse(state.toString(), allowed(setting = state))
        for (state in listOf(Sleep.WRITING, Sleep.WAITING_ASLEEP, Sleep.UNKNOWN))
            assertFalse(state.toString(), allowed(sleep = state))
        for (state in DeviceState.entries.filter { it != DeviceState.READY })
            assertFalse(state.toString(), allowed(connection = state))
    }
}
