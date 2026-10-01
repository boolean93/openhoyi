package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.Settings
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.SleepPart
import org.junit.Assert.*
import org.junit.Test

class MachineSettingsPresentationTest {
    private val presentation = MachineSettingsPresentation(DefaultStringResources::resolve)
    private val raw = ByteFrame(byteArrayOf())

    @Test fun settingChangeMessagesKeepExistingCopyAndRerenderNestedValues() {
        val examples = listOf(
            io.openhoyi.protocol.MachineSettingChange.RunMode(true) to "运行模式：工作室",
            io.openhoyi.protocol.MachineSettingChange.WaterSupply(false) to "供水方式：水箱",
            io.openhoyi.protocol.MachineSettingChange.SleepScheduleEnabled(true) to "每周睡眠计划开启",
            io.openhoyi.protocol.MachineSettingChange.StandbyDelay(0, 70) to "自动待机：永不",
            io.openhoyi.protocol.MachineSettingChange.StandbyDelay(15, 70) to "自动待机：15 分钟",
            io.openhoyi.protocol.MachineSettingChange.StandbyDelay(60, 70) to "自动待机：1 小时",
            io.openhoyi.protocol.MachineSettingChange.StandbyDelay(120, 70) to "自动待机：2 小时",
            io.openhoyi.protocol.MachineSettingChange.StandbyTemperature(70, 30) to "待机温度 70 °C",
            io.openhoyi.protocol.MachineSettingChange.LeverMode(false, false) to "拨杆模式：手动",
            io.openhoyi.protocol.MachineSettingChange.LeverMode(true, false) to "拨杆模式：自动压力",
            io.openhoyi.protocol.MachineSettingChange.LeverMode(true, true) to "拨杆模式：自动流量",
            io.openhoyi.protocol.MachineSettingChange.BrewTemperature(93) to "萃取温度 93 °C",
            io.openhoyi.protocol.MachineSettingChange.BrewCompensation(3) to "冲泡温差补偿 3 °C",
            io.openhoyi.protocol.MachineSettingChange.SteamTemperature(125) to "蒸汽温度 125 °C",
            io.openhoyi.protocol.MachineSettingChange.BrewHeating(false) to "萃取加热关闭",
            io.openhoyi.protocol.MachineSettingChange.SteamHeating(true) to "蒸汽加热开启",
            io.openhoyi.protocol.MachineSettingChange.Light(false) to "照明关闭")
        for ((change, expected) in examples) {
            val message = presentation.changeMessage(change)
            assertEquals(expected, message.render(DefaultStringResources::resolve))
            assertEquals(expected, presentation.change(change))
        }
        val message = SnapshotMessage.resource(ResourceMessage(R.string.service_write_setting_queued,
            presentation.changeMessage(io.openhoyi.protocol.MachineSettingChange.RunMode(true))),
            DefaultStringResources::resolve)
        assertEquals("queued:mode:studio", message.render { id, args -> when(id) {
            R.string.setting_run_studio -> "studio"
            R.string.settings_change_run -> "mode:${args.single()}"
            R.string.service_write_setting_queued -> "queued:${args.single()}"
            else -> error("Unexpected resource")
        } })
        assertTrue(message.initialText.contains("工作室"))
    }

    @Test fun settingsAreReadOnlyAndDoNotInventUnknownModes() {
        val settings = Settings(1, 1, 3, 0x39, 93, 5, 125, 30, 70, 128, 2, raw)
        val text = presentation.settings(settings)
        assertTrue(text.contains("1.1.3"))
        assertTrue(text.contains("93 °C"))
        assertTrue(text.contains("125 °C"))
        assertTrue(text.contains("供水方式  水箱"))
        assertTrue(text.contains("运行模式  咖啡馆"))
        assertTrue(presentation.settings(settings.copy(flags = settings.flags or 0x04)).contains("运行模式  工作室"))
        assertTrue(presentation.settings(settings.copy(flags = settings.flags or 0x02)).contains("供水方式  外接水管"))
        assertTrue(text.contains("30 分钟"))
        assertTrue(presentation.settings(settings.copy(standbyMinutes = 0)).contains("自动待机  永不"))
        assertTrue(presentation.settings(settings.copy(standbyMinutes = 120)).contains("自动待机  2 小时"))
        assertTrue(text.contains("128"))
        assertTrue(text.contains("开启"))
        assertFalse(text.contains("已应用"))
        val overview = presentation.overview(settings)
        assertTrue(overview.contains("萃取 93 °C · 蒸汽 125 °C"))
        assertTrue(overview.contains("运行模式 咖啡馆 · 供水 水箱"))
        assertTrue(overview.contains("自动待机 30 分钟 · 累计 128 杯"))
        assertFalse(overview.contains("已应用"))
        assertEquals("尚未收到机器设置", presentation.overview(null))
        assertEquals("尚未收到机器设置", presentation.settings(null))
        assertEquals("拨杆模式：尚未收到设置", presentation.leverMode(null))
        assertEquals("拨杆模式：手动", presentation.leverMode(settings))
        assertEquals("拨杆模式：自动压力", presentation.leverMode(settings.copy(flags = 0x80)))
        assertEquals("拨杆模式：自动流量", presentation.leverMode(settings.copy(flags = 0xC0)))
        assertTrue(presentation.leverMode(settings.copy(flags = 0x40)).contains("未知组合"))
    }

