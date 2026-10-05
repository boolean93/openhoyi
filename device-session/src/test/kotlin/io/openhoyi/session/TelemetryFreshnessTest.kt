package io.openhoyi.session

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.IdleTelemetry
import org.junit.Assert.*
import org.junit.Test

class TelemetryFreshnessTest {
    private val idle = IdleTelemetry(9200, 12000, 0, 0, 0, 0, 0, 0, ByteFrame(byteArrayOf()))
    @Test fun negativeDomainAndOverflowCannotQualifySamples() {
        for ((at, now) in listOf(-1L to 0L, -1L to -1L, Long.MIN_VALUE to Long.MAX_VALUE))
            assertFalse("$at/$now", TelemetryFreshness.isFresh(at, now))
        assertFalse(TelemetryFreshness.isFresh(0, 0, -1))
    }
    @Test fun inclusiveBoundaryAndLargeValidClocksStayUnchanged() {
        assertTrue(TelemetryFreshness.isFresh(0, 0))
        assertTrue(TelemetryFreshness.isFresh(0, 1500))
        assertFalse(TelemetryFreshness.isFresh(0, 1501))
        assertFalse(TelemetryFreshness.isFresh(null, 1000))
        assertFalse(TelemetryFreshness.isFresh(1001, 1000))
        assertTrue(TelemetryFreshness.isFresh(Long.MAX_VALUE - 1500, Long.MAX_VALUE))
        assertFalse(TelemetryFreshness.isFresh(Long.MAX_VALUE - 1501, Long.MAX_VALUE))
        assertTrue(TelemetryFreshness.isFresh(0, 180000, SettingsFreshness.MAX_AGE_MS))
        assertFalse(TelemetryFreshness.isFresh(0, 180001, SettingsFreshness.MAX_AGE_MS))
    }
    @Test fun cancellationRejectsNegativeAndOverflowWithoutChangingPriority() {
        for ((at, now) in listOf(-1L to 0L, Long.MIN_VALUE to Long.MAX_VALUE)) {
            assertEquals(PreheatGate.CancelBlock.IDLE_STALE,
                PreheatGate.cancelBlock(DeviceState.READY, idle, at, now, ExtractionState.IDLE, false))
            assertEquals(PreheatGate.CancelBlock.EXTRACTION_UNSETTLED,
                PreheatGate.cancelBlock(DeviceState.READY, idle, at, now, ExtractionState.RUNNING, false))
        }
    }
    @Test fun extractionRejectsBothClockSourcesAndKeepsNoWeightMode() {
        for ((at, now) in listOf(-1L to 0L, Long.MIN_VALUE to Long.MAX_VALUE)) {
            assertEquals(ExtractionStartGate.Block.IDLE_NOT_FRESH,
                ExtractionStartGate.block(2700, true, DeviceState.READY, idle, at, DeviceState.READY, now, now, ExtractionState.IDLE))
            assertEquals(ExtractionStartGate.Block.WEIGHT_NOT_FRESH,
                ExtractionStartGate.block(2700, true, DeviceState.READY, idle, now, DeviceState.READY, at, now, ExtractionState.IDLE))
            assertNull(ExtractionStartGate.block(0, true, DeviceState.READY, idle, now, DeviceState.DISCONNECTED, at, now, ExtractionState.IDLE))
        }
    }
}
