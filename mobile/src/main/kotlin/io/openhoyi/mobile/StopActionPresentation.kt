package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState

/** One stop affordance policy shared by Home and Extraction; transport checks remain in MobileService. */
object StopActionPresentation {
    data class State(val visible: Boolean, val enabled: Boolean, val label: String)

    fun describe(shot: ExtractionState, coffee: DeviceState, serviceRunning: Boolean): State {
        val visible = ShotGate.active(shot)
        val enabled = serviceRunning && shot in setOf(ExtractionState.STARTING, ExtractionState.RUNNING,
            ExtractionState.OUTCOME_UNKNOWN) &&
            (shot != ExtractionState.OUTCOME_UNKNOWN || coffee == DeviceState.READY)
        val label = when {
            shot == ExtractionState.STOP_REQUESTED -> "停止请求处理中"
            shot == ExtractionState.OUTCOME_UNKNOWN && coffee != DeviceState.READY -> "连接中断 · 请检查机器"
            else -> "立即停止萃取"
        }
        return State(visible, enabled, label)
    }
}
