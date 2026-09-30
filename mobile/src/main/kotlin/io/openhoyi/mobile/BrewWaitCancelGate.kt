package io.openhoyi.mobile

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.PreheatGate

/** Maps shared preheat recovery decisions to product text. */
object BrewWaitCancelGate {
    fun block(coffeeState: DeviceState, frame: HoyiMessage?, receivedAtMs: Long?, nowMs: Long,
        shotState: ExtractionState, unresolvedShot: Boolean): String? = when (PreheatGate.cancelBlock(
            coffeeState, frame, receivedAtMs, nowMs, shotState, unresolvedShot)) {
        PreheatGate.CancelBlock.EXTRACTION_UNSETTLED -> "萃取尚未确认结束，请先检查机器并用拨杆停液"
        PreheatGate.CancelBlock.COFFEE_NOT_READY -> "咖啡机未就绪，无法发送取消预热"
        PreheatGate.CancelBlock.IDLE_MISSING -> "等待咖啡机新的待机回报后再取消预热"
        PreheatGate.CancelBlock.IDLE_STALE -> "咖啡机待机数据已过期，请等待新回报"
        PreheatGate.CancelBlock.NOT_AWAKE -> "咖啡机未回报清醒待机，不能发送取消预热"
        null -> null
    }
}
