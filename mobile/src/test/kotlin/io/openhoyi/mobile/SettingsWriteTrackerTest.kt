package io.openhoyi.mobile

import io.openhoyi.protocol.DecodeResult
import io.openhoyi.protocol.HoyiCodec
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.Settings
import io.openhoyi.session.OperationResult
import org.junit.Assert.*
import org.junit.Test

class SettingsWriteTrackerTest {
    private val initial = (HoyiCodec.decode("830113FD5C007D0F350019006E".chunked(2)
        .map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value as Settings

    @Test fun requiresNewMatchingReadbackAfterWrite() {
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(MachineSettingChange.BrewTemperature(93)))
        assertNull(tracker.begin(MachineSettingChange.SteamTemperature(126)))
        assertFalse(tracker.observe(1, initial.copy(brewTemperatureC = 93)))
        assertTrue(tracker.written(token, OperationResult.Success(), 1))
        assertFalse(tracker.observe(1, initial.copy(brewTemperatureC = 93)))
        assertFalse(tracker.observe(2, initial))
        assertTrue(tracker.observe(3, initial.copy(brewTemperatureC = 93)))
        assertEquals(SettingsWriteTracker.State.CONFIRMED, tracker.state)
    }

    @Test fun failedUnknownAndStaleCallbacksCannotClaimApplied() {
        val tracker = SettingsWriteTracker()
        val first = requireNotNull(tracker.begin(MachineSettingChange.BrewHeating(false)))
        assertTrue(tracker.written(first, OperationResult.Unknown("link"), 0))
        assertEquals(SettingsWriteTracker.State.UNKNOWN, tracker.state)
        val second = requireNotNull(tracker.begin(MachineSettingChange.Light(false)))
        assertFalse(tracker.written(first, OperationResult.Success(), 0))
        assertFalse(tracker.timeout(first))
        assertTrue(tracker.written(second, OperationResult.Success(), 0))
        tracker.disconnected()
        assertFalse(tracker.observe(1, initial.copy(flags = initial.flags and 0x08.inv())))
        assertEquals(SettingsWriteTracker.State.UNKNOWN, tracker.state)
    }

    @Test fun readbackTimeoutRemainsUnknown() {
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(MachineSettingChange.SteamHeating(false)))
        tracker.written(token, OperationResult.Success(), 0)
        assertTrue(tracker.timeout(token))
        assertEquals(SettingsWriteTracker.State.UNKNOWN, tracker.state)
    }

    @Test fun leverModeRequiresNewMatchingFlags() {
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(MachineSettingChange.LeverMode(false, false)))
        assertTrue(tracker.written(token, OperationResult.Success(), 10))
        assertFalse(tracker.observe(10, initial.copy(flags = initial.flags and 0x3F)))
        assertFalse(tracker.observe(11, initial))
        assertTrue(tracker.observe(12, initial.copy(flags = initial.flags and 0x3F)))
        assertEquals(SettingsWriteTracker.State.CONFIRMED, tracker.state)
    }

    @Test fun standbyDelayRequiresCorrectMinutesAndPreservedTemperature() {
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(MachineSettingChange.StandbyDelay(30, initial.standbyTemperatureC)))
        assertTrue(tracker.written(token, OperationResult.Success(), 5))
        assertFalse(tracker.observe(6, initial.copy(standbyMinutes = 15)))
        assertFalse(tracker.observe(7, initial.copy(standbyMinutes = 30, standbyTemperatureC = 90)))
        assertTrue(tracker.observe(8, initial.copy(standbyMinutes = 30)))
        assertEquals(SettingsWriteTracker.State.CONFIRMED, tracker.state)
    }

    @Test fun standbyTemperatureRequiresNewReadbackAndPreservedDelay() {
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(MachineSettingChange.StandbyTemperature(70, initial.standbyMinutes)))
        assertTrue(tracker.written(token, OperationResult.Success(), 8))
        assertFalse(tracker.observe(8, initial.copy(standbyTemperatureC = 70)))
        assertFalse(tracker.observe(9, initial.copy(standbyMinutes = 30, standbyTemperatureC = 70)))
        assertTrue(tracker.observe(10, initial.copy(standbyTemperatureC = 70)))
        assertEquals(SettingsWriteTracker.State.CONFIRMED, tracker.state)
    }

    @Test fun brewCompensationNeedsNewMatchingMachineValue() {
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(MachineSettingChange.BrewCompensation(5)))
        assertTrue(tracker.written(token, OperationResult.Success(), 10))
        assertFalse(tracker.observe(10, initial.copy(brewCompensationTenthsC = 50)))
        assertFalse(tracker.observe(11, initial.copy(brewCompensationTenthsC = 40)))
        assertTrue(tracker.observe(12, initial.copy(brewCompensationTenthsC = 50)))
        assertEquals(SettingsWriteTracker.State.CONFIRMED, tracker.state)
    }

    @Test fun sleepScheduleToggleNeedsNewMatchingFlags() {
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(MachineSettingChange.SleepScheduleEnabled(false)))
        assertTrue(tracker.written(token, OperationResult.Success(), 20))
        assertFalse(tracker.observe(20, initial.copy(flags = initial.flags and 0x01.inv())))
        assertFalse(tracker.observe(21, initial))
        assertTrue(tracker.observe(22, initial.copy(flags = initial.flags and 0x01.inv())))
        assertEquals(SettingsWriteTracker.State.CONFIRMED, tracker.state)
    }

    @Test fun waterSupplyNeedsNewMatchingReadbackBit() {
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(MachineSettingChange.WaterSupply(piped = true)))
        assertTrue(tracker.written(token, OperationResult.Success(), 30))
        assertFalse(tracker.observe(30, initial.copy(flags = initial.flags or 0x02)))
        assertFalse(tracker.observe(31, initial.copy(flags = initial.flags and 0x02.inv())))
        assertTrue(tracker.observe(32, initial.copy(flags = initial.flags or 0x02)))
        assertEquals(SettingsWriteTracker.State.CONFIRMED, tracker.state)
    }

    @Test fun runModeRequiresNewMatchingReadbackBit() {
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(MachineSettingChange.RunMode(studio = false)))
        assertTrue(tracker.written(token, OperationResult.Success(), 40))
        assertFalse(tracker.observe(41, initial))
        assertTrue(tracker.observe(42, initial.copy(flags = initial.flags and 0x04.inv())))
        assertEquals(SettingsWriteTracker.State.CONFIRMED, tracker.state)
    }
}
