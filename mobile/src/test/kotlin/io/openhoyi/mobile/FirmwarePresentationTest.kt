package io.openhoyi.mobile

import io.openhoyi.session.CoffeeFirmware
import io.openhoyi.session.DeviceState
import org.junit.Assert.*
import org.junit.Test

class FirmwarePresentationTest {
    @Test fun verifiedAndUnsupportedFirmwareHaveDifferentControlStatus() {
        assertEquals("固件 1.1.3 · 已验证协议", FirmwarePresentation.describe(CoffeeFirmware(1,1,3),DeviceState.READY,false))
        assertEquals("固件 1.1.4 · 未验证，控制已禁用", FirmwarePresentation.describe(CoffeeFirmware(1,1,4),DeviceState.UNSUPPORTED,false))
    }
    @Test fun disconnectedAndConnectingStatesDoNotDisplayOldMachineIdentity() {
        for(state in listOf(DeviceState.DISCONNECTED,DeviceState.CONNECTING,DeviceState.FAILED))
            assertEquals("固件版本：等待连接后读取", FirmwarePresentation.describe(CoffeeFirmware(1,1,3),state,false))
        assertEquals("固件版本：等待连接后读取",FirmwarePresentation.describe(null,DeviceState.READY,false))
    }
    @Test fun mockFirmwareDoesNotClaimRealDeviceValidation() {
        assertEquals("模拟固件 1.1.3 · 无蓝牙控制",FirmwarePresentation.describe(CoffeeFirmware(1,1,3),DeviceState.READY,true))
    }
}
