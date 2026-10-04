package io.openhoyi.session

import io.openhoyi.protocol.DecodeResult
import io.openhoyi.protocol.HoyiCodec
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.Settings
import org.junit.Assert.*
import org.junit.Test

/** Host serial ordering only; no Android, transport, firmware transaction ID or replay renumbering. */
class ReadbackOrderingTest {
    private val initial = (HoyiCodec.decode("830113FD5C007D0F350019006E".chunked(2)
        .map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value as Settings
    private fun setting(boundary: String) {
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(MachineSettingChange.BrewTemperature(93)))
        assertTrue(tracker.written(token, OperationResult.Success(), 10))
        assertFalse(tracker.observe(12, initial))
        when (boundary) {
            "timeout" -> assertTrue(tracker.timeout(token, 10))
            "disconnect" -> tracker.disconnected(10)
        }
        assertFalse("Older matching settings must not confirm after a newer different readback",
            tracker.observe(11, initial.copy(brewTemperatureC = 93)))
        assertFalse("Repeated host serial must not replace already observed settings",
            tracker.observe(12, initial.copy(brewTemperatureC = 93)))
        assertEquals(if (boundary.isEmpty()) SettingsWriteTracker.State.WAITING_READBACK else SettingsWriteTracker.State.UNKNOWN, tracker.state)
        assertTrue(tracker.observe(13, initial.copy(brewTemperatureC = 93)))
        assertEquals(SettingsWriteTracker.State.CONFIRMED, tracker.state)
    }
    private fun sleep(boundary: String) {
        val tracker = SleepNowTracker()
        val token = requireNotNull(tracker.begin())
        assertTrue(tracker.written(token, OperationResult.Success(), 10))
        assertFalse(tracker.observe(12, 0))
        when (boundary) {
            "timeout" -> assertTrue(tracker.timeout(token, 10))
            "disconnect" -> tracker.disconnected(10)
        }
        assertFalse("Older asleep readback must not confirm after a newer awake readback", tracker.observe(11, 1))
        assertFalse("Repeated host serial must not replace already observed sleep state", tracker.observe(12, 1))
        assertEquals(if (boundary.isEmpty()) SleepNowTracker.State.WAITING_ASLEEP else SleepNowTracker.State.UNKNOWN, tracker.state)
        assertTrue(tracker.observe(13, 1))
        assertEquals(SleepNowTracker.State.CONFIRMED, tracker.state)
    }
    @Test fun newSettingsOperationDoesNotInheritAnOldConsumedWatermark() {
        val tracker = SettingsWriteTracker()
        val first = requireNotNull(tracker.begin(MachineSettingChange.BrewTemperature(93)))
        tracker.written(first, OperationResult.Success(), 40)
        assertTrue(tracker.observe(50, initial.copy(brewTemperatureC = 93)))
        requireNotNull(tracker.begin(MachineSettingChange.Light(false)))
        // A new host stream can disconnect before written() establishes its fresh baseline.
        tracker.disconnected(5)
        assertTrue(tracker.observe(6, initial.copy(flags = initial.flags and 0x08.inv())))
    }
    @Test fun newSleepOperationDoesNotInheritAnOldConsumedWatermark() {
        val tracker = SleepNowTracker()
        val first = requireNotNull(tracker.begin())
        tracker.written(first, OperationResult.Success(), 40)
        assertTrue(tracker.observe(50, 1))
        requireNotNull(tracker.begin())
        tracker.disconnected(5)
        assertTrue(tracker.observe(6, 1))
    }
    @Test fun settingsIgnoreOlderAndRepeatedReadbacks() = setting("")
    @Test fun settingsTimeoutCannotMoveReadbackWatermarkBackwards() = setting("timeout")
    @Test fun settingsDisconnectCannotMoveReadbackWatermarkBackwards() = setting("disconnect")
    @Test fun sleepIgnoresOlderAndRepeatedReadbacks() = sleep("")
    @Test fun sleepTimeoutCannotMoveReadbackWatermarkBackwards() = sleep("timeout")
    @Test fun sleepDisconnectCannotMoveReadbackWatermarkBackwards() = sleep("disconnect")
}
