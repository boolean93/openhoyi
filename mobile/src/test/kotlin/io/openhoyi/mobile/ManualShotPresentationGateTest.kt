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
        assertEquals(ManualShotPresentationGate.Outcome.ENDED, gate.outcome("cup-1"))
        assertFalse(gate.claim("cup-1", true))
        gate.started("cup-2")
        assertTrue(gate.claim("cup-2", true))
    }

    @Test fun disconnectedCupRemainsUnknownAndCannotClaimUntilIdleAndNextStart() {
        val gate = ManualShotPresentationGate()
        gate.started("cup-1")
        gate.disconnected()
        assertNull(gate.activeId)
        assertEquals(ManualShotPresentationGate.Outcome.UNKNOWN, gate.outcome("cup-1"))
        gate.ended() // Provisional discard or shutdown must not turn unknown into "ended".
        assertEquals(ManualShotPresentationGate.Outcome.UNKNOWN, gate.outcome("cup-1"))
        gate.started("unconfirmed-tail")
        assertFalse(gate.claim("unconfirmed-tail", true))
        gate.observedIdle()
        gate.started("cup-2")
        assertTrue(gate.claim("cup-2", true))
    }
}
