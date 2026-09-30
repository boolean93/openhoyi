package io.openhoyi.mobile

import io.openhoyi.session.ShotRecoveryState

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState

/** Human acknowledgement still requires independent, fresh evidence of a stopped machine. */
object ShotRecoveryClearGate {
    fun block(recovery: ShotRecoveryState, address: String?, coffee: DeviceState,
        frame: HoyiMessage?, receivedAtMs: Long?, nowMs: Long,
        shot: ExtractionState, manualShotActive: Boolean): String? {
        if (manualShotActive || ShotGate.active(shot)) return "萃取尚未确认结束，请先用机器拨杆停液"
        if (!recovery.matchesDevice(address)) return "请先连接上一杯使用的咖啡机"
        if (coffee != DeviceState.READY || frame !is IdleTelemetry ||
            receivedAtMs?.let { it <= nowMs && nowMs - it <= 1500 } != true)
            return "请连接原咖啡机，等待新的待机回报后再确认"
        return null
    }
}
