package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class BrewWaitCancelGateTest {
    private val idle = IdleTelemetry(9200, 12000, 10, 10, 0, 0, 0, 0, ByteFrame(byteArrayOf()))
    private fun block(frame: io.openhoyi.protocol.HoyiMessage? = idle, at: Long? = 1000,
        now: Long = 1000, coffee: DeviceState = DeviceState.READY,
        shot: ExtractionState = ExtractionState.IDLE, unresolvedShot: Boolean = false) =
        BrewWaitCancelGate.block(coffee, frame, at, now, shot, unresolvedShot)

    @Test fun cancellationRequiresFreshAwakeMachineIdle() {
        assertNull(block())
        assertNotNull(block(coffee = DeviceState.DISCONNECTED))
        assertNotNull(block(frame = null))
        assertNotNull(block(frame = ExtractionTelemetry(1, 1, 1, 1, 9200, 1, 64, 0,
            ByteFrame(byteArrayOf()))))
        assertNotNull(block(at = 1000, now = 2501))
        assertNotNull(block(at = 1001, now = 1000))
        assertNotNull(block(frame = idle.copy(sleepStateRaw = 1)))
    }

    @Test fun exactFreshnessBoundaryAndAllConnectionStatesPreserveDecision() {
        assertNull(block(at = 1000, now = 2500))
        assertEquals("咖啡机待机数据已过期，请等待新回报", block(at = null))
        for (state in DeviceState.entries) {
            if (state == DeviceState.READY) assertNull(block(coffee = state))
            else assertEquals("咖啡机未就绪，无法发送取消预热", block(coffee = state))
        }
        for (state in ExtractionState.entries) {
            if (state in setOf(ExtractionState.STARTING, ExtractionState.RUNNING,
                    ExtractionState.STOP_REQUESTED, ExtractionState.OUTCOME_UNKNOWN))
                assertEquals("萃取尚未确认结束，请先检查机器并用拨杆停液", block(shot = state))
            else assertNull(block(shot = state))
        }
    }

    @Test fun unresolvedExtractionBlocksPreheatCancellationEvenWithFreshIdle() {
        assertNotNull(block(unresolvedShot = true))
        for (state in listOf(ExtractionState.STARTING, ExtractionState.RUNNING,
            ExtractionState.STOP_REQUESTED, ExtractionState.OUTCOME_UNKNOWN))
            assertNotNull(block(shot = state))
        assertNull(block(shot = ExtractionState.ENDED_OBSERVED))
    }
}
