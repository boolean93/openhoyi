package io.openhoyi.mobile

import io.openhoyi.protocol.BookooSample
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.Settings
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState

/** Deterministic UI fixture. It has no Bluetooth transport and cannot encode a GATT command. */
internal class MockDeviceRuntime {
    private val emptyFrame = ByteFrame(byteArrayOf())
    var shotState = ExtractionState.IDLE
        private set
    private var startedAtMs = 0L

    fun start(now: Long) {
        shotState = ExtractionState.RUNNING
        startedAtMs = now
    }

    fun stop() { shotState = ExtractionState.ENDED_OBSERVED }

    fun sample(now: Long): MobileSnapshot {
        val elapsed = if (shotState == ExtractionState.RUNNING) ((now - startedAtMs) / 1000).toInt().coerceAtLeast(0) else 0
        if (shotState == ExtractionState.RUNNING && elapsed >= 32) shotState = ExtractionState.ENDED_OBSERVED
        val extracting = shotState == ExtractionState.RUNNING
        val coffee = if (extracting) ExtractionTelemetry(
            slotOrPhase = 1, elapsedSeconds = elapsed, pressureTenthsBar = (elapsed * 10).coerceAtMost(90),
            totalWaterTenthsMl = elapsed * 6, brewTemperatureHundredthsC = 9300,
            flowTenthsMlPerSecond = if (elapsed < 4) 8 else 20,
            statusBits = 64, manualStageRaw = 0, raw = emptyFrame,
        ) else IdleTelemetry(
            brewTemperatureHundredthsC = 9300, steamTemperatureHundredthsC = 12000,
            brewPressureTenthsBar = 0, steamPressureTenthsBar = 8, sleepStateRaw = 0,
            alarmBits = 0, cupCount = 42, extraSensorRaw = 0, raw = emptyFrame,
        )
        return MobileSnapshot(
            coffeeState = DeviceState.READY, scaleState = DeviceState.READY,
            coffee = coffee, coffeeAt = now, alarmBits = 0, alarmAt = now,
            settings = Settings(1, 1, 3, 0x30, 93, 0, 120, 30, 80, 42, 0, emptyFrame),
            weight = BookooSample(if (extracting) elapsed * 70 else 0, if (extracting) 70 else 0,
                43, 43, emptyFrame), weightAt = now,
            message = "Mock 数据 · 不连接蓝牙设备",
        )
    }
}
