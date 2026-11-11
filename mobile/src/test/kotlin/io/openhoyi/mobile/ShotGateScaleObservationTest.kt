package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.DecodeResult
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.ScaleCapabilities
import io.openhoyi.protocol.ScaleEvidence
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.session.BookooScaleProtocolAdapter
import io.openhoyi.session.DeviceState
import io.openhoyi.session.DiFluidScaleProtocolAdapter
import io.openhoyi.session.DiFluidTiScaleProtocolAdapter
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.FelicitaScaleProtocolAdapter
import io.openhoyi.session.ScaleProtocolAdapter
import org.junit.Assert.*
import org.junit.Test

/** Real adapter -> generic observation -> shared mobile preflight, with no BOOKOO callback. */
class ShotGateScaleObservationTest {
    private val gate = ShotGate(DefaultStringResources::resolve, mockMode = false)
    private val weighted = CurveCatalog.profiles[2]
    private val idle = IdleTelemetry(0, 0, 0, 0, 0, 0, 0, 0, ByteFrame(byteArrayOf()))
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val felicitaFrame = hex("01022B303037363030206700000000000D0A")
    // Official 76 g full sensor frame, including the embedded gram unit.
    private val diFluidFrame = hex("DFDF03000D000002F800000000000A27B000A9")
    private fun observation(adapter: ScaleProtocolAdapter, bytes: ByteArray): ScaleObservation =
        (adapter.decode(bytes, 1000) as DecodeResult.Valid).value.also {
            assertEquals(7600, it.hundredthsGram)
            assertEquals(1000L, it.receivedAtMs)
        }
    private fun block(observation: ScaleObservation?, legacyAt: Long? = null, now: Long = 2000,
        scale: DeviceState = DeviceState.READY, profile: CurveProfile = weighted,
        gate: ShotGate = this.gate): Int? =
        gate.startBlockMessage(profile, DeviceState.READY, idle, now, scale,
            legacyAt, now, ExtractionState.IDLE, validated = true,
            scaleObservation = observation)?.resourceId

    @Test fun freshFelicitaReadingDoesNotNeedBookooTimestamp() {
        assertNull(block(observation(FelicitaScaleProtocolAdapter, felicitaFrame)))
    }

    @Test fun freshDiFluidEeReadingDoesNotNeedBookooTimestamp() {
        assertNull(block(observation(DiFluidScaleProtocolAdapter, diFluidFrame)))
    }

    @Test fun freshDiFluidTiReadingDoesNotNeedBookooTimestamp() {
        val sample = observation(DiFluidTiScaleProtocolAdapter, diFluidFrame)
        assertNull(block(sample))
        // prepareBrew uses the string facade; startShot uses the structured result above.
        assertNull(gate.startBlock(weighted, DeviceState.READY, idle, 2000, DeviceState.READY,
            null, 2000, ExtractionState.IDLE, validated = true, scaleObservation = sample))
    }

    @Test fun bookooAlsoUsesItsGenericObservationTime() {
        val bytes = ByteArray(20).apply {
            this[0] = 3; this[1] = 11; this[6] = 43; this[8] = 0x1D; this[9] = 0xB0.toByte(); this[10] = 43
            this[19] = take(19).fold(0) { sum, byte -> sum xor (byte.toInt() and 255) }.toByte()
        }
        assertNull(block(observation(BookooScaleProtocolAdapter, bytes), legacyAt = 0))
    }

    @Test fun freshLegacyTimestampCannotMaskStaleFutureOrInvalidObservation() {
        val sample = observation(DiFluidTiScaleProtocolAdapter, diFluidFrame)
        assertNull(block(sample, now = 2500)) // The original 1500 ms boundary is inclusive.
        assertEquals(R.string.start_block_weight_not_fresh, block(sample, legacyAt = 2501, now = 2501))
        for (at in listOf(-1L, 3000L)) {
            assertEquals(R.string.start_block_weight_not_fresh,
                block(sample.copy(receivedAtMs = at), legacyAt = 2500, now = 2500))
        }
        for (grams in listOf(-50_001, 600_001)) {
            assertEquals(R.string.start_block_weight_not_fresh,
                block(sample.copy(hundredthsGram = grams), legacyAt = 2000))
        }
        assertEquals(R.string.scale_control_unverified, block(null, legacyAt = 2000))
    }

    @Test fun genericTimestampDoesNotGrantMissingEvidenceOrCapabilities() {
        val sample = observation(FelicitaScaleProtocolAdapter, felicitaFrame)
        for (evidence in listOf(ScaleEvidence.LIVE_READ_ONLY, ScaleEvidence.OFFLINE_CANDIDATE)) {
            assertEquals(R.string.scale_control_unverified,
                block(sample.copy(evidence = evidence), legacyAt = 2000))
        }
        assertEquals(R.string.scale_control_unverified, block(sample.copy(
            capabilities = ScaleCapabilities(tare = true))))
        assertEquals(R.string.scale_control_unverified, block(sample.copy(
            capabilities = sample.capabilities.copy(weight = false))))
        assertEquals(R.string.scale_control_unverified, block(sample.copy(
            capabilities = sample.capabilities.copy(tare = false))))
    }

    @Test fun disconnectedScaleStillBlocksButZeroTargetDoesNotNeedScaleEvidence() {
        val sample = observation(DiFluidTiScaleProtocolAdapter, diFluidFrame)
        assertEquals(R.string.start_block_scale_not_ready, block(sample, scale = DeviceState.DISCONNECTED))
        val flow = CurveCatalog.profiles[1]
        assertEquals(0, flow.targetHundredthsGram)
        assertNull(block(null, scale = DeviceState.DISCONNECTED, profile = flow))
    }

    @Test fun mockKeepsItsExistingLegacyTimestampAndEvidenceBypass() {
        val mock = ShotGate(DefaultStringResources::resolve, mockMode = true)
        val sample = observation(DiFluidTiScaleProtocolAdapter, diFluidFrame)
        assertNull(block(null, legacyAt = 2000, gate = mock))
        assertNull(block(sample.copy(receivedAtMs = -1, evidence = ScaleEvidence.LIVE_READ_ONLY),
            legacyAt = 2000, gate = mock))
        assertEquals(R.string.start_block_weight_not_fresh, block(sample, gate = mock))
    }
}
