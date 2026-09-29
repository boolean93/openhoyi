package io.openhoyi.mobile

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState

/** A preheat recovery write must not race a current or unresolved extraction. */
object BrewWaitCancelGate {
    fun block(coffeeState: DeviceState, frame: HoyiMessage?, receivedAtMs: Long?, nowMs: Long,
        shotState: ExtractionState, unresolvedShot: Boolean): String? {
        if (unresolvedShot || ShotGate.active(shotState))
            return "萃取尚未确认结束，请先检查机器并用拨杆停液"
        if (coffeeState != DeviceState.READY) return "咖啡机未就绪，无法发送取消预热"
        val idle = frame as? IdleTelemetry ?: return "等待咖啡机新的待机回报后再取消预热"
        if (receivedAtMs?.let { it <= nowMs && nowMs - it <= 1500 } != true)
            return "咖啡机待机数据已过期，请等待新回报"
        if (idle.sleepStateRaw != 0) return "咖啡机未回报清醒待机，不能发送取消预热"
        return null
    }
}
