package io.openhoyi.session

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import org.junit.Assert.*
import org.junit.Test

class ExtractionStartGateTest {
    private val raw = ByteFrame(byteArrayOf())
    private val idle = IdleTelemetry(9200, 12000, 0, 0, 0, 0, 0, 0, raw)
    private fun block(target: Int? = 2700, validated: Boolean = true,
        coffee: DeviceState = DeviceState.READY, frame: HoyiMessage? = idle,
        coffeeAt: Long? = 1000, scale: DeviceState = DeviceState.READY,
        weightAt: Long? = 1000, now: Long = 2000, shot: ExtractionState = ExtractionState.IDLE) =
        ExtractionStartGate.block(target, validated, coffee, frame, coffeeAt, scale, weightAt, now, shot)

    @Test fun missingAndUnverifiedCurveTakePriorityOverUnsettledOrDisconnectedMachine() {
        assertEquals(ExtractionStartGate.Block.CURVE_MISSING,
            block(target = null, validated = false, coffee = DeviceState.DISCONNECTED,
                shot = ExtractionState.OUTCOME_UNKNOWN))
        assertEquals(ExtractionStartGate.Block.CURVE_UNVERIFIED,
            block(validated = false, coffee = DeviceState.DISCONNECTED,
                shot = ExtractionState.OUTCOME_UNKNOWN))
        for (state in ExtractionState.entries) {
            val unsettled = state in setOf(ExtractionState.STARTING, ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED, ExtractionState.OUTCOME_UNKNOWN)
            assertEquals(if (unsettled) ExtractionStartGate.Block.EXTRACTION_UNSETTLED else null,
                block(shot = state))
        }
    }

    @Test fun allConnectionStatesRequireCoffeeAndWeightModeRequiresScale() {
        for (state in DeviceState.entries) {
            assertEquals(if (state == DeviceState.READY) null else ExtractionStartGate.Block.COFFEE_NOT_READY,
                block(coffee = state))
            assertEquals(if (state == DeviceState.READY) null else ExtractionStartGate.Block.SCALE_NOT_READY,
                block(scale = state))
            assertNull(block(target = 0, scale = state, weightAt = null))
        }
    }

    @Test fun onlyCurrentIdleAndFreshWeightMeetExactTimeBoundary() {
        assertNull(block(now = 2500))
        assertEquals(ExtractionStartGate.Block.IDLE_NOT_FRESH, block(now = 2501))
        assertEquals(ExtractionStartGate.Block.IDLE_NOT_FRESH, block(coffeeAt = null))
        assertEquals(ExtractionStartGate.Block.IDLE_NOT_FRESH, block(coffeeAt = 2001))
        assertEquals(ExtractionStartGate.Block.IDLE_NOT_FRESH, block(frame = null))
        assertEquals(ExtractionStartGate.Block.IDLE_NOT_FRESH,
            block(frame = ExtractionTelemetry(7, 5, 90, 20, 9200, 10, 64, 0, raw)))
        assertEquals(ExtractionStartGate.Block.WEIGHT_NOT_FRESH, block(weightAt = null))
        assertEquals(ExtractionStartGate.Block.WEIGHT_NOT_FRESH, block(weightAt = 2001))
        assertEquals(ExtractionStartGate.Block.WEIGHT_NOT_FRESH, block(now = 2501, coffeeAt = 2500))
        assertNull(block(target = 0, now = 2501, coffeeAt = 2500, weightAt = null))
    }

    @Test fun sleepAndFaultsBlockWhileTailWaterRemainsAdvisory() {
        assertEquals(ExtractionStartGate.Block.ASLEEP, block(frame = idle.copy(sleepStateRaw = 1)))
        assertEquals(ExtractionStartGate.Block.SLEEP_UNKNOWN, block(frame = idle.copy(sleepStateRaw = 2)))
        assertNull(block(frame = idle.copy(alarmBits = 0x4000)))
        for (bit in 0..15) {
            if (bit != 14) assertEquals(ExtractionStartGate.Block.ALARM,
                block(frame = idle.copy(alarmBits = (1 shl bit) or 0x4000)))
        }
        assertEquals(ExtractionStartGate.Block.SCALE_NOT_READY,
            block(scale = DeviceState.DISCONNECTED, frame = idle.copy(alarmBits = 1)))
        assertEquals(ExtractionStartGate.Block.WEIGHT_NOT_FRESH,
            block(weightAt = null, frame = idle.copy(alarmBits = 1)))
    }
}
