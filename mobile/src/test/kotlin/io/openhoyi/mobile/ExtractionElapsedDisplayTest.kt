package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractionElapsedDisplayTest {
    private val raw = ByteFrame(byteArrayOf())
    private val idle = IdleTelemetry(9200, 12000, 10, 10, 0, 0, 0, 0, raw)
    private fun extraction(seconds: Int, bits: Int = 64) =
        ExtractionTelemetry(1, seconds, 90, 300, 9200, 20, bits, 0, raw)

    private fun ShotSeries.display(
        latest: HoyiMessage? = idle, state: ExtractionState = ExtractionState.RUNNING,
        connection: DeviceState = DeviceState.READY, at: Long = 4_000, now: Long = at,
        preparingNextShot: Boolean = false, appOutcomeWasUnknown: Boolean = false,
    ) = displayElapsedSeconds(latest, connection, at, now, state, preparingNextShot, appOutcomeWasUnknown)

    @Test fun closingAndIdleConfirmationKeepLastMachineTimeThenCompletionSurvivesStaleIdleAndReentry() {
        val cup = ShotSeries()
        cup.begin("cup", 100)
        cup.machine(extraction(14), 1_000, null, null)
        assertEquals(14, cup.display(extraction(14), at = 1_000))
        cup.machine(extraction(0, bits = 0), 1_100, null, null)
        assertEquals(14, cup.display(extraction(0, bits = 0), ExtractionState.STOP_REQUESTED))
        assertEquals(14, cup.display(state = ExtractionState.STOP_REQUESTED))
        assertNull(cup.display(state = ExtractionState.STOP_REQUESTED, now = 5_501))
        // Link loss after confirmed idle but before the service watcher processes completion.
        cup.interruptElapsed(endAlreadyObserved = true)
        cup.finish(observedEnd = true)
        assertEquals(14, cup.display(state = ExtractionState.ENDED_OBSERVED))
        // A new view asks the same service-owned series; it needs no saved view state.
        assertEquals(14, cup.display(state = ExtractionState.ENDED_OBSERVED, now = 60_000))
        assertEquals(14, cup.display(latest = null, state = ExtractionState.ENDED_OBSERVED,
            connection = DeviceState.DISCONNECTED))
    }

    @Test fun interruptedCupDoesNotBecomeCompleteWhenIdleReconcilesTheController() {
        val cup = ShotSeries()
        cup.begin("interrupted", 100)
        cup.machine(extraction(14), 1_000, null, null)
        assertNull(cup.display(connection = DeviceState.DISCONNECTED))
        assertNull(cup.display(state = ExtractionState.OUTCOME_UNKNOWN))
        cup.interruptElapsed()
        assertNull(cup.display(state = ExtractionState.STOP_REQUESTED))
        cup.finish(observedEnd = true)
        assertNull(cup.display(state = ExtractionState.ENDED_OBSERVED))
        assertEquals(14, cup.observedMachineElapsedSeconds) // Historical evidence is unchanged.
    }

    @Test fun stickyAppUncertaintyPreemptsWatcherButDoesNotLeakIntoNextManualCup() {
        val cup = ShotSeries()
        cup.begin("app", 100)
        cup.machine(extraction(14), 1_000, null, null)
        // The UI first reads the controller after it already settled on a fresh idle.
        assertNull(cup.display(state = ExtractionState.ENDED_OBSERVED, appOutcomeWasUnknown = true))
        cup.finish(observedEnd = true, appOutcomeWasUnknown = true)
        assertNull(cup.display(state = ExtractionState.ENDED_OBSERVED))
        assertEquals(14, cup.observedMachineElapsedSeconds)
        cup.begin("manual", 8_000, "manual", passive = true)
        cup.machine(extraction(17), 9_000, null, null)
        // The App controller can keep the older shot's evidence while the passive detector runs.
        assertEquals(17, cup.display(state = ExtractionState.ENDED_OBSERVED,
            at = 9_000, appOutcomeWasUnknown = true))
        cup.finish(observedEnd = true, appOutcomeWasUnknown = true)
        assertEquals(17, cup.display(state = ExtractionState.ENDED_OBSERVED,
            at = 9_000, appOutcomeWasUnknown = true))
    }

    @Test fun waitCandidateAndNewCupNeverUseOldCupTime() {
        val cup = ShotSeries()
        cup.begin("previous", 100)
        cup.machine(extraction(14), 1_000, null, null)
        cup.finish(observedEnd = true)
        assertNull(cup.display(state = ExtractionState.ENDED_OBSERVED, preparingNextShot = true))
        assertNull(cup.display(extraction(0, bits = 96), ExtractionState.ENDED_OBSERVED))
        assertNull(cup.display(extraction(0), ExtractionState.ENDED_OBSERVED))
        cup.begin("next", 4_500)
        assertNull(cup.display(state = ExtractionState.STARTING, at = 4_500))
        cup.machine(extraction(0, bits = 96), 4_600, null, null)
        assertNull(cup.display(extraction(0, bits = 96), at = 4_600))
        cup.finish(observedEnd = true)
        assertNull(cup.display(state = ExtractionState.ENDED_OBSERVED))
    }

    @Test fun passiveHistoryRebindKeepsSameCupWhileShortFlushNeverKeepsATerminalTime() {
        val cup = ShotSeries()
        cup.begin("provisional", 100, "manual")
        cup.machine(extraction(14), 1_000, null, null)
        cup.rebind("history")
        assertEquals(14, cup.display(state = ExtractionState.IDLE))
        assertEquals("history", cup.finish(observedEnd = true)?.first)
        assertEquals(14, cup.display(state = ExtractionState.IDLE))
        cup.begin("flush", 5_000, "manual")
        cup.machine(extraction(2), 7_000, null, null)
        assertEquals(2, cup.display(state = ExtractionState.IDLE, at = 7_000))
        cup.finish(observedEnd = false)
        assertNull(cup.display(state = ExtractionState.IDLE, at = 9_000))
    }
}
