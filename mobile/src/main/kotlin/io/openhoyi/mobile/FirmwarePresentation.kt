package io.openhoyi.mobile

import io.openhoyi.session.CoffeeFirmware
import io.openhoyi.session.DeviceState

object FirmwarePresentation {
    fun describe(firmware: CoffeeFirmware?, state: DeviceState, mock: Boolean): String {
        if(firmware==null || state !in setOf(DeviceState.READY,DeviceState.UNSUPPORTED))
            return "固件版本：等待连接后读取"
        if(mock) return "模拟固件 $firmware · 无蓝牙控制"
        return if(state==DeviceState.READY) "固件 $firmware · 已验证协议"
            else "固件 $firmware · 未验证，控制已禁用"
    }
}
