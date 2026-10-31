package io.openhoyi.mobile

import android.os.SystemClock
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.CupResetTracker
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker
import io.openhoyi.session.SleepScheduleWriteTracker

/** The same readback and safety gate is used by machine settings and home boiler switches. */
internal object MachineSettingsEditGate {
    fun allowed(owner: MobileService?, snapshot: MobileSnapshot = owner?.snapshot ?: MobileSnapshot(),
        now: Long = SystemClock.elapsedRealtime()): Boolean {
        val idle = snapshot.coffee as? IdleTelemetry
        val freshIdle = idle?.sleepStateRaw == 0 &&
            snapshot.coffeeAt?.let { it <= now && now - it <= 1500 } == true
        val pending = owner?.settingWriteState ?: SettingsWriteTracker.State.IDLE
        return snapshot.coffeeState == DeviceState.READY && owner?.machineSettingsFresh == true && freshIdle &&
            owner.manualShotActive != true && owner.machineControlSafetyMessage == null &&
            !ShotGate.active(owner.shotState) && owner.brewPreparationState == BrewPreparation.State.IDLE &&
            pending !in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
                SettingsWriteTracker.State.UNKNOWN) &&
            owner.scheduleWriteState !in setOf(SleepScheduleWriteTracker.State.WRITING, SleepScheduleWriteTracker.State.WAITING_READBACK) &&
            owner.cupResetState !in setOf(CupResetTracker.State.WRITING, CupResetTracker.State.WAITING_ZERO) &&
            owner.sleepNowState !in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP, SleepNowTracker.State.UNKNOWN)
    }
}
