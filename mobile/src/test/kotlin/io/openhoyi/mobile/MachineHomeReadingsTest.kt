package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import org.junit.Assert.assertEquals
import org.junit.Test

class MachineHomeReadingsTest {
    private val raw = ByteFrame(byteArrayOf())

    @Test fun freshIdleUsesBothIndependentBoilerSensorsIncludingZero() {
        val frame = IdleTelemetry(9315, 12420, 0, 17, 0, 0, 1, 0, raw)
        val result = MachineHomeReadings.from(LiveTelemetry.machine(frame, DeviceState.READY, 1000, 2500))
        assertEquals("93.15 °C", result.brewTemperature)
        assertEquals("0.0 bar", result.brewPressure)
        assertEquals("124.20 °C", result.steamTemperature)
        assertEquals("1.7 bar", result.steamPressure)
    }

    @Test fun extractionNeverInventsSteamValues() {
        val frame = ExtractionTelemetry(1, 8, 93, 45, 9450, 20, 0, 0, raw)
        val result = MachineHomeReadings.from(LiveTelemetry.machine(frame, DeviceState.READY, 1000, 1000))
        assertEquals("94.50 °C", result.brewTemperature)
        assertEquals("9.3 bar", result.brewPressure)
        assertEquals("—", result.steamTemperature)
        assertEquals("—", result.steamPressure)
    }

    @Test fun staleOrDisconnectedFrameClearsAllFourValues() {
        val frame = IdleTelemetry(9315, 12420, 60, 17, 0, 0, 1, 0, raw)
        val empty = MachineHomeReadings("—", "—", "—", "—")
        assertEquals(empty, MachineHomeReadings.from(LiveTelemetry.machine(frame, DeviceState.READY, 1000, 2501)))
        assertEquals(empty, MachineHomeReadings.from(LiveTelemetry.machine(frame, DeviceState.DISCONNECTED, 2499, 2500)))
    }
}
