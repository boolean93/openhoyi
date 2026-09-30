package io.openhoyi.session

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.Settings
import org.junit.Assert.*
import org.junit.Test

class PreheatGateTest {
    private val raw = ByteFrame(byteArrayOf())
    private val settings = Settings(1, 1, 3, 4, 92, 0, 125, 15, 70, 0, 0, raw)
    private val idle = IdleTelemetry(9200, 12000, 10, 10, 0, 0, 0, 0, raw)

    @Test fun studioRequiresMatchingReadyPreparationAndCurrentTemperature() {
        val preparation = BrewPreparation()
        fun block(temp: Int?, profile: String = "profile", target: Int = 92,
            machine: Settings? = settings) =
            PreheatGate.startBlock(machine, temp, profile, target, preparation)
        assertEquals(PreheatGate.StartBlock.SETTINGS_MISSING, block(9200, machine = null))
        assertEquals(PreheatGate.StartBlock.TEMPERATURE_NOT_READY, block(null))
        assertEquals(PreheatGate.StartBlock.TEMPERATURE_NOT_READY, block(9099))
        assertNull(block(9100))
        assertNull(block(9300))
        assertEquals(PreheatGate.StartBlock.TEMPERATURE_NOT_READY, block(9301))
        assertNull(block(null, machine = settings.copy(flags = 0)))
        val token = requireNotNull(preparation.begin("profile", 92))
        assertEquals(PreheatGate.StartBlock.PREPARATION_MISMATCH, block(9200))
        preparation.written(token, OperationResult.Success(), 1)
        assertEquals(PreheatGate.StartBlock.PREPARATION_MISMATCH, block(9200))
        preparation.observe(2, 9200)
        assertNull(block(9200))
        assertEquals(PreheatGate.StartBlock.PREPARATION_MISMATCH, block(9200, profile = "other"))
        assertEquals(PreheatGate.StartBlock.PREPARATION_MISMATCH, block(9200, target = 93))
        assertEquals(PreheatGate.StartBlock.MODE_CHANGED, block(9200, machine = settings.copy(flags = 0)))
        preparation.disconnected()
        assertEquals(PreheatGate.StartBlock.PREPARATION_MISMATCH, block(9200))
    }

    @Test fun cancellationRequiresFreshAwakeIdleAcrossEveryConnectionState() {
        fun block(coffee: DeviceState = DeviceState.READY,
            frame: io.openhoyi.protocol.HoyiMessage? = idle, at: Long? = 1000, now: Long = 1000) =
            PreheatGate.cancelBlock(coffee, frame, at, now, ExtractionState.IDLE, false)
        for (state in DeviceState.entries) {
            if (state == DeviceState.READY) assertNull(block(coffee = state))
            else assertEquals(PreheatGate.CancelBlock.COFFEE_NOT_READY, block(coffee = state))
        }
        assertEquals(PreheatGate.CancelBlock.IDLE_MISSING, block(frame = null))
        assertEquals(PreheatGate.CancelBlock.IDLE_MISSING,
            block(frame = ExtractionTelemetry(1, 1, 1, 1, 9200, 1, 64, 0, raw)))
        assertEquals(PreheatGate.CancelBlock.IDLE_STALE, block(at = null))
        assertEquals(PreheatGate.CancelBlock.IDLE_STALE, block(at = 1001))
        assertNull(block(now = 2500))
        assertEquals(PreheatGate.CancelBlock.IDLE_STALE, block(now = 2501))
        for (sleep in listOf(1, 2, 255))
            assertEquals(PreheatGate.CancelBlock.NOT_AWAKE, block(frame = idle.copy(sleepStateRaw = sleep)))
    }

    @Test fun unsettledShotTakesPriorityOverConnectionAndTelemetry() {
        for (state in ExtractionState.entries) {
            val result = PreheatGate.cancelBlock(DeviceState.READY, idle, 1000, 1000, state, false)
            if (state in setOf(ExtractionState.STARTING, ExtractionState.RUNNING,
                    ExtractionState.STOP_REQUESTED, ExtractionState.OUTCOME_UNKNOWN))
                assertEquals(PreheatGate.CancelBlock.EXTRACTION_UNSETTLED, result)
            else assertNull(result)
            assertEquals(PreheatGate.CancelBlock.EXTRACTION_UNSETTLED,
                PreheatGate.cancelBlock(DeviceState.DISCONNECTED, null, null, 1000, state, true))
        }
    }
}
