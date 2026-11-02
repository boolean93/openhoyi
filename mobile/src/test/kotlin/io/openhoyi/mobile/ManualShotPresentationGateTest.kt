package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ManualShotPresentationGateTest {
    @Test fun repeatedUpdatesKeepOneActiveIdUntilEnd() {
        val gate=ManualShotPresentationGate()
        gate.started("cup-1");assertEquals("cup-1",gate.activeId)
        assertEquals("cup-1",gate.activeId)
        gate.ended();assertNull(gate.activeId)
        gate.started("cup-2");assertEquals("cup-2",gate.activeId)
    }
    @Test fun interruptedManualCupCannotClaimNavigationUntilRealIdleAndNextStart() {
        val gate=ManualShotPresentationGate()
        gate.started("cup-1");gate.disconnected();assertNull(gate.activeId)
        gate.started("unconfirmed-fragment");assertNull(gate.activeId)
        gate.ended();assertNull(gate.activeId)
        gate.observedIdle();gate.started("cup-2")
        assertEquals("cup-2",gate.activeId)
    }
}
