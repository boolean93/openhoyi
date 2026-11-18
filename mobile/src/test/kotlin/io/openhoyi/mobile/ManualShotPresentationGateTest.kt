package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ManualShotPresentationGateTest {
    @Test fun oneClaimSharedByComparisonAndMainUntilNextCup() {
        val gate = ManualShotPresentationGate()
        gate.started("cup-1")
        assertTrue(gate.claim(isResumed = true)) // Comparison routes to Main.
        assertFalse(gate.claim(isResumed = true)) // Main must not present it again.
        assertFalse(gate.claim(isResumed = true)) // Returning/recreating must not re-route.
        gate.ended()
        gate.started("cup-2")
        assertTrue(gate.claim(isResumed = true))
    }

    @Test fun onlyResumedHostClaimsAndEndedCupIsNotQueued() {
        val gate = ManualShotPresentationGate()
        gate.started("hidden-cup")
        assertFalse(gate.claim(isResumed = false))
        gate.ended()
        assertFalse(gate.claim(isResumed = true))
        gate.started("still-running")
        assertFalse(gate.claim(isResumed = false))
        assertTrue(gate.claim(isResumed = true))
    }

    @Test fun interruptedManualCupCannotClaimNavigationUntilRealIdleAndNextStart() {
        val gate = ManualShotPresentationGate()
        gate.started("cup-1")
        gate.disconnected()
        assertFalse(gate.claim(isResumed = true))
        gate.started("unconfirmed-fragment")
        assertFalse(gate.claim(isResumed = true))
        gate.ended()
        gate.observedIdle()
        gate.started("cup-2")
        assertTrue(gate.claim(isResumed = true))
    }
}
