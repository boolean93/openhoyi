package io.openhoyi.mobile

import io.openhoyi.session.DeviceState

/** User-facing connection state shared by device screens. */
internal object DeviceStatusText {
    fun label(state: DeviceState) = when (state) {
        DeviceState.DISCONNECTED -> "未连接"
        DeviceState.CONNECTING -> "连接中"
        DeviceState.DISCOVERING -> "查找服务中"
        DeviceState.SUBSCRIBING -> "订阅数据中"
        DeviceState.INITIALIZING -> "初始化中"
        DeviceState.SYNCHRONIZING -> "同步中"
        DeviceState.READY -> "已就绪"
        DeviceState.UNSUPPORTED -> "不支持"
        DeviceState.FAILED -> "连接失败"
    }
}
