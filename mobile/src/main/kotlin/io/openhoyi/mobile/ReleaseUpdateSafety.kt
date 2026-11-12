package io.openhoyi.mobile

import io.openhoyi.session.*

/** Display-only projection. It never grants a machine command or acknowledges a recovery record. */
internal fun releaseUpdateInterruptionBlocked(
    shot: ExtractionState, manualShot: Boolean, preparingScale: Boolean, preparingBrew: Boolean,
    tare: StandaloneTare.State, shotRecovery: Boolean, machineRecovery: Boolean,
    setting: SettingsWriteTracker.State, cupsBusy: Boolean, scheduleBusy: Boolean,
    sleep: SleepNowTracker.State, leverInFlight: Boolean, leverBlocked: Boolean,
): Boolean = ShotGate.active(shot) || manualShot || preparingScale || preparingBrew ||
    tare in setOf(StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO, StandaloneTare.State.UNKNOWN) ||
    shotRecovery || machineRecovery || cupsBusy || scheduleBusy || leverInFlight || leverBlocked ||
    setting in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK, SettingsWriteTracker.State.UNKNOWN) ||
    sleep in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP, SleepNowTracker.State.UNKNOWN)
