package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.Settings
import org.junit.Assert.*
import org.junit.Test

class HomeBoilerDialHoldTest {
    private val hub = Any()
    private val identity = Triple(hub, "00:00:00:AB:CD:01", 1L)
    private val raw = ByteFrame(byteArrayOf())
    private val frame = IdleTelemetry(9140, 11980, 88, 9, 0, 0, 1, 0, raw)
    private val settings = Settings(1, 1, 1, 32, 92, 0, 120, 0, 80, 0, 0, raw)
    private val hold = HomeBoilerDialHold()

    private fun prime() = hold.resolve(identity, frame, 1000, settings, canHold = true, active = false)

    @Test fun longGapAndCompletedReadbackKeepTemperatureAndPressureUntilNewTelemetry() {
        val previous = prime()
        hold.arm(identity, target = true)
        assertEquals(previous, hold.resolve(identity, null, null, null, canHold = true, active = false, targetActive = true))
        // The readback is complete, but the telemetry has been missing for well over five seconds.
        val confirmed = settings.copy(flags = 16, brewTemperatureC = 93)
        val beforeIdle = hold.resolve(identity, null, null, confirmed, canHold = true, active = false)
        assertSame(frame, beforeIdle.frame)
        assertEquals(93, beforeIdle.brewTarget)
        assertEquals(120, beforeIdle.steamTarget)
        assertFalse(beforeIdle.settings!!.brewHeating)
        assertTrue(beforeIdle.settings.steamHeating)
        assertTrue(hold.targetDisplayGap)
        val newer = frame.copy(brewTemperatureHundredthsC = 9150, steamPressureTenthsBar = 12)
        val resumed = hold.resolve(identity, newer, 12_000, confirmed, canHold = true, active = false)
        assertSame(newer, resumed.frame)
        assertFalse(hold.targetDisplayGap)
        assertNull(hold.resolve(identity, null, null, confirmed, canHold = true, active = false).frame)
    }

    @Test fun telemetryCanResumeBeforeSettingsAndFailedWriteNeverProjectsRequestedValues() {
        prime()
        hold.arm(identity)
        val newer = frame.copy(steamTemperatureHundredthsC = 12004, brewPressureTenthsBar = 92)
        val first = hold.resolve(identity, newer, 8_000, null, canHold = true, active = true)
        assertSame(newer, first.frame)
        assertSame(settings, first.settings)
        assertFalse(hold.targetDisplayGap)
        val sameAfterFailure = hold.resolve(identity, newer, 8_000, null, canHold = true, active = false)
        assertEquals(92, sameAfterFailure.brewTarget)
        assertTrue(sameAfterFailure.settings!!.brewHeating)
        // A second telemetry pause while waiting for settings keeps the newer reading, not the original one.
        assertSame(newer, hold.resolve(identity, null, null, null, canHold = true, active = false).frame)
        val confirmed = settings.copy() // A fresh report of the same values still closes the gap.
        assertSame(confirmed, hold.resolve(identity, newer, 9_000, confirmed, canHold = true, active = false).settings)
        assertNull(hold.resolve(identity, newer, 9_100, null, canHold = true, active = false).settings)
    }

    @Test fun noHoldOutsideAnActionAndSwitchingSessionOrLeavingDiscardsTheCache() {
        prime()
        assertNull(hold.resolve(identity, null, null, null, canHold = true, active = false).frame)
        for (other in listOf(null, Triple(hub, "00:00:00:AB:CD:02", 1L), identity.copy(third = 2L), identity.copy(first = Any()))) {
            prime()
            hold.arm(identity)
            val absent = hold.resolve(other, null, null, null, canHold = true, active = true)
            assertNull(absent.frame)
            assertNull(absent.settings)
        }
        prime()
        hold.arm(identity)
        val sleeping = frame.copy(sleepStateRaw = 1)
        assertSame(sleeping, hold.resolve(identity, sleeping, 2000, settings, canHold = true, active = true).frame)
        assertNull(hold.resolve(identity, null, null, null, canHold = true, active = false).frame)
        prime()
        hold.arm(identity)
        hold.clear()
        assertNull(hold.resolve(identity, null, null, null, canHold = true, active = true).settings)
    }
}
