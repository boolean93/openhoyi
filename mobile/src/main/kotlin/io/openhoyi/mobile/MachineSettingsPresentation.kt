package io.openhoyi.mobile

import io.openhoyi.protocol.Settings
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.SleepPart

/** Formats decoded device values; it never creates a command or claims that a setting was applied. */
class MachineSettingsPresentation(private val resolve: (Int, Array<out Any>) -> String) {
    constructor(context: android.content.Context) : this({ id, args -> context.getString(id, *args) })

    private fun text(id: Int, vararg args: Any): String = resolve(id, args)
    data class SleepDaySummary(val name: String, val state: String, val period: String, val enabled: Boolean?)

    fun overview(value: Settings?): String {
        if (value == null) return text(R.string.settings_missing)
        val mode = if (value.flags and 0x04 != 0) text(R.string.setting_run_studio) else text(R.string.setting_run_cafe)
        val supply = if (value.flags and 0x02 != 0) text(R.string.setting_water_piped) else text(R.string.setting_water_tank)
        val standby = if (value.standbyMinutes == 0) text(R.string.machine_settings_standby_never) else text(R.string.machine_settings_minutes, value.standbyMinutes.toString())
        return text(R.string.settings_overview_temperatures, value.brewTemperatureC.toString(), value.steamTemperatureC.toString()) +
            text(R.string.settings_overview_mode_supply, mode, supply) +
            text(R.string.settings_overview_standby_cups, standby, value.cupCount.toString())
    }
    fun leverMode(value: Settings?): String {
        if (value == null) return text(R.string.settings_lever_missing)
        val pressure = value.flags and 0x80 != 0
        val flow = value.flags and 0x40 != 0
        return text(R.string.settings_lever_prefix) + when {
            !pressure && flow -> text(R.string.settings_lever_unknown, "0x%02X".format(value.flags and 0xC0))
            flow -> text(R.string.home_lever_flow)
            pressure -> text(R.string.home_lever_pressure)
            else -> text(R.string.home_lever_manual)
        }
    }
    fun change(value: MachineSettingChange): String = when (value) {
        is MachineSettingChange.RunMode -> text(R.string.settings_change_run, if (value.studio) text(R.string.setting_run_studio) else text(R.string.setting_run_cafe))
        is MachineSettingChange.WaterSupply -> text(R.string.settings_change_water, if (value.piped) text(R.string.setting_water_piped) else text(R.string.setting_water_tank))
        is MachineSettingChange.SleepScheduleEnabled -> text(R.string.settings_change_schedule, enabledText(value.enabled))
        is MachineSettingChange.StandbyDelay -> text(R.string.settings_change_standby_prefix) + when (value.minutes) {
            0 -> text(R.string.machine_settings_standby_never)
            60 -> text(R.string.settings_one_hour)
            120 -> text(R.string.settings_two_hours)
            else -> text(R.string.machine_settings_minutes, value.minutes.toString())
        }
        is MachineSettingChange.StandbyTemperature -> text(R.string.settings_change_standby_temperature, value.celsius.toString())
        is MachineSettingChange.LeverMode -> text(R.string.settings_lever_prefix) + when {
            value.flow -> text(R.string.home_lever_flow)
            value.pressure -> text(R.string.home_lever_pressure)
            else -> text(R.string.home_lever_manual)
        }
        is MachineSettingChange.BrewTemperature -> text(R.string.settings_change_brew_temperature, value.celsius.toString())
        is MachineSettingChange.BrewCompensation -> text(R.string.settings_change_compensation, value.celsius.toString())
        is MachineSettingChange.SteamTemperature -> text(R.string.settings_change_steam_temperature, value.celsius.toString())
        is MachineSettingChange.BrewHeating -> text(R.string.settings_change_brew_heating, enabledText(value.enabled))
        is MachineSettingChange.SteamHeating -> text(R.string.settings_change_steam_heating, enabledText(value.enabled))
        is MachineSettingChange.Light -> text(R.string.settings_change_light, enabledText(value.enabled))
    }
    fun settings(value: Settings?): String {
        if (value == null) return text(R.string.settings_missing)
        fun enabled(bit: Int) = if (value.flags and bit != 0) text(R.string.setting_on) else text(R.string.setting_off)
        return buildString {
            appendLine(text(R.string.settings_readback_firmware, "${value.firmwareMajor}.${value.firmwareMinor}.${value.firmwarePatch}"))
            appendLine(text(R.string.settings_readback_brew_temperature, value.brewTemperatureC.toString()))
            appendLine(text(R.string.settings_readback_compensation, (value.brewCompensationTenthsC / 10.0).toString()))
            appendLine(text(R.string.settings_readback_steam_temperature, value.steamTemperatureC.toString()))
            appendLine(text(R.string.settings_readback_brew_heating, enabled(0x20)))
            appendLine(text(R.string.settings_readback_steam_heating, enabled(0x10)))
            appendLine(text(R.string.settings_readback_light, enabled(0x08)))
            appendLine(text(R.string.settings_readback_water, if (value.flags and 0x02 != 0) text(R.string.setting_water_piped) else text(R.string.setting_water_tank)))
            appendLine(leverMode(value))
            appendLine(text(R.string.settings_readback_schedule, enabled(0x01)))
            appendLine(text(R.string.settings_readback_standby_prefix) + when (value.standbyMinutes) {
                0 -> text(R.string.machine_settings_standby_never)
                60 -> text(R.string.settings_one_hour)
                120 -> text(R.string.settings_two_hours)
                15, 30 -> text(R.string.machine_settings_minutes, value.standbyMinutes.toString())
                else -> text(R.string.settings_readback_standby_nonpreset, value.standbyMinutes.toString())
            })
            appendLine(text(R.string.settings_readback_standby_temperature, value.standbyTemperatureC.toString()))
            appendLine(text(R.string.settings_readback_cups, value.cupCount.toString()))
            value.filterInstalled?.let { appendLine(text(R.string.settings_readback_filter, text(if (it) R.string.settings_filter_installed else R.string.settings_filter_absent))) }
            append(text(R.string.settings_readback_run, if (value.flags and 0x04 != 0) text(R.string.setting_run_studio) else text(R.string.setting_run_cafe)))
        }
    }

