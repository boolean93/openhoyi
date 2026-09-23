package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class ShotGateTest {
    private val weighted = CurveCatalog.profiles[2]
    private val flow = CurveCatalog.profiles[1]
    private val idleFrame = IdleTelemetry(0, 0, 0, 0, 0, 0, 0, 0, ByteFrame(byteArrayOf()))
    private fun block(profile: CurveProfile?, coffee: DeviceState = DeviceState.READY,
        scale: DeviceState = DeviceState.READY, weightAt: Long? = 1000, now: Long = 2000,
        shot: ExtractionState = ExtractionState.IDLE, coffeeFrame: IdleTelemetry? = idleFrame,
        coffeeAt: Long? = 1000): String? = ShotGate.startBlock(profile, coffee, coffeeFrame, coffeeAt, scale, weightAt, now, shot)
    @Test fun weightedStartRequiresFreshScaleAndVerifiedCoffee() {
        assertEquals("请先选择曲线", block(null))
        assertEquals("咖啡机尚未就绪", block(weighted, coffee = DeviceState.UNSUPPORTED))
        assertEquals("等待咖啡机新鲜待机数据", block(weighted, coffeeFrame = null))
        assertEquals("等待咖啡机新鲜待机数据", block(weighted, coffeeAt = 0, now = 1501))
        assertEquals("目标重量萃取需要电子秤", block(weighted, scale = DeviceState.FAILED))
        assertEquals("电子秤数据已过期", block(weighted, weightAt = 1000, now = 2501, coffeeAt = 2500))
        assertEquals("电子秤数据已过期", block(weighted, weightAt = 3000, now = 2500, coffeeAt = 2500))
        assertNull(block(weighted, now = 2500, coffeeAt = 2500))
    }
    @Test fun flowProfileNeedsOnlyCoffeeAndActiveOutcomeBlocksRestart() {
        assertNull(block(flow, scale = DeviceState.DISCONNECTED, weightAt = null))
        val activeFrame = ExtractionTelemetry(7, 5, 90, 20, 9200, 10, 64, 0, ByteFrame(byteArrayOf()))
        assertEquals("等待咖啡机新鲜待机数据", ShotGate.startBlock(flow, DeviceState.READY,
            activeFrame, 1000, DeviceState.READY, 1000, 2000, ExtractionState.IDLE))
        for (state in listOf(ExtractionState.STARTING, ExtractionState.RUNNING, ExtractionState.STOP_REQUESTED, ExtractionState.OUTCOME_UNKNOWN)) {
            assertEquals("上一杯尚未确认结束", block(flow, shot = state))
            assertTrue(ShotGate.active(state))
        }
        assertFalse(ShotGate.mayReconnectCoffee(ExtractionState.RUNNING))
        assertTrue(ShotGate.mayReconnectCoffee(ExtractionState.OUTCOME_UNKNOWN))
        assertNull(block(flow, shot = ExtractionState.ENDED_OBSERVED))
    }
    @Test fun sleepingOrUnknownMachineStateCannotStart() {
        assertEquals("咖啡机处于睡眠状态", block(flow, coffeeFrame = idleFrame.copy(sleepStateRaw = 1)))
        assertEquals("咖啡机睡眠状态未知", block(flow, coffeeFrame = idleFrame.copy(sleepStateRaw = 2)))
    }
    @Test fun freshFaultAlarmsBlockStartButTailWaterWarningRemainsAdvisory() {
        assertTrue(block(flow, coffeeFrame = idleFrame.copy(alarmBits = 1))!!.contains("C1"))
        assertTrue(block(flow, coffeeFrame = idleFrame.copy(alarmBits = 1 shl 8))!!.contains("C9"))
        assertTrue(block(flow, coffeeFrame = idleFrame.copy(alarmBits = 0x8000))!!.contains("未知告警"))
        assertNull(block(flow, coffeeFrame = idleFrame.copy(alarmBits = 1 shl 14)))
        assertEquals("等待咖啡机新鲜待机数据", block(flow,
            coffeeFrame = idleFrame.copy(alarmBits = 0), coffeeAt = 1000, now = 2501))
    }
}
