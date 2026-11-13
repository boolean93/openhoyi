package io.openhoyi.mobile

import io.openhoyi.session.CoffeeSessionFailure
import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.DeviceState
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker

/** Conservative display projection only; command gates remain with the existing owners. */
internal object TaskShellDeviceStatus {
    fun coffee(active: Boolean, sleep: SleepNowTracker.State?, setting: SettingsWriteTracker.State?,
        unavailable: Boolean, running: Boolean, connection: DeviceState, sleepRaw: Int?,
        failure: CoffeeSessionFailure? = null,lastSleepRaw:Int?=sleepRaw,
        preparation:BrewPreparation.State=BrewPreparation.State.IDLE,preparationAtTarget:Boolean=false): Int = when {
        active -> R.string.task_shell_machine_extraction
        sleep in setOf(SleepNowTracker.State.UNKNOWN,SleepNowTracker.State.WRITING,SleepNowTracker.State.WAITING_ASLEEP) ->
            SleepPresentation.status(sleep,sleepRaw,lastSleepRaw)
        setting == SettingsWriteTracker.State.UNKNOWN -> R.string.task_shell_machine_unknown
        setting == SettingsWriteTracker.State.WRITING || setting == SettingsWriteTracker.State.WAITING_READBACK ->
            R.string.task_shell_machine_setting
        sleep == SleepNowTracker.State.CONFIRMED && sleepRaw==null && lastSleepRaw==1 -> R.string.sleep_status_last_confirmed
        unavailable || PreheatStatusPresentation.abnormal(preparation) -> R.string.task_shell_machine_unavailable
        !running -> R.string.device_state_disconnected
        connection != DeviceState.READY -> DeviceStatusText.coffeeResource(connection, failure)
        sleepRaw!=1 && preparation in setOf(BrewPreparation.State.WRITING,BrewPreparation.State.WAITING_TEMP) ->
            R.string.auto_preheat_heating
        sleepRaw!=1 && preparation==BrewPreparation.State.READY -> if(preparationAtTarget)
            R.string.task_shell_machine_preheat_ready else R.string.home_boiler_preheat_occupied
        else -> SleepPresentation.status(sleep,sleepRaw,lastSleepRaw)
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
