package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker

/** Only the confirmed-sleep action may retry its connection. Ordinary disconnected retry is unchanged. */
internal object SleepReconnectPolicy {
    fun allowed(running: Boolean, connection: DeviceState, sleep: SleepNowTracker.State?,
        freshSleepRaw: Int?, lastSleepRaw: Int?, extractionActive: Boolean,
        setting: SettingsWriteTracker.State?, preparing: Boolean, otherWritePending: Boolean): Boolean =
        running && connection == DeviceState.READY && !extractionActive && !preparing && !otherWritePending &&
            setting !in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
                SettingsWriteTracker.State.UNKNOWN) &&
            sleep !in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP,
                SleepNowTracker.State.UNKNOWN) &&
            (freshSleepRaw == 1 || (freshSleepRaw == null && sleep == SleepNowTracker.State.CONFIRMED && lastSleepRaw == 1))
}
