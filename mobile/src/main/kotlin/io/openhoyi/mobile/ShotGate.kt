package io.openhoyi.mobile

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState

/** Host-side preflight; the lower session repeats the actual safety checks at the send boundary. */
object ShotGate {
    /** Unknown outcome must permit reconnection so a user can explicitly retry Stop. */
    fun mayReconnectCoffee(state: ExtractionState): Boolean = state !in setOf(
        ExtractionState.STARTING, ExtractionState.RUNNING, ExtractionState.STOP_REQUESTED,
    )
    fun active(state: ExtractionState): Boolean = state in setOf(
        ExtractionState.STARTING, ExtractionState.RUNNING,
        ExtractionState.STOP_REQUESTED, ExtractionState.OUTCOME_UNKNOWN,
    )
    fun startBlock(profile: CurveProfile?, coffee: DeviceState, coffeeFrame: HoyiMessage?, coffeeAt: Long?,
        scale: DeviceState, weightAt: Long?, now: Long, shot: ExtractionState,
        validated: Boolean = profile?.let(CurveCatalog::validated) == true): String? = when {
        profile == null -> "请先选择曲线"
        !validated -> "曲线未通过报文校验"
        active(shot) -> "上一杯尚未确认结束"
        coffee != DeviceState.READY -> "咖啡机尚未就绪"
        coffeeFrame !is IdleTelemetry || coffeeAt == null || coffeeAt > now || now - coffeeAt > 1500 -> "等待咖啡机新鲜待机数据"
        profile.targetHundredthsGram > 0 && scale != DeviceState.READY -> "目标重量萃取需要电子秤"
        profile.targetHundredthsGram > 0 && (weightAt == null || weightAt > now || now - weightAt > 1500) -> "电子秤数据已过期"
        else -> null
    }
}
