package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MachineWriteRegistrationTest(private val kind: MachineWriteRecoveryState.Kind) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun kinds() = MachineWriteRecoveryState.Kind.entries.filter { it != MachineWriteRecoveryState.Kind.UNKNOWN }.map { arrayOf(it) }
    }
    private val address = "AA:BB:CC:DD:EE:01"
    private inner class Fixture {
        val setting = SettingsWriteTracker()
        val cup = CupResetTracker()
        val schedule = SleepScheduleWriteTracker()
        val sleep = SleepNowTracker()
        val brew = BrewPreparation()
        val request: MachineWriteRegistration.Request = when (kind) {
            MachineWriteRecoveryState.Kind.SETTING -> MachineWriteRegistration.Request.Setting(setting, MachineSettingChange.BrewTemperature(93), 7)
            MachineWriteRecoveryState.Kind.CUP_RESET -> MachineWriteRegistration.Request.CupReset(cup, 25, 7, 8)
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> MachineWriteRegistration.Request.Schedule(schedule,
                WeeklySleepSchedule(List(7) { WeeklySleepDay(false, SleepDay(22, 0, 7, 0)) }), 7, 8, null, null)
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> MachineWriteRegistration.Request.Sleep(sleep, 7)
            MachineWriteRecoveryState.Kind.BREW_WAIT -> MachineWriteRegistration.Request.BrewWait(brew, "fixture", 93)
            else -> error("unsupported fixture")
        }
        fun state(): String = when (kind) {
            MachineWriteRecoveryState.Kind.SETTING -> setting.state.name
            MachineWriteRecoveryState.Kind.CUP_RESET -> cup.state.name
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> schedule.state.name
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> sleep.state.name
            MachineWriteRecoveryState.Kind.BREW_WAIT -> brew.state.name
            else -> error("unsupported fixture")
        }
        fun assertRejectedState() = assertEquals(if (kind == MachineWriteRecoveryState.Kind.BREW_WAIT) "IDLE" else "FAILED", state())
        inner class Memory(var stored: MachineWriteRecoveryState.Record = MachineWriteRecoveryState.Record(null, null)) : MachineWriteRecoveryState.Storage {
            var writable = false
            var throwing = false
            var attempts = 0
            override fun read() = stored
            override fun write(record: MachineWriteRecoveryState.Record): Boolean {
                attempts++
                assertEquals("WRITING", state()) // Tracker established before the persistence barrier.
                if (throwing) error("storage failure")
                if (!writable) return false
                stored = record
                return true
            }
        }
    }
    @Test fun registrationPersistsIntentAndBusyNeverWritesAgain() {
        val f = Fixture(); val disk = f.Memory().apply { writable = true }; val recovery = MachineWriteRecoveryState(disk)
        val result = MachineWriteRegistration.begin(recovery, address, f.request)
        assertTrue(result is MachineWriteRegistration.Result.Registered)
        assertTrue((result as MachineWriteRegistration.Result.Registered).token > 0)
        assertEquals("WRITING", f.state())
        assertEquals(MachineWriteRecoveryState.Record(kind, address), disk.stored)
        assertEquals(kind, recovery.kind); assertEquals(address, recovery.address)
        assertEquals(MachineWriteRegistration.Result.Busy, MachineWriteRegistration.begin(recovery, address, f.request))
        assertEquals(1, disk.attempts); assertEquals("WRITING", f.state())
    }
    @Test fun failedOrThrowingStorageLeavesNoTokenUntilExplicitRetry() {
        for (throws in listOf(false, true)) {
            val f = Fixture(); val disk = f.Memory().apply { throwing = throws }; val recovery = MachineWriteRecoveryState(disk)
            assertEquals(MachineWriteRegistration.Result.RecordFailed, MachineWriteRegistration.begin(recovery, address, f.request))
            f.assertRejectedState(); assertFalse(recovery.pending)
            assertEquals(MachineWriteRecoveryState.Record(null, null), disk.stored); assertEquals(1, disk.attempts)
            disk.throwing = false; disk.writable = true
            assertTrue(MachineWriteRegistration.begin(recovery, address, f.request) is MachineWriteRegistration.Result.Registered)
            assertEquals(2, disk.attempts); assertEquals("WRITING", f.state()); assertEquals(kind, recovery.kind)
        }
    }
    @Test fun existingIntentIncludingUnknownIsNeverOverwritten() {
        for (existing in MachineWriteRecoveryState.Kind.entries) {
            val f = Fixture(); val before = MachineWriteRecoveryState.Record(existing, address)
            val disk = f.Memory(before).apply { writable = true }; val recovery = MachineWriteRecoveryState(disk)
            assertEquals(MachineWriteRegistration.Result.RecordFailed, MachineWriteRegistration.begin(recovery, address, f.request))
            f.assertRejectedState(); assertTrue(recovery.pending); assertEquals(before, disk.stored); assertEquals(0, disk.attempts)
        }
    }
    @Test fun invalidIdentityNeverCrossesPersistenceBarrier() {
        for (identity in listOf(null, "invalid")) {
            val f = Fixture(); val disk = f.Memory().apply { writable = true }; val recovery = MachineWriteRecoveryState(disk)
            assertEquals(MachineWriteRegistration.Result.RecordFailed, MachineWriteRegistration.begin(recovery, identity, f.request))
            f.assertRejectedState(); assertFalse(recovery.pending); assertEquals(0, disk.attempts)
        }
    }
}
