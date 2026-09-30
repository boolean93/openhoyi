package io.openhoyi.mobile

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.DeviceConnectionGate
import io.openhoyi.session.ExtractionStartGate

/** Host-side preflight; session also checks readiness and allowed wire frames at send time. */
object ShotGate {
    /** Unknown outcome must permit reconnection so a user can explicitly retry Stop. */
    fun mayReconnectCoffee(state: ExtractionState): Boolean = state !in setOf(
        ExtractionState.STARTING, ExtractionState.RUNNING, ExtractionState.STOP_REQUESTED,
    )
    fun active(state: ExtractionState): Boolean = DeviceConnectionGate.unsettled(state)
    fun startBlock(profile: CurveProfile?, coffee: DeviceState, coffeeFrame: HoyiMessage?, coffeeAt: Long?,
        scale: DeviceState, weightAt: Long?, now: Long, shot: ExtractionState,
        validated: Boolean = profile?.let(CurveCatalog::validated) == true): String? =
        when (ExtractionStartGate.block(profile?.targetHundredthsGram, validated, coffee,
            coffeeFrame, coffeeAt, scale, weightAt, now, shot)) {
            ExtractionStartGate.Block.CURVE_MISSING -> "请先选择曲线"
            ExtractionStartGate.Block.CURVE_UNVERIFIED -> "曲线未通过报文校验"
            ExtractionStartGate.Block.EXTRACTION_UNSETTLED -> "上一杯尚未确认结束"
            ExtractionStartGate.Block.COFFEE_NOT_READY -> "咖啡机尚未就绪"
            ExtractionStartGate.Block.IDLE_NOT_FRESH -> "等待咖啡机新鲜待机数据"
            ExtractionStartGate.Block.ASLEEP -> "咖啡机处于睡眠状态"
            ExtractionStartGate.Block.SLEEP_UNKNOWN -> "咖啡机睡眠状态未知"
            ExtractionStartGate.Block.SCALE_NOT_READY -> "目标重量萃取需要电子秤"
            ExtractionStartGate.Block.WEIGHT_NOT_FRESH -> "电子秤数据已过期"
            ExtractionStartGate.Block.ALARM -> MachineAlarms.startBlock((coffeeFrame as IdleTelemetry).alarmBits)
            null -> null
        }
}
