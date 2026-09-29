package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class MachineWriteRecoveryStateTest {
    private class Memory(var value: MachineWriteRecoveryState.Record =
        MachineWriteRecoveryState.Record(null, null), var writable: Boolean = true) : MachineWriteRecoveryState.Storage {
        override fun read() = value
        override fun write(record: MachineWriteRecoveryState.Record): Boolean {
            if (!writable) return false
            value = record
            return true
        }
    }

    @Test fun cupResetIntentSurvivesRestartAndIsBoundToOneCoffeeMachine() {
        val disk=Memory()
        val original=MachineWriteRecoveryState(disk)
        assertTrue(original.arm(MachineWriteRecoveryState.Kind.CUP_RESET,"aa:bb:cc:dd:ee:01"))
        val restarted=MachineWriteRecoveryState(disk)
        assertTrue(restarted.pending)
        assertEquals(MachineWriteRecoveryState.Kind.CUP_RESET,restarted.kind)
        assertEquals("AA:BB:CC:DD:EE:01",restarted.address)
        assertFalse(restarted.matchesDevice("AA:BB:CC:DD:EE:02"))
        assertFalse(restarted.matchesDevice(null))
        assertTrue(restarted.matchesDevice("aa:bb:cc:dd:ee:01"))
        assertFalse(restarted.arm(MachineWriteRecoveryState.Kind.CUP_RESET,"AA:BB:CC:DD:EE:02"))
        disk.writable=false
        assertFalse(restarted.clear())
        assertTrue(restarted.pending)
        disk.writable=true
        assertTrue(restarted.clear())
        assertFalse(MachineWriteRecoveryState(disk).pending)
    }

    @Test fun storageFailureOrInvalidRecordFailsClosed() {
        val disk=Memory(writable=false)
        assertFalse(MachineWriteRecoveryState(disk).arm(MachineWriteRecoveryState.Kind.CUP_RESET,
            "AA:BB:CC:DD:EE:01"))
        assertFalse(disk.value.pending)
        val unreadable=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read():MachineWriteRecoveryState.Record=error("unreadable")
            override fun write(record:MachineWriteRecoveryState.Record)=false
        })
        assertTrue(unreadable.pending)
        assertFalse(unreadable.clear())
        val invalid=MachineWriteRecoveryState(Memory(
            MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.CUP_RESET,"invalid")))
        assertTrue(invalid.pending)
        assertEquals(MachineWriteRecoveryState.Kind.UNKNOWN,invalid.kind)
        assertTrue(invalid.matchesDevice("AA:BB:CC:DD:EE:02"))
        assertFalse(invalid.arm(MachineWriteRecoveryState.Kind.CUP_RESET,"AA:BB:CC:DD:EE:02"))
    }

    @Test fun cupResetRequiresFreshMatchingReadbacksFromOriginalMachineBeforeClear() {
        val state=MachineWriteRecoveryState(Memory())
        assertTrue(state.arm(MachineWriteRecoveryState.Kind.CUP_RESET,"AA:BB:CC:DD:EE:01"))
        val good=MachineWriteRecoveryState.CupResetEvidence("AA:BB:CC:DD:EE:01",0,4,0,7,100,100,false)
        assertTrue(state.canClearCupReset(good,3,6))
        assertFalse(state.canClearCupReset(good.copy(address="AA:BB:CC:DD:EE:02"),3,6))
        assertFalse(state.canClearCupReset(good.copy(settingsSerial=3),3,6))
        assertFalse(state.canClearCupReset(good.copy(idleSerial=6),3,6))
        assertFalse(state.canClearCupReset(good.copy(settingsCount=1),3,6))
        assertFalse(state.canClearCupReset(good.copy(idleAtMs=0,nowMs=1_501),3,6))
        assertFalse(state.canClearCupReset(good.copy(writeActive=true),3,6))
    }
}
