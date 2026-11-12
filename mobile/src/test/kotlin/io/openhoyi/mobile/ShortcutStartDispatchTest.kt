package io.openhoyi.mobile

import io.openhoyi.protocol.StartParameters
import io.openhoyi.protocol.ScaleObservationDecoder
import io.openhoyi.session.*
import org.junit.Assert.*
import org.junit.Test

/** Real extraction controller; only transport and monotonic time are fake. No device or history. */
class ShortcutStartDispatchTest {
    private class Coffee : CoffeeControl {
        override val ready = true
        override fun prepareStart(parameters: StartParameters) = true
        override fun startConditionsValid(parameters: StartParameters) = true
        var sent = 0
        var stops = 0
        var dispatch: (() -> Unit)? = null
        var completion: ((OperationResult) -> Unit)? = null
        override fun start(parameters: StartParameters, beforeDispatch: () -> Boolean, done: (OperationResult) -> Unit) {
            completion = done
            dispatch = { if (beforeDispatch()) sent++ else done(OperationResult.Cancelled("revoked")) }
        }
        override fun stop(done: (OperationResult) -> Unit) { stops++; done(OperationResult.Success()) }
    }
    private class Scale : ScaleControl {
        override val ready = true
        override val startAllowed = true
        override val capabilities = ScaleObservationDecoder.bookooCapabilities
        var tares = 0
        override fun tare(beforeDispatch: () -> Boolean, done: (OperationResult) -> Unit) {
            if (beforeDispatch()) { tares++; done(OperationResult.Success()) }
            else done(OperationResult.Cancelled("revoked"))
        }
    }
    private class Fixture(val target: Int = 0) {
        var now = 0L
        var valid = true
        val coffee = Coffee()
        val scale = Scale()
        val permit = ShortcutStartPermit(now, { now }, { valid })
        val controller = ExtractionController(coffee, scale, { now })
        fun start() {
            assertTrue(permit.claim())
            controller.weight(WeightReading(0, now))
            assertTrue(controller.start(CurveCatalog.profiles[1].parameters, target, 0, permit::allows))
        }
    }

    @Test fun revokedDuringTareAbortsWithoutMachineStartOrStopAndLateZeroCannotReplay() {
        val f = Fixture(3400); f.start()
        assertEquals(1, f.scale.tares); assertTrue(f.controller.preparingScale)
        f.permit.revoke(); f.controller.tick()
        assertEquals(ExtractionState.IDLE, f.controller.state)
        assertFalse(f.controller.preparingScale)
        f.now = 100; f.controller.weight(WeightReading(0, f.now))
        assertEquals(0, f.coffee.sent); assertEquals(0, f.coffee.stops)
        assertNull(f.coffee.dispatch)
    }

    @Test fun heldDispatchCannotStartAfterPauseAndResumeOrDeadline() {
        for (expired in listOf(false, true)) {
            val f = Fixture(); f.start()
            if (expired) f.now = 5_000 else { f.permit.revoke(); f.valid = true }
            f.coffee.dispatch!!()
            assertEquals(0, f.coffee.sent)
            assertEquals(ExtractionState.IDLE, f.controller.state)
            assertFalse(f.permit.claim())
        }
    }

    @Test fun changedConfigurationCannotReviveQueuedStartEvenWhenOriginalValueReturns() {
        val epoch = ShortcutContentRevision("original")
        val expected = epoch.revision
        val coffee = Coffee(); val scale = Scale()
        val permit = ShortcutStartPermit(0, { 0 }, { epoch.revision == expected })
        val controller = ExtractionController(coffee, scale, { 0 })
        assertTrue(permit.claim())
        assertTrue(controller.start(CurveCatalog.profiles[1].parameters, 0, 0, permit::allows))
        epoch.observe("changed"); epoch.observe("original")
        coffee.dispatch!!()
        assertEquals(0, coffee.sent); assertEquals(ExtractionState.IDLE, controller.state)
    }

    @Test fun cafeModeCanStartColdButStudioMustReceiveANewTapAfterReachingTarget() {
        val profile = CurveCatalog.profiles[1]
        val gate = StudioStartGate(DefaultStringResources::resolve)
        val settings = io.openhoyi.protocol.Settings(1,1,3,0,92,0,125,15,70,0,0,io.openhoyi.protocol.ByteFrame(byteArrayOf()))
        val preparation = BrewPreparation()
        var temperature = 7500
        for (studio in listOf(false, true)) {
            val mode = settings.copy(flags = if (studio) 0x04 else 0)
            val permit = ShortcutStartPermit(0, { 0 }, { gate.block(mode, temperature, profile, preparation) == null })
            val coffee = Coffee(); val scale = Scale()
            val controller = ExtractionController(coffee, scale, { 0 })
            val claimed = permit.claim()
            assertEquals(!studio, claimed)
            if (claimed) {
                assertTrue(controller.start(profile.parameters, 0, 0, permit::allows))
                coffee.dispatch!!(); assertEquals(1, coffee.sent)
            } else {
                temperature = 9200
                assertFalse(permit.claim()); assertNull(coffee.dispatch); assertEquals(0, coffee.sent)
                val newTap = ShortcutStartPermit(0, { 0 }, { gate.block(mode, temperature, profile, preparation) == null })
                assertTrue(newTap.claim())
            }
        }
    }

    @Test fun offThenOnThroughTheOtherSwitchCannotReviveAQueuedStart() {
        var stored = ShortcutStartPolicy.Preference(true)
        val safety = ShortcutPreferenceSafety()
        val home = ShortcutStartPreferences({ stored }, { stored = it; true }, safety)
        val settings = ShortcutStartPreferences({ stored }, { stored = it; true }, safety)
        val expectedRevision = home.revision
        val coffee = Coffee(); val scale = Scale()
        val permit = ShortcutStartPermit(0, { 0 }, { home.read().startsOnTap && home.revision == expectedRevision })
        val stopRootObserver = home.observe(permit::revoke)
        val controller = ExtractionController(coffee, scale, { 0 })
        assertTrue(permit.claim())
        assertTrue(controller.start(CurveCatalog.profiles[1].parameters, 0, 0, permit::allows))
        assertTrue(settings.choose(false)); assertTrue(home.choose(true))
        assertTrue(home.read().startsOnTap)
        coffee.dispatch!!()
        assertEquals(0, coffee.sent); assertEquals(ExtractionState.IDLE, controller.state)
        stopRootObserver()
    }

    @Test fun revocationAfterSubmissionDoesNotInventCancellationOrClearUnknownOutcome() {
        val f = Fixture(); f.start(); f.coffee.dispatch!!()
        assertEquals(1, f.coffee.sent)
        f.permit.revoke()
        f.coffee.completion!!(OperationResult.Unknown("sent without acknowledgement"))
        assertEquals(ExtractionState.OUTCOME_UNKNOWN, f.controller.state)
        assertEquals(0, f.coffee.stops)
    }

    @Test fun normalPermitKeepsExistingTareBeforeDispatchAndCanStartOnlyOnce() {
        val f = Fixture(3400); f.start()
        assertEquals(0, f.coffee.sent)
        f.now = 100; f.controller.weight(WeightReading(0, f.now))
        f.coffee.dispatch!!(); f.coffee.completion!!(OperationResult.Success())
        assertEquals(1, f.coffee.sent); assertEquals(1, f.scale.tares)
        assertEquals(ExtractionState.RUNNING, f.controller.state)
        assertFalse(f.permit.claim())
    }
}
