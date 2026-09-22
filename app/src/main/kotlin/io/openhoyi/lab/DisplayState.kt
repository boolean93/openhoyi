package io.openhoyi.lab

import io.openhoyi.bluetooth.DiscoveredDevice
import io.openhoyi.protocol.BookooSample
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.Settings
import io.openhoyi.session.DeviceState
import java.util.Locale

/** UI projections only; no packet parsing or protocol decisions. */
data class LabSnapshot(
    val coffeeState: DeviceState = DeviceState.DISCONNECTED,
    val scaleState: DeviceState = DeviceState.DISCONNECTED,
    val coffee: HoyiMessage? = null,
    val coffeeAt: Long? = null,
    val settings: Settings? = null,
    val weight: BookooSample? = null,
    val weightAt: Long? = null,
    val devices: List<DiscoveredDevice> = emptyList(),
    val scanning: Boolean = false,
    val events: List<String> = emptyList(),
)
fun freshness(at: Long?, now: Long, connected: Boolean): String = when {
    at == null -> "尚无数据"
    !connected -> "已断开 · 历史数据"
    now < at || now - at > 1500 -> "数据已过期"
    else -> "实时"
}
fun hundredths(value: Int): String = String.format(Locale.ROOT, "%.2f", value / 100.0)
fun DeviceState.label(): String = when(this) {
    DeviceState.DISCONNECTED -> "未连接"
    DeviceState.CONNECTING -> "连接中"
    DeviceState.DISCOVERING -> "发现服务中"
    DeviceState.SUBSCRIBING -> "订阅数据中"
    DeviceState.INITIALIZING -> "初始化中"
    DeviceState.SYNCHRONIZING -> "等待设备数据"
    DeviceState.READY -> "已就绪"
    DeviceState.UNSUPPORTED -> "固件未验证 · 仅查看"
    DeviceState.FAILED -> "连接失败"
}
