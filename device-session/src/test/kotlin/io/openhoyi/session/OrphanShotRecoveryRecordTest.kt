package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class OrphanShotRecoveryRecordTest(private val storedAddress: String?) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "address={0}")
        fun addresses(): List<Array<Any?>> = listOf(
            arrayOf(null), arrayOf("aa:bb:cc:dd:ee:01"), arrayOf("invalid"), arrayOf(""))
    }

    @Test fun residualAddressMustRetainProtectionUntilExplicitDurableClear() {
        val original = ShotRecoveryState.Record(false, storedAddress)
        var persisted = original
        var writes = 0
        var writable = false
        val disk = object : ShotRecoveryState.Storage {
            override fun read() = persisted
            override fun write(record: ShotRecoveryState.Record): Boolean {
                writes++
                if (!writable) return false
                persisted = record
                return true
            }
        }
        val state = ShotRecoveryState(disk)
        val restarted = ShotRecoveryState(disk)
        if (storedAddress == null) {
            assertFalse(state.pending)
            assertNull(state.address)
            assertFalse(restarted.pending)
            assertTrue(state.clear())
            assertEquals(0, writes)
            assertEquals(original, persisted)
            return
        }
        assertTrue("Residual identity must not discard unresolved-shot protection", state.pending)
        val identity = if (storedAddress.startsWith("aa:")) "AA:BB:CC:DD:EE:01" else null
        assertEquals(identity, state.address)
        assertTrue(restarted.pending)
        assertEquals(identity, restarted.address)
        assertEquals(0, writes)
        assertEquals(original, persisted)

        val other = "AA:BB:CC:DD:EE:02"
        assertFalse(state.arm(other))
        assertEquals(0, writes)
        assertEquals(if (identity == null) ShotRecoveryGate.Block.IDLE_NOT_FRESH else
            ShotRecoveryGate.Block.DEVICE_MISMATCH,
            ShotRecoveryGate.clearBlock(state, other, DeviceState.READY, null, 100, 100,
                ExtractionState.IDLE, false))
        assertEquals(ShotRecoveryGate.Block.EXTRACTION_UNSETTLED,
            ShotRecoveryGate.clearBlock(state, identity, DeviceState.DISCONNECTED, null, null, 100,
                ExtractionState.OUTCOME_UNKNOWN, false))
        if (identity == null) assertFalse(state.mayClearAfterPassiveShot(true, other))
        else {
            assertFalse(state.mayClearAfterPassiveShot(true, other))
            assertTrue(state.arm(identity.lowercase())) // Existing idempotence, never a new intent.
            assertEquals(0, writes)
        }

        assertFalse(state.clear())
        assertTrue(state.pending)
        assertEquals(original, persisted)
        assertTrue(ShotRecoveryState(disk).pending)
        assertEquals(1, writes)
        writable = true
        assertTrue(state.clear())
        assertFalse(state.pending)
        assertEquals(ShotRecoveryState.Record(false, null), persisted)
        assertFalse(ShotRecoveryState(disk).pending)
        assertEquals(2, writes)
        assertTrue(state.clear())
        assertEquals(2, writes)
    }
}
