package io.openhoyi.mobile

import io.openhoyi.session.CoffeeFirmware
import io.openhoyi.session.CoffeeSessionFailure
import io.openhoyi.session.DeviceState
import org.junit.Assert.*
import org.junit.Test

class FirmwarePresentationTest {
    private val presentation = FirmwarePresentation(DefaultStringResources::resolve)
    @Test fun verifiedAndUnsupportedFirmwareHaveDifferentControlStatus() {
        assertEquals("固件 1.1.3 · 已验证协议", presentation.describe(CoffeeFirmware(1,1,3),DeviceState.READY,false))
        assertEquals("固件 1.1.4 · 已验证协议", presentation.describe(CoffeeFirmware(1,1,4),DeviceState.READY,false))
        assertEquals("固件 1.1.5 · 暂不支持新版固件", presentation.describe(CoffeeFirmware(1,1,5),DeviceState.UNSUPPORTED,false))
    }
    @Test fun disconnectedAndConnectingStatesDoNotDisplayOldMachineIdentity() {
        for(state in listOf(DeviceState.DISCONNECTED,DeviceState.CONNECTING,DeviceState.FAILED))
            assertEquals("固件版本：等待连接后读取", presentation.describe(CoffeeFirmware(1,1,3),state,false))
        assertEquals("固件版本：等待连接后读取",presentation.describe(null,DeviceState.READY,false))
    }
    @Test fun protocolTimeoutExplainsThatNewFirmwareMayBeUnsupported() {
        assertEquals("固件版本读取超时 · 可能是暂不支持的新版固件",
            presentation.describe(CoffeeFirmware(1,1,3),DeviceState.FAILED,false,CoffeeSessionFailure.PROTOCOL_TIMEOUT))
        assertEquals("固件版本读取超时 · 可能是暂不支持的新版固件",
            presentation.describe(null,DeviceState.FAILED,false,CoffeeSessionFailure.PROTOCOL_TIMEOUT))
        assertEquals("固件版本：等待连接后读取",
            presentation.describe(CoffeeFirmware(1,1,3),DeviceState.FAILED,false,CoffeeSessionFailure.AUTH))
    }
    @Test fun mockFirmwareDoesNotClaimRealDeviceValidation() {
        assertEquals("模拟固件 1.1.3 · 无蓝牙控制",presentation.describe(CoffeeFirmware(1,1,3),DeviceState.READY,true))
    }
}
