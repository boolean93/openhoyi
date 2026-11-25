package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetachedCoreClockTest {
    @Test fun exactStepRunsDriverTickThenItsDueOperationsAndLeavesFutureTimersPending() {
        val clock=DetachedCoreClock()
        val seen=mutableListOf<String>()
        clock.schedule(500){seen+="due@${clock.now}";clock.schedule(1){seen+="new@${clock.now}"}}
        clock.advance(499){seen+="tick@${clock.now}"}
        assertEquals(listOf("tick@509"),seen)
        clock.advance(1){seen+="tick@${clock.now}"}
        assertEquals(listOf("tick@509","tick@510","due@510"),seen)
        clock.advance(1){seen+="tick@${clock.now}"}
        assertEquals(listOf("tick@509","tick@510","due@510","tick@511","new@511"),seen)
        clock.close()
    }

    @Test fun fixtureBoundsRejectBackwardClockAndUnboundedSelfScheduling() {
        val clock=DetachedCoreClock()
        assertTrue(runCatching { clock.advance(-1) {} }.isFailure)
        assertTrue(runCatching { clock.advance(60_001) {} }.isFailure)
        fun perpetuate():Unit=clock.schedule(0,::perpetuate)
        perpetuate()
        assertTrue(runCatching { clock.advance(0) {} }.isFailure)
        clock.close()
    }
}
