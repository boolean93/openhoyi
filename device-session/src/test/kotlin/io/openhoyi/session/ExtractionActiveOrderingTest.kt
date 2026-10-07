package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

class ExtractionActiveOrderingTest {
    private val profile = StartParameters(true, true, 3, 7, 92, 136, false, 0, 20, 35, 18, 0, 150, 5, 400, 130, 0)
    private val raw = ByteFrame(byteArrayOf())
    private val active = ExtractionTelemetry(8, 1, 20, 1, 9200, 20, 64, 0, raw)
    private val idle = IdleTelemetry(9200, 12000, 0, 0, 0, 0, 0, 0, raw)
    private class Fixture {
        var now = 0L
        var ready = true
        var starts = 0
        var stops = 0
        val coffee = object : CoffeeControl {
            override val ready get() = this@Fixture.ready
            override fun prepareStart(parameters: StartParameters) = ready
            override fun startConditionsValid(parameters: StartParameters) = ready
            override fun start(parameters:StartParameters,beforeDispatch:()->Boolean,done:(OperationResult)->Unit){
                if(!runCatching(beforeDispatch).getOrDefault(false)){done(OperationResult.Failed("caller rejected"));return}

                starts++; done(OperationResult.Success())
            }
            override fun stop(done: (OperationResult) -> Unit) {
                stops++; done(OperationResult.Success())
            }
        }
        val scale = object : ScaleControl {
            override val ready = false
            override val startAllowed = true
            override fun tare(beforeDispatch: () -> Boolean, done: (OperationResult) -> Unit) = error("No tare expected")
        }
        val controller = ExtractionController(coffee, scale) { now }
    }
    private fun olderActiveCannotAdvanceCompletion(state: ExtractionState) {
        for (age in listOf(1000L, 1500L)) {
            val f = Fixture()
            val c = f.controller
            assertTrue(c.start(profile, 0, 0))
            f.now = 2000
            c.machineFrame(active, f.now)
            when (state) {
                ExtractionState.STOP_REQUESTED -> c.manualStop()
                ExtractionState.OUTCOME_UNKNOWN -> {
                    f.ready = false; c.tick(); f.ready = true
                }
                ExtractionState.RUNNING -> Unit
                else -> error("Unsupported test state")
            }
            assertEquals(state, c.state)
            // A delayed frame is still individually fresh, but cannot replace newer activity.
            c.machineFrame(active, f.now - age)
            f.now = 4800
            c.machineFrame(idle, f.now)
            assertEquals("$state age=$age: 2800ms remains inclusive", state, c.state)
            assertFalse(c.start(profile, 0, 0))
            assertEquals(1, f.starts)
            f.now = 4801
            c.machineFrame(idle, f.now)
            assertEquals(ExtractionState.ENDED_OBSERVED, c.state)
            assertEquals(if (state == ExtractionState.STOP_REQUESTED) 1 else 0, f.stops)
            assertTrue(c.start(profile, 0, 0))
            assertEquals(2, f.starts)
            assertEquals(ExtractionState.RUNNING, c.state)
        }
    }
    @Test fun runningKeepsNewestActiveReceiptBeforeAllowingNextShot() = olderActiveCannotAdvanceCompletion(ExtractionState.RUNNING)
    @Test fun pendingManualStopKeepsNewestActiveReceipt() = olderActiveCannotAdvanceCompletion(ExtractionState.STOP_REQUESTED)
    @Test fun uncertainDisconnectKeepsNewestActiveReceipt() = olderActiveCannotAdvanceCompletion(ExtractionState.OUTCOME_UNKNOWN)
    @Test fun sameTimestampAndNewerActiveEvidenceKeepOriginalBoundary() {
        val f = Fixture()
        val c = f.controller
        assertTrue(c.start(profile, 0, 0))
        f.now = 1000; c.machineFrame(active, f.now); c.machineFrame(active, f.now)
        f.now = 2000; c.machineFrame(active, f.now)
        f.now = 3801; c.machineFrame(idle, f.now)
        assertEquals(ExtractionState.RUNNING, c.state)
        f.now = 4800; c.machineFrame(idle, f.now)
        assertEquals(ExtractionState.RUNNING, c.state)
        f.now = 4801; c.machineFrame(idle, f.now)
        assertEquals(ExtractionState.ENDED_OBSERVED, c.state)
        assertEquals(1, f.starts)
        assertEquals(0, f.stops)
    }
}
