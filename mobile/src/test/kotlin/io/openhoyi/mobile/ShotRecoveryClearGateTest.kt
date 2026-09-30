package io.openhoyi.mobile

import io.openhoyi.session.ShotRecoveryState

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class ShotRecoveryClearGateTest {
    private val gate = ShotRecoveryClearGate(DefaultStringResources::resolve)
    private val disk = object : ShotRecoveryState.Storage {
        var value = ShotRecoveryState.Record(false, null)
        override fun read() = value
        override fun write(record: ShotRecoveryState.Record): Boolean { value = record; return true }
    }
    private val idle = IdleTelemetry(9200, 12000, 10, 10, 0, 0, 0, 0, ByteFrame(byteArrayOf()))

    @Test fun allStatesExactFreshnessAndRejectionPriorityRemainStable() {
        val recovery = ShotRecoveryState(disk)
        assertTrue(recovery.arm("AA:BB:CC:DD:EE:01"))
        fun block(coffee: DeviceState = DeviceState.READY, at: Long? = 1000, now: Long = 2500,
            address: String? = "aa:bb:cc:dd:ee:01", shot: ExtractionState = ExtractionState.IDLE,
            manual: Boolean = false) =
            gate.block(recovery, address, coffee, idle, at, now, shot, manual)
        assertNull(block())
        assertEquals("请连接原咖啡机，等待新的待机回报后再确认", block(now = 2501))
        assertEquals("请连接原咖啡机，等待新的待机回报后再确认", block(at = null))
        for (state in DeviceState.entries) {
            assertEquals(if (state == DeviceState.READY) null else
                "请连接原咖啡机，等待新的待机回报后再确认", block(coffee = state))
        }
        for (state in ExtractionState.entries) {
            val unsettled = state in setOf(ExtractionState.STARTING, ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED, ExtractionState.OUTCOME_UNKNOWN)
            assertEquals(if (unsettled) "萃取尚未确认结束，请先用机器拨杆停液" else null, block(shot = state))
        }
        assertEquals("萃取尚未确认结束，请先用机器拨杆停液",
            block(coffee = DeviceState.DISCONNECTED, address = null, manual = true))
        assertEquals("请先连接上一杯使用的咖啡机", block(coffee = DeviceState.DISCONNECTED, address = null))
    }

    @Test fun freshIdleAloneCannotClearAnActiveOrWrongDeviceShot() {
        val recovery = ShotRecoveryState(disk)
        assertTrue(recovery.arm("AA:BB:CC:DD:EE:01"))
        fun block(shot: ExtractionState = ExtractionState.IDLE, manual: Boolean = false,
            address: String? = "AA:BB:CC:DD:EE:01", at: Long? = 1000,
            now: Long = 1000, coffee: DeviceState = DeviceState.READY) =
            gate.block(recovery, address, coffee, idle, at, now, shot, manual)
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
