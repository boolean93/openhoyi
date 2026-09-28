package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ShotRecoveryStateTest {
    private class Memory(var value: Boolean = false, var writable: Boolean = true) : ShotRecoveryState.Storage {
        override fun read(): Boolean = value
        override fun write(pending: Boolean): Boolean {
            if (!writable) return false
            value = pending
            return true
        }
    }

    @Test fun restartRetainsWarningUntilDurableClear() {
        val disk = Memory()
        val first = ShotRecoveryState(disk)
        assertTrue(first.arm())
        assertTrue(disk.value)
        val restarted = ShotRecoveryState(disk)
        assertTrue(restarted.pending)
        disk.writable = false
        assertFalse(restarted.clear())
        assertTrue(restarted.pending)
        disk.writable = true
        assertTrue(restarted.clear())
        assertFalse(ShotRecoveryState(disk).pending)
    }

    @Test fun failedDurableArmCannotPermitMachineStart() {
        val disk = Memory(writable = false)
        val state = ShotRecoveryState(disk)
        assertFalse(state.arm())
        assertFalse(state.pending)
        assertFalse(disk.value)
    }

    @Test fun unreadableStateFailsClosed() {
        val state = ShotRecoveryState(object : ShotRecoveryState.Storage {
            override fun read(): Boolean = error("storage unreadable")
            override fun write(pending: Boolean): Boolean = false
        })
        assertTrue(state.pending)
        assertFalse(state.clear())
        assertTrue(state.pending)
    }
}
