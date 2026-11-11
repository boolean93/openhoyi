package io.openhoyi.mobile

import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.session.DeviceState

/** Readings for history and samples are observations, never control permits or curve estimates. */
internal object ScaleBusinessReading {
    fun live(observation: ScaleObservation?, state: DeviceState, now: Long): ScaleObservation? =
        LiveTelemetry.scale(observation, state, now)

    fun terminalWeight(observation: ScaleObservation?, state: DeviceState, now: Long): Int? =
        live(observation, state, now)?.hundredthsGram

    fun recordMachine(series: ShotSeries, frame: ExtractionTelemetry, atMs: Long,
        observation: ScaleObservation?, state: DeviceState) {
        val live = live(observation, state, atMs)
        series.machine(frame, atMs, live?.hundredthsGram, live?.receivedAtMs,
            live?.deviceFlowHundredths?.takeIf { live.capabilities.deviceFlow })
    }
}
