package io.openhoyi.mobile

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.protocol.ScaleEvidence
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ScaleReadingPolicy
import io.openhoyi.session.ScaleSelectionPolicy
import io.openhoyi.session.observationEvidence

/** Only current, connected readings may be displayed as live values. */
object LiveTelemetry {
    private fun fresh(state: DeviceState, at: Long?, now: Long): Boolean =
        state == DeviceState.READY && at != null && at >= 0 && at <= now && now - at <= 1500

    fun machine(frame: HoyiMessage?, state: DeviceState, at: Long?, now: Long): HoyiMessage? =
        frame.takeIf { fresh(state, at, now) }

    /** Read-only display does not confer permission to control extraction. */
    fun scale(sample: ScaleObservation?, state: DeviceState, now: Long): ScaleObservation? =
        sample?.takeIf { it.capabilities.weight && state == DeviceState.READY &&
            ScaleReadingPolicy.isFresh(it.hundredthsGram, it.receivedAtMs, now) }

    /** Snapshot samples are cleared by the service on scale replacement/disconnect. */
    internal fun scaleBattery(protocolId: String?, sample: ScaleObservation?, state: DeviceState, now: Long): Int? {
        val adapter = protocolId?.let(ScaleSelectionPolicy::adapter)?.takeIf { it.capabilities.battery } ?: return null
        return observedBattery(sample, state, now, adapter.observationEvidence)
    }

    internal fun observedBattery(sample: ScaleObservation?, state: DeviceState, now: Long, expected: ScaleEvidence): Int? =
        scale(sample, state, now)?.takeIf { it.capabilities.battery && it.evidence == expected }
            ?.batteryPercent?.takeIf { it in 0..100 }
}
