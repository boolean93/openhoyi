package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RecoveryClockEvidenceTest(private val kind: MachineWriteRecoveryState.Kind) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = MachineWriteRecoveryState.Kind.entries.filter {
            it != MachineWriteRecoveryState.Kind.UNKNOWN
        }.map { arrayOf(it) }
    }
    private val address = "AA:BB:CC:DD:EE:01"
    private val disk = object : MachineWriteRecoveryState.Storage {
        var record = MachineWriteRecoveryState.Record(kind, address)
        override fun read() = record
        override fun write(record: MachineWriteRecoveryState.Record): Boolean {
            this.record = record
            return true
        }
    }
    private val state = MachineWriteRecoveryState(disk)
    private fun allowed(at: Long?, now: Long) = when (kind) {
        MachineWriteRecoveryState.Kind.CUP_RESET -> state.canClearCupReset(
            MachineWriteRecoveryState.CupResetEvidence(address, 0, 2, 0, 2, at, now, false), 1, 1)
        MachineWriteRecoveryState.Kind.SETTING -> state.canClearSetting(
            MachineWriteRecoveryState.SettingEvidence(address, true, 2, true, at, now, false), 1)
        MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> state.canClearSchedule(
            MachineWriteRecoveryState.ScheduleEvidence(address, true, 2, 2, true, at, now, false), 1, 1)
        MachineWriteRecoveryState.Kind.SLEEP_NOW -> state.canClearSleep(
            MachineWriteRecoveryState.SleepEvidence(address, 1, 2, at, now, false), 1)
        MachineWriteRecoveryState.Kind.BREW_WAIT -> state.canClearBrewWait(
            MachineWriteRecoveryState.BrewWaitEvidence(address, true, 2, at, now, false), 1)
        else -> error("unsupported fixture")
    }
    @Test fun negativeClockEvidenceCannotAllowAcknowledgement() {
        assertFalse(allowed(-1, 100))
        assertFalse(allowed(-1, -1))
        assertTrue(state.pending)
        assertEquals(kind, disk.record.kind)
    }
    @Test fun subtractionOverflowCannotMakeAncientEvidenceFresh() {
        assertFalse(allowed(Long.MIN_VALUE, Long.MAX_VALUE))
        assertTrue(state.pending)
        assertEquals(kind, disk.record.kind)
    }
    @Test fun validClockRetainsExactFreshnessBoundary() {
        assertTrue(allowed(0, 0))
        assertTrue(allowed(0, 1500))
        assertFalse(allowed(0, 1501))
        assertFalse(allowed(101, 100))
        assertFalse(allowed(null, 100))
        assertTrue(allowed(Long.MAX_VALUE - 1500, Long.MAX_VALUE))
        assertFalse(allowed(0, Long.MAX_VALUE))
        // Eligibility is read-only; a separate human action and persisted clear are still required.
        assertTrue(state.pending)
        assertEquals(kind, disk.record.kind)
    }
}
