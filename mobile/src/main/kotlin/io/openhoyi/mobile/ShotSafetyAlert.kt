package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState

/** Advisory only: Bluetooth cannot guarantee a physical stop after a lost link. */
object ShotSafetyAlert {
    fun message(shot: ExtractionState, coffee: DeviceState): String? = when {
        shot == ExtractionState.OUTCOME_UNKNOWN && coffee != DeviceState.READY ->
            "萃取结果未知且连接中断。请立即检查咖啡机，必要时手动复位拨杆。"
        shot == ExtractionState.OUTCOME_UNKNOWN ->
            "萃取结果未知。请检查咖啡机；若仍在运行，请在确认后重试停止。"
        coffee != DeviceState.READY && shot in setOf(ExtractionState.STARTING,
            ExtractionState.RUNNING, ExtractionState.STOP_REQUESTED) ->
            "萃取中连接中断。请立即检查咖啡机，必要时手动复位拨杆。"
        else -> null
    }
}
