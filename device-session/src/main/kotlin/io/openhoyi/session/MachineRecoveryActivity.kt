package io.openhoyi.session

/** Transaction activity only; device identity and readback qualification stay separate. */
object MachineRecoveryActivity {
    fun isBusy(state: CupResetTracker.State): Boolean =
        state in setOf(CupResetTracker.State.WRITING, CupResetTracker.State.WAITING_ZERO)
    fun isBusy(state: SettingsWriteTracker.State): Boolean =
        state in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK)
    fun isBusy(state: SleepScheduleWriteTracker.State): Boolean =
        state in setOf(SleepScheduleWriteTracker.State.WRITING, SleepScheduleWriteTracker.State.WAITING_READBACK)
    fun isBusy(state: SleepNowTracker.State): Boolean =
        state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP)
    fun isBusy(state: BrewPreparation.State): Boolean =
        state !in setOf(BrewPreparation.State.IDLE, BrewPreparation.State.FAILED,
            BrewPreparation.State.UNKNOWN, BrewPreparation.State.CANCEL_WRITTEN)

    fun available(kind: MachineWriteRecoveryState.Kind?, cup: CupResetTracker.State,
        setting: SettingsWriteTracker.State, schedule: SleepScheduleWriteTracker.State,
        sleep: SleepNowTracker.State, brew: BrewPreparation.State,
        shotPending: Boolean, shotActive: Boolean): Boolean = when (kind) {
        MachineWriteRecoveryState.Kind.CUP_RESET -> !isBusy(cup)
        MachineWriteRecoveryState.Kind.SETTING -> !isBusy(setting)
        MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> !isBusy(schedule)
        MachineWriteRecoveryState.Kind.SLEEP_NOW -> !isBusy(sleep)
        MachineWriteRecoveryState.Kind.BREW_WAIT -> !shotPending && !shotActive && !isBusy(brew)
        else -> false
    }
}
