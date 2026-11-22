package io.openhoyi.mobile

import io.openhoyi.mobile.BoilerTargetSteps.Boiler
import io.openhoyi.mobile.BoilerTargetSteps.Feedback
import io.openhoyi.session.SettingsWriteTracker.State
import org.junit.Assert.*
import org.junit.Test

class BoilerTargetStepsTest {
    private val brew = Boiler.BREW
    private val steam = Boiler.STEAM
    private val q = BoilerTargetSteps().apply { sessionChanged("machine/generation-1") }

    @Test fun serializesOwnersAndPreservesRapidIntentEvenWithOldReadback() {
        assertTrue(q.step(brew, 1, 93))
        val first = q.next()!!
        assertEquals(94, first.value)
        assertTrue(q.step(brew, 1, 92))
        assertTrue(q.step(steam, -1, 120))
        assertTrue(q.step(brew, 1, 92))
        assertEquals(96, q.target(brew, 91))
        assertEquals(119, q.target(steam, 121))
        q.observe(first.boiler.change(first.value), State.WAITING_READBACK)
        assertNull(q.next())
        q.observe(first.boiler.change(first.value), State.CONFIRMED)
        val merged = q.next()!!
        assertEquals(BoilerTargetSteps.Request(brew, 96), merged)
        assertEquals(96, q.target(brew, 94))
        q.observe(merged.boiler.change(merged.value), State.CONFIRMED)
        assertEquals(BoilerTargetSteps.Request(steam, 119), q.next())
    }

    @Test fun failureUnknownOrSessionLossNeverReplaysUnsentWork() {
        for (state in listOf(State.FAILED, State.UNKNOWN, State.RECONCILED, State.WRITING)) {
            q.sessionChanged("session-$state")
            q.step(brew, 1, 93)
            val request = q.next()!!
            q.step(steam, 1, 120)
            if (state == State.WRITING) q.sessionChanged(null)
            else q.observe(request.boiler.change(request.value), state)
            assertFalse(q.active)
            assertNull(q.next())
            assertEquals(93, q.target(brew, 93))
            assertEquals(120, q.target(steam, 120))
            assertEquals(Feedback.NOT_SENT, q.feedback(steam))
            assertEquals(if (state == State.FAILED) Feedback.FAILED else Feedback.UNKNOWN, q.feedback(brew))
        }
    }

    @Test fun alternatingClicksKeepLatestProjectionAndDoNotTransferAcrossNewMachine() {
        assertTrue(q.step(brew, 1, 93))
        val first = q.next()!!
        assertTrue(q.step(brew, 1, 93))
        assertEquals(95, q.target(brew, 93))
        assertTrue(q.step(brew, -1, 93))
        assertEquals(94, q.target(brew, 93))
        assertTrue(q.step(steam, 1, 120))
        assertEquals(121, q.target(steam, 120))
        assertEquals(Feedback.QUEUED, q.feedback(steam))
        assertNull(q.next())
        q.observe(first.boiler.change(first.value), State.CONFIRMED)
        // The service drops a queued target that equals the already confirmed measured target.
        val noOp = q.next()!!
        assertEquals(first, noOp)
        q.observe(noOp.boiler.change(noOp.value), State.CONFIRMED)
        val second = q.next()!!
        assertEquals(BoilerTargetSteps.Request(steam, 121), second)
        assertTrue(q.step(brew, -1, 94))
        q.sessionChanged("other machine/generation-2")
        assertFalse(q.active)
        assertNull(q.next())
        assertEquals(80, q.target(brew, 80))
        assertEquals(Feedback.NOT_SENT, q.feedback(brew))
    }

    @Test fun protectsAnExistingLegacyObserverAndDoesNotKeepAStaleQueue() {
        assertFalse(q.acceptsAlongsideTemperature(State.WAITING_READBACK))
        assertTrue(q.acceptsAlongsideTemperature(State.CONFIRMED))
        assertTrue(q.step(brew, 1, 93))
        val issued = q.next()!!
        assertTrue(q.acceptsAlongsideTemperature(State.WAITING_READBACK))
        assertTrue(q.step(steam, -1, 120))
        q.observe(issued.boiler.change(issued.value), State.WAITING_READBACK)
        assertNull(q.next())
        q.observe(issued.boiler.change(issued.value), State.UNKNOWN)
        assertNull(q.next())
        q.sessionChanged("machine/generation-2")
        assertNull(q.next())
        assertTrue(q.step(steam, -1, 120))
        assertEquals(119, q.next()?.value)
    }

    @Test fun onlyOneDegreeInsideKnownRangesAndKnownSession() {
        assertFalse(q.step(steam, 1, null))
        assertFalse(q.step(steam, 1, 145))
        assertFalse(q.step(brew, -1, 75))
        assertFalse(q.step(brew, 2, 90))
        q.sessionChanged(null)
        assertFalse(q.step(brew, 1, 90))
    }
}
