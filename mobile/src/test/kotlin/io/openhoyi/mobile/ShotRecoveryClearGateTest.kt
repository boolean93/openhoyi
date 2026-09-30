package io.openhoyi.mobile

import io.openhoyi.session.ShotRecoveryState

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class ShotRecoveryClearGateTest {
    private val disk = object : ShotRecoveryState.Storage {
        var value = ShotRecoveryState.Record(false, null)
        override fun read() = value
        override fun write(record: ShotRecoveryState.Record): Boolean { value = record; return true }
    }
    private val idle = IdleTelemetry(9200, 12000, 10, 10, 0, 0, 0, 0, ByteFrame(byteArrayOf()))

    @Test fun freshIdleAloneCannotClearAnActiveOrWrongDeviceShot() {
        val recovery = ShotRecoveryState(disk)
        assertTrue(recovery.arm("AA:BB:CC:DD:EE:01"))
        fun block(shot: ExtractionState = ExtractionState.IDLE, manual: Boolean = false,
            address: String? = "AA:BB:CC:DD:EE:01", at: Long? = 1000,
            now: Long = 1000, coffee: DeviceState = DeviceState.READY) =
            ShotRecoveryClearGate.block(recovery, address, coffee, idle, at, now, shot, manual)
        assertNull(block())
        for (state in listOf(ExtractionState.STARTING, ExtractionState.RUNNING,
            ExtractionState.STOP_REQUESTED, ExtractionState.OUTCOME_UNKNOWN))
            assertNotNull(block(shot = state))
        assertNotNull(block(manual = true))
        assertNotNull(block(address = "AA:BB:CC:DD:EE:02"))
        assertNotNull(block(at = 1000, now = 2501))
        assertNotNull(block(at = 1001, now = 1000))
        assertNotNull(block(coffee = DeviceState.DISCONNECTED))
    }
}
