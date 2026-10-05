package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class OrphanMachineRecoveryRecordTest(private val storedAddress: String?) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "address={0}")
        fun addresses(): List<Array<Any?>> = listOf(
            arrayOf(null), arrayOf("aa:bb:cc:dd:ee:01"), arrayOf("invalid"), arrayOf(""))
    }

    @Test fun onlyCompletelyEmptyRecordMayLoadWithoutRecoveryGate() {
        val original = MachineWriteRecoveryState.Record(null, storedAddress)
        var writes = 0
        val storage = object : MachineWriteRecoveryState.Storage {
            override fun read() = original
            override fun write(record: MachineWriteRecoveryState.Record): Boolean {
                writes++
                return false
            }
        }
        val state = MachineWriteRecoveryState(storage)
        val restarted = MachineWriteRecoveryState(storage)
        if (storedAddress == null) {
            assertFalse(state.pending)
            assertNull(state.kind)
            assertNull(state.address)
            assertTrue(state.clear())
        } else {
            assertTrue("Residual address must retain unresolved-operation protection", state.pending)
            assertEquals(MachineWriteRecoveryState.Kind.UNKNOWN, state.kind)
            assertEquals(if (storedAddress.startsWith("aa:")) "AA:BB:CC:DD:EE:01" else null, state.address)
            assertFalse(state.arm(MachineWriteRecoveryState.Kind.SETTING, "AA:BB:CC:DD:EE:01"))
            val address = "AA:BB:CC:DD:EE:01"
            assertFalse(state.canClearCupReset(MachineWriteRecoveryState.CupResetEvidence(
                address, 0, 1, 0, 1, 100, 100, false), 0, 0))
            assertFalse(state.canClearSetting(MachineWriteRecoveryState.SettingEvidence(
                address, true, 1, true, 100, 100, false), 0))
            assertFalse(state.canClearSchedule(MachineWriteRecoveryState.ScheduleEvidence(
                address, true, 1, 1, true, 100, 100, false), 0, 0))
            assertFalse(state.canClearSleep(MachineWriteRecoveryState.SleepEvidence(
                address, 1, 1, 100, 100, false), 0))
            assertFalse(state.canClearBrewWait(MachineWriteRecoveryState.BrewWaitEvidence(
                address, true, 1, 100, 100, false), 0))
            assertTrue(restarted.pending)
            assertEquals(state.kind, restarted.kind)
            assertEquals(state.address, restarted.address)
        }
        assertEquals("Loading must not silently repair/delete persisted evidence", 0, writes)
        assertEquals(original, storage.read())
    }
}
