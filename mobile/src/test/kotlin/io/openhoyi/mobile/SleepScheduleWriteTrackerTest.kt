package io.openhoyi.mobile

import io.openhoyi.protocol.DecodeResult
import io.openhoyi.protocol.HoyiCodec
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.SleepPart
import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.OperationResult
import org.junit.Assert.*
import org.junit.Test

class SleepScheduleWriteTrackerTest {
    private fun part(hex: String) = (HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        as DecodeResult.Valid).value as SleepPart
    private val first = part("8340FE0A00071E0A00071E0A00071E0A00071E3D")
    private val second = part("83800A00071E0A00071E0A00071E10")
    private val original = requireNotNull(WeeklySleepSchedule.fromReadback(first, second))

    @Test fun confirmsOnlyAfterBothNewFragmentsMatchEntireWeek() {
        val changed = WeeklySleepSchedule(original.days.toMutableList().apply {
            this[0] = WeeklySleepDay(false, SleepDay(10, 0, 7, 30))
        })
        val tracker = SleepScheduleWriteTracker()
        val token = requireNotNull(tracker.begin(changed, 11, 11))
        assertFalse(tracker.observe(11, 11, first, second))
        assertTrue(tracker.written(token, OperationResult.Success(), 11, 11, first, second))
        assertFalse(tracker.observe(12, 12, first, second))
        assertFalse(tracker.observe(13, 11, first, second))
        val changedFirst = part("83407E0A00071E0A00071E0A00071E0A00071E3D")
        assertTrue(tracker.observe(13, 12, changedFirst, second))
        assertEquals(SleepScheduleWriteTracker.State.CONFIRMED, tracker.state)
    }

    @Test fun failedPartialWriteAndDisconnectRemainUnconfirmed() {
        val tracker = SleepScheduleWriteTracker()
        val token = requireNotNull(tracker.begin(original, 0, 0))
        assertNull(tracker.begin(original, 0, 0))
        assertTrue(tracker.written(token, OperationResult.Unknown("link"), 0, 0, first, second))
        assertEquals(SleepScheduleWriteTracker.State.UNKNOWN, tracker.state)
        assertNull(tracker.begin(original, 0, 0))
        assertTrue(tracker.observe(1, 1, first, second))
        assertEquals(SleepScheduleWriteTracker.State.RECONCILED, tracker.state)
        val next = requireNotNull(tracker.begin(original, 1, 1))
        assertFalse(tracker.written(token, OperationResult.Success(), 0, 0, first, second))
        assertTrue(tracker.written(next, OperationResult.Success(), 1, 1, first, second))
        tracker.disconnected(1, 1)
        assertFalse(tracker.observe(1, 1, first, second))
        assertEquals(SleepScheduleWriteTracker.State.UNKNOWN, tracker.state)
        assertTrue(tracker.observe(2, 2, first, second))
        assertEquals(SleepScheduleWriteTracker.State.RECONCILED, tracker.state)
    }

    @Test fun matchingFragmentsObservedDuringTransportConfirmOnlyAfterBothWritesSucceed() {
        val tracker = SleepScheduleWriteTracker()
        val token = requireNotNull(tracker.begin(original, 4, 7))
        assertFalse(tracker.observe(5, 8, first, second))
        assertEquals(SleepScheduleWriteTracker.State.WRITING, tracker.state)
        assertTrue(tracker.written(token, OperationResult.Success(), 5, 8, first, second))
        assertEquals(SleepScheduleWriteTracker.State.CONFIRMED, tracker.state)
    }
}
