package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class MachineServiceWriterFenceTest {
    @Test fun uncertainCloseSticksAcrossSameInstanceRestartAndRetirement() {
        val fence=MachineServiceWriterFence()
        assertTrue(fence.mayStartOrDispatch)
        fence.beginDrain()
        assertFalse(fence.mayStartOrDispatch)
        fence.finishDrain(safe=false)
        assertFalse(fence.mayStartOrDispatch)
        assertFalse(fence.mayReleaseWriter)
        fence.beginDrain(retire=true)
        fence.finishDrain(safe=true)
        assertFalse(fence.mayStartOrDispatch)
        assertFalse(fence.mayReleaseWriter)
    }

    @Test fun safeTemporaryDrainCanRestartButFinalDrainReleasesOnlyAfterSettled() {
        val fence=MachineServiceWriterFence()
        fence.beginDrain()
        assertFalse(fence.mayStartOrDispatch)
        fence.finishDrain(safe=true)
        assertTrue(fence.mayStartOrDispatch)
        fence.beginDrain(retire=true)
        fence.beginDrain() // Native close nested inside final lifecycle.
        assertFalse(fence.mayStartOrDispatch)
        assertFalse(fence.mayReleaseWriter)
        fence.finishDrain(safe=true)
        assertFalse(fence.mayStartOrDispatch)
        assertTrue(fence.mayReleaseWriter)
    }
}
