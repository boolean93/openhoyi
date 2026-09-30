package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class ShotRecoveryStateTest {
    private class Memory(var value: ShotRecoveryState.Record = ShotRecoveryState.Record(false, null),
                         var writable: Boolean = true) : ShotRecoveryState.Storage {
        override fun read(): ShotRecoveryState.Record = value
        override fun write(record: ShotRecoveryState.Record): Boolean {
            if (!writable) return false
            value = record
            return true
        }
    }

    @Test fun restartRetainsWarningUntilDurableClear() {
        val disk = Memory()
        val first = ShotRecoveryState(disk)
        assertTrue(first.arm("AA:BB:CC:DD:EE:01"))
        assertEquals(ShotRecoveryState.Record(true,"AA:BB:CC:DD:EE:01"),disk.value)
        val restarted = ShotRecoveryState(disk)
        assertTrue(restarted.pending)
        assertEquals("AA:BB:CC:DD:EE:01",restarted.address)
        assertFalse(restarted.arm("AA:BB:CC:DD:EE:02"))
        assertFalse(restarted.matchesDevice("AA:BB:CC:DD:EE:02"))
        assertFalse(restarted.matchesDevice(null))
        assertTrue(restarted.matchesDevice("aa:bb:cc:dd:ee:01"))
        assertTrue(restarted.mayClearAfterPassiveShot(true,"AA:BB:CC:DD:EE:01"))
        assertFalse(restarted.mayClearAfterPassiveShot(true,"AA:BB:CC:DD:EE:02"))
        disk.writable = false
        assertFalse(restarted.clear())
        assertTrue(restarted.pending)
        disk.writable = true
        assertTrue(restarted.clear())
        assertFalse(ShotRecoveryState(disk).pending)
        assertNull(disk.value.address)
    }

    @Test fun failedDurableArmCannotPermitMachineStart() {
        val disk = Memory(writable = false)
        val state = ShotRecoveryState(disk)
        assertFalse(state.arm("AA:BB:CC:DD:EE:01"))
        assertFalse(state.pending)
        assertFalse(disk.value.pending)
    }

    @Test fun unreadableStateFailsClosed() {
        val state = ShotRecoveryState(object : ShotRecoveryState.Storage {
            override fun read(): ShotRecoveryState.Record = error("storage unreadable")
            override fun write(record: ShotRecoveryState.Record): Boolean = false
        })
        assertTrue(state.pending)
        assertFalse(state.clear())
        assertTrue(state.pending)
    }

    @Test fun legacyPendingMarkerKeepsWarningWithoutInventingDeviceIdentity() {
        val state=ShotRecoveryState(Memory(ShotRecoveryState.Record(true,null)))
        assertTrue(state.pending)
        assertNull(state.address)
        assertTrue(state.matchesDevice("AA:BB:CC:DD:EE:01"))
        assertFalse(state.mayClearAfterPassiveShot(true,"AA:BB:CC:DD:EE:01"))
    }

    @Test fun passiveShotWithoutKnownAddressStillPersistsWarning() {
        val disk=Memory()
        assertTrue(ShotRecoveryState(disk).arm(null))
        assertEquals(ShotRecoveryState.Record(true,null),disk.value)
        assertTrue(ShotRecoveryState(disk).pending)
        assertTrue(ShotRecoveryState(disk).mayClearAfterPassiveShot(false,null))
    }
}
