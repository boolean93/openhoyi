package io.openhoyi.mobile

import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockDeviceRuntimeTest {
    @Test fun idleShotAndAutomaticEndUseOnlySyntheticSamples() {
        val mock = MockDeviceRuntime()
        val idle = mock.sample(1_000)
        assertEquals(DeviceState.READY, idle.coffeeState)
        assertEquals(DeviceState.READY, idle.scaleState)
        assertTrue(idle.coffee is IdleTelemetry)
        assertEquals(0, idle.weight?.weightHundredthsGram)
        val schedule = WeeklySleepSchedule.fromReadback(idle.sleepFirst, idle.sleepSecond)
        assertEquals(7, schedule?.days?.size)
        assertEquals(5, schedule?.days?.count { it.enabled })

        mock.start(2_000)
        val running = mock.sample(7_000)
        assertEquals(ExtractionState.RUNNING, mock.shotState)
        assertTrue(running.coffee is ExtractionTelemetry)
        assertEquals(350, running.weight?.weightHundredthsGram)

        val ended = mock.sample(34_000)
        assertEquals(ExtractionState.ENDED_OBSERVED, mock.shotState)
        assertTrue(ended.coffee is IdleTelemetry)
    }
}