    fun schedule(first: SleepPart?, second: SleepPart?): String {
        if (first == null && second == null) return text(R.string.settings_schedule_missing)
        val names = listOf(text(R.string.machine_settings_sunday), text(R.string.machine_settings_monday), text(R.string.machine_settings_tuesday), text(R.string.machine_settings_wednesday), text(R.string.machine_settings_thursday), text(R.string.machine_settings_friday), text(R.string.machine_settings_saturday))
        val days = mutableMapOf<Int, SleepDay>()
        for (part in listOfNotNull(first, second)) {
            part.days.forEachIndexed { index, day ->
                val offset = part.firstDaySundayIndex + index
                if (offset in 0..6) days[offset] = day
            }
        }
        return buildString {
            appendLine(text(R.string.settings_schedule_mask, first?.enabledBits?.let { "0x%02X".format(it) } ?: text(R.string.settings_not_received)))
            for (index in 0..6) {
                val day = days[index]
                val enabled = first?.enabledBits?.let { if (it and (0x80 shr index) != 0) text(R.string.setting_on) else text(R.string.setting_off) } ?: text(R.string.settings_unknown)
                appendLine(text(R.string.settings_schedule_day, names[index], enabled, day?.let { "${time(it.sleepHour, it.sleepMinute)} → ${time(it.wakeHour, it.wakeMinute)}" } ?: text(R.string.settings_not_received)))
            }
            if (first == null) append(text(R.string.settings_schedule_first_missing))
            else if (second == null) append(text(R.string.settings_schedule_second_missing))
        }.trimEnd()
    }

    /** The day list shown in the UI; the raw enable mask remains available in schedule(). */
    fun scheduleDays(first: SleepPart?, second: SleepPart?): String {
        val full = schedule(first, second)
        if (first == null && second == null) return full
        return full.lineSequence().drop(1).joinToString("\n")
    }

    fun scheduleDaySummaries(first: SleepPart?, second: SleepPart?): List<SleepDaySummary> {
        val names = listOf(text(R.string.machine_settings_sunday), text(R.string.machine_settings_monday), text(R.string.machine_settings_tuesday), text(R.string.machine_settings_wednesday), text(R.string.machine_settings_thursday), text(R.string.machine_settings_friday), text(R.string.machine_settings_saturday))
        val enabledBits = first?.takeIf { it.firstDaySundayIndex == 0 }?.enabledBits
        val days = mutableMapOf<Int, SleepDay>()
        listOfNotNull(first, second).forEach { part ->
            part.days.forEachIndexed { index, day ->
                (part.firstDaySundayIndex + index).takeIf { it in 0..6 }?.let { days[it] = day }
            }
        }
        return names.mapIndexed { index, name ->
            val enabled = enabledBits?.let { it and (0x80 shr index) != 0 }
            val state = enabled?.let { if (it) text(R.string.setting_on) else text(R.string.setting_off) } ?: text(R.string.settings_state_unknown)
            val period = days[index]?.let { "${time(it.sleepHour, it.sleepMinute)} → ${time(it.wakeHour, it.wakeMinute)}" }
                ?: text(R.string.settings_time_missing)
            SleepDaySummary(name, state, period, enabled)
        }
    }

    private fun enabledText(enabled: Boolean): String =
        text(if (enabled) R.string.setting_on else R.string.setting_off)

    private fun time(hour: Int, minute: Int): String =
        if (hour in 0..23 && minute in 0..59) "%02d:%02d".format(hour, minute)
        else text(R.string.settings_raw_time, hour.toString(), minute.toString())
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
