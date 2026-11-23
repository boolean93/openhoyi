package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ManualShotPresentationGateTest {
    @Test fun foregroundClaimIsSharedAcrossHosts() {
        val gate = ManualShotPresentationGate()
        gate.started("cup-1")
        assertFalse(gate.claim("cup-1", false))
        // Eligibility can be checked while waiting for an existing service; it does not consume.
        assertTrue(gate.eligible("cup-1", true))
        assertTrue(gate.claim("cup-1", true))
        assertFalse(gate.claim("cup-1", true))
        gate.ended()
        assertEquals(ManualShotPresentationGate.Outcome.ENDED, gate.terminalFor("cup-1")?.outcome)
        assertFalse(gate.claim("cup-1", true))
        gate.started("cup-2")
        assertTrue(gate.claim("cup-2", true))
    }

    @Test fun provisionalObservationCanRouteImmediatelyWithoutBecomingACompletedCup() {
        val gate = ManualShotPresentationGate()
        gate.started("short")
        assertTrue(gate.claim("short", true))
        assertFalse(gate.claim("short", true))
        gate.discarded()
        assertEquals(ManualShotPresentationGate.Outcome.DISCARDED, gate.terminalFor("short")?.outcome)
        assertFalse(gate.eligible("short", true))
        gate.started("next")
        assertTrue(gate.eligible("next", true))
    }

    @Test fun offeredWindowCanClaimOnlyCurrentObservationAndCannotLaunchTwice() {
        val gate = ManualShotPresentationGate()
        gate.started("shot-1")
        assertFalse(gate.offerWindow("shot-1",false))
        assertTrue(gate.offerWindow("shot-1",true))
        assertFalse(gate.acceptWindow("other",true))
        assertFalse(gate.acceptWindow("shot-1",false))
        assertTrue(gate.acceptWindow("shot-1",true))
        assertFalse(gate.offerWindow("shot-1",true))
        gate.discarded()
        gate.started("shot-2")
        assertTrue(gate.offerWindow("shot-2",true))
        gate.dropWindow("shot-2")
        assertFalse(gate.offerWindow("shot-2",true))
        assertTrue(gate.claim("shot-2",true)) // A visible Main shell can still render inline.
        gate.discarded()
        assertFalse(gate.acceptWindow("shot-2",true))
    }

    @Test fun disconnectedCupRemainsUnknownAndCannotClaimUntilIdleAndNextStart() {
        val gate = ManualShotPresentationGate()
        gate.started("cup-1")
        gate.disconnected()
        assertNull(gate.activeId)
        assertEquals(ManualShotPresentationGate.Outcome.UNKNOWN, gate.terminalFor("cup-1")?.outcome)
        gate.ended() // Provisional discard or shutdown must not turn unknown into "ended".
        assertEquals(ManualShotPresentationGate.Outcome.UNKNOWN, gate.terminalFor("cup-1")?.outcome)
        gate.started("unconfirmed-tail")
        assertFalse(gate.claim("unconfirmed-tail", true))
        gate.observedIdle()
        gate.started("cup-2")
        assertTrue(gate.claim("cup-2", true))
    }
}
