package io.openhoyi.mobile

import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import java.util.Locale

/** Display values from one already freshness-checked machine frame. */
internal data class MachineHomeReadings(
    val brewTemperature: String,
    val brewPressure: String,
    val steamTemperature: String,
    val steamPressure: String,
) {
    companion object {
        private fun temperature(hundredths: Int) =
            String.format(Locale.ROOT, "%.2f °C", hundredths / 100.0)
        private fun pressure(tenths: Int) =
            String.format(Locale.ROOT, "%.1f bar", tenths / 10.0)

        fun from(frame: HoyiMessage?): MachineHomeReadings = when (frame) {
            is IdleTelemetry -> MachineHomeReadings(temperature(frame.brewTemperatureHundredthsC),
                pressure(frame.brewPressureTenthsBar), temperature(frame.steamTemperatureHundredthsC),
                pressure(frame.steamPressureTenthsBar))
            is ExtractionTelemetry -> MachineHomeReadings(temperature(frame.brewTemperatureHundredthsC),
                pressure(frame.pressureTenthsBar), "—", "—")
            else -> MachineHomeReadings("—", "—", "—", "—")
        }
    }
}
