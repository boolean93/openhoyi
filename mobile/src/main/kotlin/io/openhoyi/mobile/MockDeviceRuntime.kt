package io.openhoyi.mobile

import io.openhoyi.protocol.BookooSample
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.Settings
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.SleepPart
import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState

/** Deterministic UI fixture. It has no Bluetooth transport and cannot encode a GATT command. */
internal class MockDeviceRuntime {
    private val emptyFrame = ByteFrame(byteArrayOf())
    private var schedule = WeeklySleepSchedule(List(7) { index ->
        WeeklySleepDay(index in 1..5, SleepDay(23, 0, 7, 0))
    })
    private var settings = Settings(1, 1, 3, 0x31, 93, 0, 120, 30, 80, 42, 0, emptyFrame)
    private var idleWeightHundredths = 1250
    private var sleeping = false
    var lastSetting: MachineSettingChange? = null
        private set
    var scheduleChanged = false
        private set
    var tareChanged = false
        private set
    var cupReset = false
        private set
    val isSleeping get() = sleeping
    var shotState = ExtractionState.IDLE
        private set
    private var startedAtMs = 0L
    private var lastShotElapsed = 0

    fun start(now: Long) {
        shotState = ExtractionState.RUNNING
        startedAtMs = now
        lastShotElapsed = 0
        idleWeightHundredths = 0
    }

    fun stop() {
        idleWeightHundredths = lastShotElapsed * 70
        shotState = ExtractionState.ENDED_OBSERVED
    }

    private fun editable(): String? = when {
        shotState == ExtractionState.RUNNING -> "Mock 萃取正在进行"
        sleeping -> "Mock 咖啡机已入睡；重启模拟服务可复位"
        else -> null
    }

    fun changeSetting(change: MachineSettingChange): String? {
        editable()?.let { return it }
        if (change is MachineSettingChange.StandbyDelay && change.temperatureC != settings.standbyTemperatureC)
            return "Mock 待机温度已变化"
        if (change is MachineSettingChange.StandbyTemperature && change.minutes != settings.standbyMinutes)
            return "Mock 自动待机时间已变化"
        fun flag(mask: Int, enabled: Boolean): Int = if (enabled) settings.flags or mask else settings.flags and mask.inv()
        settings = when (change) {
            is MachineSettingChange.RunMode -> settings.copy(flags = flag(0x04, change.studio))
            is MachineSettingChange.WaterSupply -> settings.copy(flags = flag(0x02, change.piped))
            is MachineSettingChange.SleepScheduleEnabled -> settings.copy(flags = flag(0x01, change.enabled))
            is MachineSettingChange.StandbyDelay -> settings.copy(standbyMinutes = change.minutes)
            is MachineSettingChange.StandbyTemperature -> settings.copy(standbyTemperatureC = change.celsius)
            is MachineSettingChange.LeverMode -> settings.copy(flags =
                (settings.flags and 0xC0.inv()) or (if (change.pressure) 0x80 else 0) or
                    (if (change.flow) 0x40 else 0))
            is MachineSettingChange.BrewTemperature -> settings.copy(brewTemperatureC = change.celsius)
            is MachineSettingChange.BrewCompensation -> settings.copy(brewCompensationTenthsC = change.celsius * 10)
            is MachineSettingChange.SteamTemperature -> settings.copy(steamTemperatureC = change.celsius)
            is MachineSettingChange.BrewHeating -> settings.copy(flags = flag(0x20, change.enabled))
            is MachineSettingChange.SteamHeating -> settings.copy(flags = flag(0x10, change.enabled))
            is MachineSettingChange.Light -> settings.copy(flags = flag(0x08, change.enabled))
        }
        lastSetting = change
        return null
    }

    fun changeSchedule(expected: WeeklySleepSchedule, target: WeeklySleepSchedule): String? {
        editable()?.let { return it }
        if (expected.days != schedule.days) return "Mock 睡眠计划已变化，请重新编辑"
        if (expected.days.indices.count { expected.days[it] != target.days[it] } != 1)
            return "一次只能修改一天的 Mock 睡眠计划"
        schedule = target
        scheduleChanged = true
        return null
    }

    fun tare(): String? {
        editable()?.let { return it }
        idleWeightHundredths = 0
        tareChanged = true
        return null
    }

    fun resetCupCount(expected: Int): String? {
        editable()?.let { return it }
        if (expected <= 0 || settings.cupCount != expected) return "Mock 杯数已变化，请重新核对"
        settings = settings.copy(cupCount = 0)
        cupReset = true
        return null
    }

    fun sleepNow(): String? {
        editable()?.let { return it }
        sleeping = true
        return null
    }

    fun sample(now: Long): MobileSnapshot {
        val elapsed = if (shotState == ExtractionState.RUNNING) ((now - startedAtMs) / 1000).toInt().coerceAtLeast(0) else 0
        if (shotState == ExtractionState.RUNNING) {
            lastShotElapsed = elapsed.coerceAtMost(32)
            if (elapsed >= 32) stop()
        }
        val extracting = shotState == ExtractionState.RUNNING
        val coffee = if (extracting) ExtractionTelemetry(
            slotOrPhase = 1, elapsedSeconds = elapsed, pressureTenthsBar = (elapsed * 10).coerceAtMost(90),
            totalWaterTenthsMl = elapsed * 6, brewTemperatureHundredthsC = 9300,
            flowTenthsMlPerSecond = if (elapsed < 4) 8 else 20,
            statusBits = 64, manualStageRaw = 0, raw = emptyFrame,
        ) else IdleTelemetry(
            brewTemperatureHundredthsC = 9300, steamTemperatureHundredthsC = 12000,
            brewPressureTenthsBar = 0, steamPressureTenthsBar = 8, sleepStateRaw = if (sleeping) 1 else 0,
            alarmBits = 0, cupCount = settings.cupCount, extraSensorRaw = 0, raw = emptyFrame,
        )
        return MobileSnapshot(
            coffeeState = DeviceState.READY, scaleState = DeviceState.READY,
            coffee = coffee, coffeeAt = now, alarmBits = 0, alarmAt = now,
            settings = settings,
            sleepFirst = SleepPart(0, schedule.days.foldIndexed(0) { index, bits, day ->
                bits or if (day.enabled) (0x80 shr index) else 0
            }, schedule.days.take(4).map(WeeklySleepDay::time), emptyFrame),
            sleepSecond = SleepPart(4, null, schedule.days.drop(4).map(WeeklySleepDay::time), emptyFrame),
            weight = BookooSample(if (extracting) elapsed * 70 else idleWeightHundredths,
                if (extracting) 70 else 0,
                43, 43, emptyFrame), weightAt = now,
            message = "Mock 数据 · 不连接蓝牙设备",
        )
    }
}
