package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.Settings
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.SleepPart
import org.junit.Assert.*
import org.junit.Test

class MachineSettingsPresentationTest {
    private val raw = ByteFrame(byteArrayOf())

    @Test fun settingsAreReadOnlyAndDoNotInventUnknownModes() {
        val settings = Settings(1, 1, 3, 0x39, 93, 5, 125, 30, 70, 128, 2, raw)
        val text = MachineSettingsPresentation.settings(settings)
        assertTrue(text.contains("1.1.3"))
        assertTrue(text.contains("93 °C"))
        assertTrue(text.contains("125 °C"))
        assertTrue(text.contains("30 分钟"))
        assertTrue(text.contains("128"))
        assertTrue(text.contains("开启"))
        assertFalse(text.contains("已应用"))
        assertEquals("尚未收到机器设置", MachineSettingsPresentation.settings(null))
    }

    @Test fun weeklyScheduleRequiresBothPartsAndKeepsUnknownEnabledMaskRaw() {
        val day = SleepDay(22, 30, 7, 15)
        val first = SleepPart(0, 254, List(4) { day }, raw)
        val second = SleepPart(4, null, List(3) { day }, raw)
        assertTrue(MachineSettingsPresentation.schedule(first, null).contains("后 3 天尚未收到"))
        val text = MachineSettingsPresentation.schedule(first, second)
        assertTrue(text.contains("周日"))
        assertTrue(text.contains("周六"))
        assertTrue(text.contains("22:30"))
        assertTrue(text.contains("07:15"))
        assertTrue(text.contains("0xFE"))
    }
}
