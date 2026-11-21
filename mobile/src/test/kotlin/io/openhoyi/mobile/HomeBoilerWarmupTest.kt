package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBoilerWarmupTest {
    @Test fun arcStartsAtThirtyAndStopsAtActualBoilerTarget() {
        for (target in listOf(92f, 120f)) {
            for (reading in listOf(null, Float.NaN, 20f, 30f))
                assertEquals(0f, homeBoilerArcFraction(reading, target), 0f)
            assertEquals(.5f, homeBoilerArcFraction((30f + target) / 2, target), 0f)
            for (reading in listOf(target, target + 10f))
                assertEquals(1f, homeBoilerArcFraction(reading, target), 0f)
        }
        for (target in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, 30f, 15f))
            assertEquals(0f, homeBoilerArcFraction(100f, target), 0f)
    }

    @Test fun onlyConsecutiveValidRisingSamplesWithUnchangedSourceAndTargetRamp() {
        val connected = Any()
        fun eligible(next: Float?, target: Float? = 92f, session: Any? = connected) =
            homeBoilerShouldAnimate(25f, next, 92f, target, connected, session)
        assertTrue(eligible(49f))
        for (reading in listOf(null, Float.NaN, 25f, 24f, 655f)) assertFalse(eligible(reading))
        assertFalse(eligible(49f, target = 94f))
        assertFalse(eligible(49f, session = null))
        assertFalse(eligible(49f, session = Any()))
        assertFalse(homeBoilerShouldAnimate(null, 49f, 92f, 92f, connected, connected))
        assertFalse(homeBoilerShouldAnimate(25f, 49f, 30f, 30f, connected, connected))
    }
}
