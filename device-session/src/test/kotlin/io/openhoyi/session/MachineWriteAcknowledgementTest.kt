package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MachineWriteAcknowledgementTest(private val kind: MachineWriteRecoveryState.Kind) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun kinds() = MachineWriteRecoveryState.Kind.entries.filter { it != MachineWriteRecoveryState.Kind.UNKNOWN }
            .map { arrayOf(it) }
    }
    private val address = "AA:BB:CC:DD:EE:01"
    private inner class Memory(recordKind: MachineWriteRecoveryState.Kind? = kind) : MachineWriteRecoveryState.Storage {
        var stored = MachineWriteRecoveryState.Record(recordKind, if (recordKind == null) null else address)
        var writable = false
        var attempts = 0
        override fun read() = stored
        override fun write(record: MachineWriteRecoveryState.Record): Boolean {
            attempts++
            if (!writable) return false
            stored = record
            return true
        }
    }
    private fun request(identity: String? = address, active: Boolean = false, after: Long = 1,
        at: Long? = 100): MachineWriteAcknowledgement.Request = when (kind) {
        MachineWriteRecoveryState.Kind.CUP_RESET -> MachineWriteAcknowledgement.Request.CupReset(
            MachineWriteRecoveryState.CupResetEvidence(identity, 0, 2, 0, 2, at, 100, active), after, after)
        MachineWriteRecoveryState.Kind.SETTING -> MachineWriteAcknowledgement.Request.Setting(
            MachineWriteRecoveryState.SettingEvidence(identity, true, 2, true, at, 100, active), after)
        MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> MachineWriteAcknowledgement.Request.Schedule(
            MachineWriteRecoveryState.ScheduleEvidence(identity, true, 2, 2, true, at, 100, active), after, after)
        MachineWriteRecoveryState.Kind.SLEEP_NOW -> MachineWriteAcknowledgement.Request.Sleep(
            MachineWriteRecoveryState.SleepEvidence(identity, 1, 2, at, 100, active), after)
        MachineWriteRecoveryState.Kind.BREW_WAIT -> MachineWriteAcknowledgement.Request.BrewWait(
            MachineWriteRecoveryState.BrewWaitEvidence(identity, true, 2, at, 100, active), after)
        else -> error("unsupported fixture")
    }

    @Test fun inadequateEvidenceNeverAttemptsPersistentClear() {
        val disk = Memory().apply { writable = true }
        val recovery = MachineWriteRecoveryState(disk)
        val before = disk.stored
        assertEquals(MachineWriteAcknowledgement.Result.WAITING,
            MachineWriteAcknowledgement.acknowledge(recovery, false, request()))
        for (bad in listOf(request(identity = "AA:BB:CC:DD:EE:02"), request(active = true), request(after = 2), request(at = null)))
            assertEquals(MachineWriteAcknowledgement.Result.WAITING,
                MachineWriteAcknowledgement.acknowledge(recovery, true, bad))
        assertTrue(recovery.pending)
        assertEquals(before, disk.stored)
        assertEquals(0, disk.attempts)
    }

    @Test fun wrongRecordKindUnknownAndEmptyCannotBeAcknowledged() {
        for (other in MachineWriteRecoveryState.Kind.entries.filter { it != kind } + listOf(null)) {
            val disk = Memory(other).apply { writable = true }
            val before = disk.stored
            val recovery = MachineWriteRecoveryState(disk)
            assertEquals(MachineWriteAcknowledgement.Result.WAITING,
                MachineWriteAcknowledgement.acknowledge(recovery, true, request()))
            assertEquals(before, disk.stored)
            assertEquals(0, disk.attempts)
        }
    }

    @Test fun failedClearRetainsProtectionUntilExplicitRetrySucceeds() {
        val disk = Memory()
        val recovery = MachineWriteRecoveryState(disk)
        val before = disk.stored
        assertEquals(MachineWriteAcknowledgement.Result.CLEAR_FAILED,
            MachineWriteAcknowledgement.acknowledge(recovery, true, request()))
        assertTrue(recovery.pending)
        assertEquals(before, disk.stored)
        assertEquals(1, disk.attempts)
        disk.writable = true
        assertEquals(MachineWriteAcknowledgement.Result.ACKNOWLEDGED,
            MachineWriteAcknowledgement.acknowledge(recovery, true, request()))
        assertFalse(recovery.pending)
        assertEquals(MachineWriteRecoveryState.Record(null, null), disk.stored)
        assertEquals(2, disk.attempts)
        assertEquals(MachineWriteAcknowledgement.Result.WAITING,
            MachineWriteAcknowledgement.acknowledge(recovery, true, request()))
        assertEquals(2, disk.attempts)
    }
}
