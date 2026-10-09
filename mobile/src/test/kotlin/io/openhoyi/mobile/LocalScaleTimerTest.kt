package io.openhoyi.mobile

import org.junit.Test
import org.junit.Assert.*

class LocalScaleTimerTest {
    @Test fun rotationRestoresRunningTimerUsingMonotonicOrigin() {
        val timer=LocalScaleTimer();timer.start(1000)
        assertEquals(2500L,timer.elapsedMs(3500))
        val restored=LocalScaleTimer(timer.snapshot())
        assertEquals(6000L,restored.elapsedMs(7000));restored.pause(8000)
        assertEquals(7000L,restored.elapsedMs(18000));restored.start(20000)
        assertEquals(9000L,restored.elapsedMs(22000));restored.reset();assertEquals(0L,restored.elapsedMs(25000))
    }
    @Test fun backwardClockInvalidatesRunningOriginWithoutNegativeTime() {
        val timer=LocalScaleTimer();timer.start(9000)
        val restored=LocalScaleTimer(timer.snapshot())
        assertEquals(0L,restored.elapsedMs(10));assertFalse(restored.running)
    }
    @Test fun repeatedStartDoesNotResetAndPauseCanBeRestored() {
        val timer=LocalScaleTimer();timer.start(1000);timer.start(2000);timer.pause(3000)
        val restored=LocalScaleTimer(timer.snapshot());assertFalse(restored.running)
        assertEquals(2000L,restored.elapsedMs(20000))
    }
}
