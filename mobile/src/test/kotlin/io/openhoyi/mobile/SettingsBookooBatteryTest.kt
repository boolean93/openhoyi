package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ScaleEvidence
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.protocol.ScaleObservationDecoder
import io.openhoyi.session.BookooScaleProtocolAdapter
import io.openhoyi.session.BookooUltraLegacyScaleProtocolAdapter
import io.openhoyi.session.DeviceState
import io.openhoyi.session.TimemoreDotScaleProtocolAdapter
import io.openhoyi.session.FelicitaScaleProtocolAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBookooBatteryTest {
    private val mini = BookooScaleProtocolAdapter.id
    private val ultra = BookooUltraLegacyScaleProtocolAdapter.id
    private val sample = ScaleObservation(25, 1000L, ScaleObservationDecoder.bookooCapabilities,
        ScaleEvidence.VERIFIED_TRANSPORT, batteryPercent = 62, raw = ByteFrame(byteArrayOf()))
    private val ready = DeviceState.READY

    @Test fun onlyReadyCurrentFreshSupportedObservationHasBattery() {
        assertEquals(62, LiveTelemetry.scaleBattery(mini, sample, ready, 2500))
        assertEquals(0, LiveTelemetry.scaleBattery(mini, sample.copy(batteryPercent = 0), ready, 1000))
        assertEquals(100, LiveTelemetry.scaleBattery(ultra,
            sample.copy(evidence = ScaleEvidence.DOCUMENTED_TRANSPORT, batteryPercent = 100), ready, 1000))

        assertNull(LiveTelemetry.scaleBattery(mini, null, ready, 1001)) // New connection awaits data.
        assertEquals(62, LiveTelemetry.scaleBattery(TimemoreDotScaleProtocolAdapter.id,
            sample.copy(evidence = ScaleEvidence.DOCUMENTED_TRANSPORT), ready, 1001))
        assertNull(LiveTelemetry.scaleBattery(ultra, sample, ready, 1001)) // Replaced with different profile.
        for (state in DeviceState.entries.filter { it != ready })
            assertNull(LiveTelemetry.scaleBattery(mini, sample, state, 1001))
        for (time in listOf(999L, 2501L)) assertNull(LiveTelemetry.scaleBattery(mini, sample, ready, time))
        for (id in listOf(null, "unrecognized", FelicitaScaleProtocolAdapter.id)) assertNull(LiveTelemetry.scaleBattery(id, sample, ready, 1001))
        for (percent in listOf(null, 101, 255))
            assertNull(LiveTelemetry.scaleBattery(mini, sample.copy(batteryPercent = percent), ready, 1001))
        assertNull(LiveTelemetry.scaleBattery(mini,
            sample.copy(capabilities = sample.capabilities.copy(battery = false)), ready, 1001))
    }
}
