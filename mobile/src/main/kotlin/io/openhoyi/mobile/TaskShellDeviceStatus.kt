package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker

/** Conservative display projection only; command gates remain with the existing owners. */
internal object TaskShellDeviceStatus {
    fun coffee(active: Boolean, sleep: SleepNowTracker.State?, setting: SettingsWriteTracker.State?,
        unavailable: Boolean, running: Boolean, connection: DeviceState, sleepRaw: Int?): Int = when {
        active -> R.string.task_shell_machine_extraction
        sleep == SleepNowTracker.State.UNKNOWN || setting == SettingsWriteTracker.State.UNKNOWN ->
            R.string.task_shell_machine_unknown
        sleep == SleepNowTracker.State.WRITING || sleep == SleepNowTracker.State.WAITING_ASLEEP ->
            R.string.task_shell_machine_sleeping
        setting == SettingsWriteTracker.State.WRITING || setting == SettingsWriteTracker.State.WAITING_READBACK ->
            R.string.task_shell_machine_setting
        unavailable -> R.string.task_shell_machine_unavailable
        !running -> R.string.device_state_disconnected
        connection != DeviceState.READY -> DeviceStatusText.resource(connection)
        sleepRaw == 1 -> R.string.task_shell_machine_asleep
        sleepRaw == 0 -> R.string.task_shell_machine_awake
        else -> R.string.task_shell_machine_unconfirmed
    }

    fun connecting(state: DeviceState): Boolean = state in setOf(DeviceState.CONNECTING,
        DeviceState.DISCOVERING, DeviceState.SUBSCRIBING, DeviceState.INITIALIZING,
        DeviceState.SYNCHRONIZING)

    fun canReconnect(state: DeviceState, active: Boolean): Boolean = !active && !connecting(state)

    fun scale(state: DeviceState, suppressed: Boolean): Int = when {
        state == DeviceState.READY -> R.string.task_shell_scale_connected
        state == DeviceState.UNSUPPORTED -> R.string.task_shell_scale_unsupported
        state == DeviceState.DISCONNECTED && suppressed -> R.string.task_shell_scale_manually_disconnected
        connecting(state) -> R.string.device_state_connecting
        else -> DeviceStatusText.resource(state)
    }
}
