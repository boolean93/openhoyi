package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ConnectionDiagnosticDisplayTest {
    @Test fun normalStatusNotificationsRemainDiagnosticOnly() {
        listOf("scale decode: Unknown", "coffee decode: Unknown", "scale frame boundary resynchronized",
            "coffee.initial_sync.settings=received", "coffee.initial_sync.refresh=dispatched").forEach {
            assertTrue(it, ConnectionDiagnosticDisplay.isInformational(it))
        }
    }
    @Test fun invalidFramesAndActualFailuresAreNotSilenced() {
        listOf("scale decode: Invalid", "coffee decode: Invalid", "scale evidence rejected",
            "GATT write status=133", "weekly sleep callback failure", "new failure").forEach {
            assertFalse(it, ConnectionDiagnosticDisplay.isInformational(it))
        }
    }
    @Test fun rememberedAddressIsNotCombinedWithCurrentConnectionState() {
        assertEquals("连接中\n已记设备：old-device",
            DeviceStatusText.settingsSummary("连接中", "已记设备：old-device"))
        assertEquals("Failed\nRemembered device: old-device",
            DeviceStatusText.settingsSummary("Failed", "Remembered device: old-device"))
    }
}
