package io.openhoyi.mobile

import io.openhoyi.protocol.Settings
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.SleepPart

/** Formats decoded device values; it never creates a command or claims that a setting was applied. */
object MachineSettingsPresentation {
    fun leverMode(value: Settings?): String {
        if (value == null) return "拨杆模式：尚未收到设置"
        val pressure = value.flags and 0x80 != 0
        val flow = value.flags and 0x40 != 0
        return "拨杆模式：" + when {
            !pressure && flow -> "未知组合（原始 0x%02X）".format(value.flags and 0xC0)
            flow -> "自动流量"
            pressure -> "自动压力"
            else -> "手动"
        }
    }
    fun change(value: MachineSettingChange): String = when (value) {
        is MachineSettingChange.SleepScheduleEnabled -> "每周睡眠计划${if (value.enabled) "开启" else "关闭"}"
        is MachineSettingChange.StandbyDelay -> "自动待机：" + when (value.minutes) {
            0 -> "永不"
            60 -> "1 小时"
            120 -> "2 小时"
            else -> "${value.minutes} 分钟"
        }
        is MachineSettingChange.LeverMode -> "拨杆模式：" + when {
            value.flow -> "自动流量"
            value.pressure -> "自动压力"
            else -> "手动"
        }
        is MachineSettingChange.BrewTemperature -> "萃取温度 ${value.celsius} °C"
        is MachineSettingChange.SteamTemperature -> "蒸汽温度 ${value.celsius} °C"
        is MachineSettingChange.BrewHeating -> "萃取加热${if (value.enabled) "开启" else "关闭"}"
        is MachineSettingChange.SteamHeating -> "蒸汽加热${if (value.enabled) "开启" else "关闭"}"
        is MachineSettingChange.Light -> "照明${if (value.enabled) "开启" else "关闭"}"
    }
    fun settings(value: Settings?): String {
        if (value == null) return "尚未收到机器设置"
        fun enabled(bit: Int) = if (value.flags and bit != 0) "开启" else "关闭"
        return buildString {
            appendLine("固件  ${value.firmwareMajor}.${value.firmwareMinor}.${value.firmwarePatch}")
            appendLine("萃取设定温度  ${value.brewTemperatureC} °C")
            appendLine("萃取温差补偿  ${value.brewCompensationTenthsC / 10.0} °C")
            appendLine("蒸汽设定温度  ${value.steamTemperatureC} °C")
            appendLine("萃取加热  ${enabled(0x20)}")
            appendLine("蒸汽加热  ${enabled(0x10)}")
            appendLine("照明  ${enabled(0x08)}")
            appendLine(leverMode(value))
            appendLine("睡眠计划总开关  ${enabled(0x01)}")
            appendLine("自动待机  " + when (value.standbyMinutes) {
                0 -> "永不"
                60 -> "1 小时"
                120 -> "2 小时"
                15, 30 -> "${value.standbyMinutes} 分钟"
                else -> "机器回读 ${value.standbyMinutes} 分钟（非旧版预设）"
            })
            appendLine("待机温度  ${value.standbyTemperatureC} °C")
            appendLine("累计杯数  ${value.cupCount}")
            value.filterInstalled?.let { appendLine("滤芯状态  ${if (it) "已安装" else "未安装"}") }
            append("运行模式位  ${if (value.flags and 0x04 != 0) 1 else 0} · 供水模式位  ${if (value.flags and 0x02 != 0) 1 else 0}")
        }
    }

    fun schedule(first: SleepPart?, second: SleepPart?): String {
        if (first == null && second == null) return "尚未收到睡眠计划"
        val names = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        val days = mutableMapOf<Int, SleepDay>()
        for (part in listOfNotNull(first, second)) {
            part.days.forEachIndexed { index, day ->
                val offset = part.firstDaySundayIndex + index
                if (offset in 0..6) days[offset] = day
            }
        }
        return buildString {
            appendLine("每日启用位  ${first?.enabledBits?.let { "0x%02X".format(it) } ?: "尚未收到"}（周日到周六对应 bit7–bit1）")
            for (index in 0..6) {
                val day = days[index]
                val enabled = first?.enabledBits?.let { if (it and (0x80 shr index) != 0) "开启" else "关闭" } ?: "未知"
                appendLine("${names[index]}  $enabled · ${day?.let { "${time(it.sleepHour, it.sleepMinute)} → ${time(it.wakeHour, it.wakeMinute)}" } ?: "尚未收到"}")
            }
            if (first == null) append("前 4 天尚未收到")
            else if (second == null) append("后 3 天尚未收到")
        }.trimEnd()
    }

    private fun time(hour: Int, minute: Int): String =
        if (hour in 0..23 && minute in 0..59) "%02d:%02d".format(hour, minute)
        else "原始 $hour:$minute"
}

/** A screen transition may overlap; the service is foreground-visible while any screen is visible. */
class VisibleScreens {
    private val owners = mutableSetOf<String>()
    val visible: Boolean get() = owners.isNotEmpty()
    /** Returns true only when overall visibility changes. */
    fun set(owner: String, value: Boolean): Boolean {
        val before = visible
        if (value) owners.add(owner) else owners.remove(owner)
        return before != visible
    }
}