    @Test fun weeklyScheduleRequiresBothPartsAndKeepsUnknownEnabledMaskRaw() {
        val day = SleepDay(22, 30, 7, 15)
        val first = SleepPart(0, 0xA0, List(4) { day }, raw)
        val second = SleepPart(4, null, List(3) { day }, raw)
        assertTrue(presentation.schedule(first, null).contains("后 3 天尚未收到"))
        val text = presentation.schedule(first, second)
        assertTrue(text.contains("周日"))
        assertTrue(text.contains("周六"))
        assertTrue(text.contains("22:30"))
        assertTrue(text.contains("07:15"))
        assertTrue(text.contains("0xA0"))
        assertTrue(text.contains("周日  开启"))
        assertTrue(text.contains("周一  关闭"))
        assertTrue(text.contains("周二  开启"))
        val dayList = presentation.scheduleDays(first, second)
        assertTrue(dayList.contains("周日  开启"))
        assertFalse(dayList.contains("0xA0"))
    }

    @Test fun weeklySemanticStateIsIndependentOfTranslatedTextAndKeepsMissingMaskUnknown() {
        for (mask in 0..255) {
            val first = SleepPart(0, mask, List(4) { SleepDay(22, 30, 7, 15) }, raw)
            presentation.scheduleDaySummaries(first, null).forEachIndexed { index, day ->
                assertEquals(mask and (0x80 shr index) != 0, day.enabled)
                assertEquals(day.enabled, day.copy(state = "translated label").enabled)
            }
        }
        assertTrue(presentation.scheduleDaySummaries(null,
            SleepPart(4, null, List(3) { SleepDay(22, 30, 7, 15) }, raw)).all { it.enabled == null })
        assertTrue(presentation.scheduleDaySummaries(
            SleepPart(2, 0xFF, List(4) { SleepDay(22, 30, 7, 15) }, raw), null).all { it.enabled == null })
    }

    @Test fun translatedResourcesKeepUnknownReadbacksAndSemanticFlags() {
        val localized = MachineSettingsPresentation { id, args ->
            "translated[$id]" + args.joinToString()
        }
        for (mask in 0..255) {
            val first = SleepPart(0, mask, List(4) { SleepDay(22, 30, 7, 15) }, raw)
            val days = localized.scheduleDaySummaries(first, null)
            assertEquals(presentation.scheduleDaySummaries(first, null).map { it.enabled }, days.map { it.enabled })
            assertEquals("translated[${R.string.settings_time_missing}]", days[4].period)
        }
        localized.scheduleDaySummaries(null, null).forEach { day ->
            assertNull(day.enabled)
            assertEquals("translated[${R.string.settings_state_unknown}]", day.state)
            assertEquals("translated[${R.string.settings_time_missing}]", day.period)
        }
        assertEquals("translated[${R.string.settings_missing}]", localized.settings(null))
        assertEquals("translated[${R.string.settings_lever_missing}]", localized.leverMode(null))
        val malformedFirst = SleepPart(2, 0xFF, List(4) { SleepDay(22, 30, 7, 15) }, raw)
        assertTrue(localized.scheduleDaySummaries(malformedFirst, null).all { it.enabled == null })
    }

    @Test fun weeklyCardsShowPartialReadbackWithoutInventingMissingValues() {
        val first = SleepPart(0, 0x80, List(4) { SleepDay(22, 30, 7, 15) }, raw)
        val onlyFirst = presentation.scheduleDaySummaries(first, null)
        assertEquals(7, onlyFirst.size)
        assertEquals(MachineSettingsPresentation.SleepDaySummary("周日", "开启", "22:30 → 07:15", true), onlyFirst[0])
        assertEquals("关闭", onlyFirst[4].state)
        assertEquals("时间尚未回读", onlyFirst[4].period)
        val onlySecond = presentation.scheduleDaySummaries(
            null, SleepPart(4, null, List(3) { SleepDay(21, 0, 8, 0) }, raw))
        assertEquals("状态未知", onlySecond[4].state)
        assertEquals("21:00 → 08:00", onlySecond[4].period)
        assertTrue(presentation.scheduleDaySummaries(null, null)
            .all { it.state == "状态未知" && it.period == "时间尚未回读" })
        val malformedFirst = SleepPart(2, 0x80, List(4) { SleepDay(22, 30, 7, 15) }, raw)
        assertTrue(presentation.scheduleDaySummaries(malformedFirst, null)
            .all { it.state == "状态未知" })
    }
}
