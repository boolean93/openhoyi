package io.openhoyi.mobile

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.ShotRecoveryGate
import io.openhoyi.session.ShotRecoveryState

/** Product text for shared human-acknowledgement eligibility. */
object ShotRecoveryClearGate {
    fun block(recovery: ShotRecoveryState, address: String?, coffee: DeviceState,
        frame: HoyiMessage?, receivedAtMs: Long?, nowMs: Long,
        shot: ExtractionState, manualShotActive: Boolean): String? = when (ShotRecoveryGate.clearBlock(
            recovery, address, coffee, frame, receivedAtMs, nowMs, shot, manualShotActive)) {
        ShotRecoveryGate.Block.EXTRACTION_UNSETTLED -> "萃取尚未确认结束，请先用机器拨杆停液"
        ShotRecoveryGate.Block.DEVICE_MISMATCH -> "请先连接上一杯使用的咖啡机"
        ShotRecoveryGate.Block.IDLE_NOT_FRESH -> "请连接原咖啡机，等待新的待机回报后再确认"
        null -> null
    }
}
