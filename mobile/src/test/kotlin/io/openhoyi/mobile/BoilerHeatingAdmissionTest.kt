package io.openhoyi.mobile

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.session.MachineWriteRegistration
import io.openhoyi.session.OperationResult
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepScheduleWriteTracker
import io.openhoyi.session.SleepScheduleWriteTracker.State
import org.junit.Assert.*
import org.junit.Test

class BoilerHeatingAdmissionTest {
    @Test fun scheduleWritingAndReadbackAreDistinctNonActionableReasons() {
        val p = HomeBoilerSwitchPresentation
        val cases = mapOf(State.WRITING to R.string.home_boiler_schedule_writing,
            State.WAITING_READBACK to R.string.home_boiler_schedule_readback)
        for ((phase, label) in cases) {
            val admission = BoilerHeatingAdmission.resolve(false, phase)
            assertFalse(admission.canSubmit)
            assertEquals(label, admission.reason)
            assertTrue(p.showInline(label))
            for (brew in listOf(true, false)) {
                assertEquals(label, p.label(brew, true, null, null, admissionReason = admission.reason))
                assertEquals(label, p.label(brew, false, null, null, admissionReason = admission.reason))
                assertFalse(p.pendingAppearance(brew, null, null, ownsPending = false))
            }
        }
    }

    @Test fun projectionCannotChangeOriginalAdmissionOrCarryIntentPastDisconnect() {
        for (phase in State.entries.toList() + null) for (legacy in listOf(false, true))
            assertEquals(legacy, BoilerHeatingAdmission.resolve(legacy, phase).canSubmit)
        val disconnected = BoilerHeatingAdmission.resolve(false, null)
        assertFalse(disconnected.canSubmit)
        assertNull(disconnected.reason)
        assertTrue(BoilerHeatingAdmission.resolve(true, State.CONFIRMED).canSubmit)
    }

    @Test fun actualScheduleKeepsItsDurableSlotWhenWaitingOrDisconnected() {
        val disk = object : MachineWriteRecoveryState.Storage {
            var record = MachineWriteRecoveryState.Record(null, null)
            var writes = 0
            override fun read() = record
            override fun write(record: MachineWriteRecoveryState.Record): Boolean {
                this.record = record
                writes++
                return true
            }
        }
        val address = "AA:BB:CC:DD:EE:01"
        val ledger = MachineWriteRecoveryState(disk)
        val schedule = SleepScheduleWriteTracker()
        val week = WeeklySleepSchedule(List(7) { WeeklySleepDay(false, SleepDay(22, 0, 7, 0)) })
        val registration = MachineWriteRegistration.begin(ledger, address,
            MachineWriteRegistration.Request.Schedule(schedule, week, 1, 2, null, null))
            as MachineWriteRegistration.Result.Registered
        val owner = ledger.captureOwnership()
        assertEquals(BoilerHeatingAdmission.SCHEDULE_WRITING, BoilerHeatingAdmission.resolve(false, schedule.state))
        assertTrue(schedule.written(registration.token, OperationResult.Success(), 3, 4, null, null))
        assertEquals(BoilerHeatingAdmission.SCHEDULE_READBACK, BoilerHeatingAdmission.resolve(false, schedule.state))
        assertFalse(schedule.observe(3, 4, null, null))
        assertFalse(schedule.permitsWrite(registration.token))
        schedule.disconnected(3, 4)
        assertEquals(State.UNKNOWN, schedule.state)
        assertFalse(schedule.permitsWrite(registration.token))
        assertEquals(BoilerHeatingAdmission.UNAVAILABLE, BoilerHeatingAdmission.resolve(false, schedule.state))
        assertTrue(ledger.owns(owner))
        assertEquals(MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE, MachineWriteRecoveryState(disk).kind)
        assertEquals(1, disk.writes)
    }

    @Test fun existingMatchingBoilerRecoveryAndActualPreheatKeepPriority() {
        val reason = BoilerHeatingAdmission.resolve(false, State.WAITING_READBACK).reason
        assertEquals(R.string.home_boiler_refresh, HomeBoilerSwitchPresentation.label(true, false,
            MachineSettingChange.BrewHeating(true), SettingsWriteTracker.State.UNKNOWN, admissionReason = reason))
        assertEquals(R.string.home_boiler_preheat_occupied, HomeBoilerSwitchPresentation.label(false, true,
            null, null, preheatOccupied = true, admissionReason = reason))
    }
}
