package io.openhoyi.session

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import org.junit.Assert.*
import org.junit.Test

class ShotRecoveryGateTest {
    private val address = "AA:BB:CC:DD:EE:01"
    private val raw = ByteFrame(byteArrayOf())
    private val idle = IdleTelemetry(9200, 12000, 10, 10, 0, 0, 0, 0, raw)
    private var writes = 0
    private val recovery = ShotRecoveryState(object : ShotRecoveryState.Storage {
        override fun read() = ShotRecoveryState.Record(true, address)
        override fun write(record: ShotRecoveryState.Record): Boolean { writes++; return false }
    })
    private fun block(machine: String? = address, coffee: DeviceState = DeviceState.READY,
        frame: HoyiMessage? = idle, at: Long? = 1000, now: Long = 1000,
        shot: ExtractionState = ExtractionState.IDLE, manual: Boolean = false) =
        ShotRecoveryGate.clearBlock(recovery, machine, coffee, frame, at, now, shot, manual)

    @Test fun everyUnsettledStateAndManualShotBlocksEvenFreshIdleFromSameMachine() {
        for (state in ExtractionState.entries) {
            val unsettled = state in setOf(ExtractionState.STARTING, ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED, ExtractionState.OUTCOME_UNKNOWN)
            assertEquals(if (unsettled) ShotRecoveryGate.Block.EXTRACTION_UNSETTLED else null, block(shot = state))
        }
        assertEquals(ShotRecoveryGate.Block.EXTRACTION_UNSETTLED,
            block(machine = null, coffee = DeviceState.DISCONNECTED, manual = true))
        assertTrue(recovery.pending)
        assertEquals(0, writes)
    }

    @Test fun originalDeviceAndReadyFreshIdleAreIndependentAcknowledgementRequirements() {
        assertNull(block(machine = address.lowercase(), now = 2500))
        assertEquals(ShotRecoveryGate.Block.DEVICE_MISMATCH, block(machine = null))
        assertEquals(ShotRecoveryGate.Block.DEVICE_MISMATCH, block(machine = "AA:BB:CC:DD:EE:02"))
        assertEquals(ShotRecoveryGate.Block.DEVICE_MISMATCH, block(machine = null, coffee = DeviceState.DISCONNECTED))
        for (state in DeviceState.entries) {
            assertEquals(if (state == DeviceState.READY) null else ShotRecoveryGate.Block.IDLE_NOT_FRESH,
                block(coffee = state))
        }
        assertEquals(ShotRecoveryGate.Block.IDLE_NOT_FRESH, block(at = null))
        assertEquals(ShotRecoveryGate.Block.IDLE_NOT_FRESH, block(at = 1001))
        assertEquals(ShotRecoveryGate.Block.IDLE_NOT_FRESH, block(now = 2501))
        assertEquals(ShotRecoveryGate.Block.IDLE_NOT_FRESH, block(frame = null))
        assertEquals(ShotRecoveryGate.Block.IDLE_NOT_FRESH,
            block(frame = ExtractionTelemetry(7, 5, 90, 20, 9200, 10, 64, 0, raw)))
        assertTrue(recovery.pending)
        assertEquals(0, writes)
    }

    @Test fun legacyIdentityStillRequiresExplicitIdleAcknowledgementAndCannotAutoClear() {
        var legacyWrites = 0
        val legacy = ShotRecoveryState(object : ShotRecoveryState.Storage {
            override fun read() = ShotRecoveryState.Record(true, null)
            override fun write(record: ShotRecoveryState.Record): Boolean { legacyWrites++; return false }
        })
        assertNull(ShotRecoveryGate.clearBlock(legacy, address, DeviceState.READY, idle, 1000, 1000,
            ExtractionState.IDLE, false))
        assertFalse(legacy.mayClearAfterPassiveShot(true, address))
        assertTrue(legacy.pending)
        assertEquals(0, legacyWrites)
        assertEquals(ShotRecoveryGate.Block.IDLE_NOT_FRESH,
            ShotRecoveryGate.clearBlock(legacy, address, DeviceState.READY, idle, 1000, 2501,
                ExtractionState.IDLE, false))
    }
}
