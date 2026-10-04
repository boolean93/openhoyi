package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

/** Ordering is a host serial contract, not a firmware transaction identifier. */
class SleepScheduleOrderingTest {
    private fun part(hex: String) = (HoyiCodec.decode(hex.chunked(2).map {
        it.toInt(16).toByte()
    }.toByteArray()) as DecodeResult.Valid).value as SleepPart
    private val first = part("8340FE0A00071E0A00071E0A00071E0A00071E3D")
    private val changed = part("83407E0A00071E0A00071E0A00071E0A00071E3D")
    private val second = part("83800A00071E0A00071E0A00071E10")
    private val target = requireNotNull(WeeklySleepSchedule.fromReadback(changed, second))
    private fun tracker() = SleepScheduleWriteTracker().also {
        val token = requireNotNull(it.begin(target, 10, 10))
        assertTrue(it.written(token, OperationResult.Success(), 10, 10, first, second))
    }
    @Test fun olderFirstCannotConfirm() {
        val t = tracker()
        assertFalse(t.observe(12, 12, first, second))
        assertFalse(t.observe(11, 13, changed, second))
        assertTrue(t.observe(13, 13, changed, second))
    }
    @Test fun olderSecondCannotConfirm() {
        val t = tracker()
        assertFalse(t.observe(12, 12, first, second))
        assertFalse(t.observe(13, 11, changed, second))
        assertTrue(t.observe(13, 13, changed, second))
    }
    @Test fun repeatedSerialCannotChangePayload() {
        val t = tracker()
        assertFalse(t.observe(12, 12, first, second))
        assertFalse(t.observe(12, 13, changed, second))
        assertTrue(t.observe(13, 13, changed, second))
    }
    @Test fun unchangedFirstCanWaitForNewSecond() {
        val t = tracker()
        assertFalse(t.observe(11, 10, changed, second))
        assertTrue(t.observe(11, 11, changed, second))
    }
    @Test fun unchangedSecondCanWaitForNewFirst() {
        val t = tracker()
        assertFalse(t.observe(10, 11, first, second))
        assertTrue(t.observe(11, 11, changed, second))
    }
    @Test fun identicalDecodedFragmentAtSameSerialIsStillTheSameValue() {
        val t = tracker()
        assertFalse(t.observe(11, 10, changed, second))
        val decodedAgain = part("83407E0A00071E0A00071E0A00071E0A00071E3D")
        assertTrue(t.observe(11, 11, decodedAgain, second))
    }
    @Test fun identicalDecodedSecondAtSameSerialIsStillTheSameValue() {
        val t = tracker()
        assertFalse(t.observe(10, 11, first, second))
        val decodedAgain = part("83800A00071E0A00071E0A00071E10")
        assertTrue(t.observe(11, 11, changed, decodedAgain))
    }
    @Test fun repeatedSecondSerialCannotChangePayload() {
        val t = tracker()
        val differentSecond = part("83800B00071E0A00071E0A00071E10")
        assertFalse(t.observe(11, 12, changed, differentSecond))
        assertFalse(t.observe(12, 12, changed, second))
        assertTrue(t.observe(12, 13, changed, second))
    }
    private fun boundary(disconnect: Boolean) {
        val t = SleepScheduleWriteTracker()
        val token = requireNotNull(t.begin(target, 10, 10))
        t.written(token, OperationResult.Success(), 10, 10, first, second)
        assertFalse(t.observe(12, 12, first, second))
        if (disconnect) t.disconnected(10, 10) else assertTrue(t.timeout(token, 10, 10))
        assertFalse(t.observe(11, 11, changed, second))
        assertEquals(SleepScheduleWriteTracker.State.UNKNOWN, t.state)
        assertTrue(t.observe(13, 13, first, second))
        assertEquals(SleepScheduleWriteTracker.State.RECONCILED, t.state)
    }
    @Test fun timeoutCannotRegressEitherBaseline() = boundary(false)
    @Test fun disconnectCannotRegressEitherBaseline() = boundary(true)
    @Test fun newOperationEstablishesItsOwnBaselines() {
        val t = tracker()
        assertTrue(t.observe(50, 50, changed, second))
        requireNotNull(t.begin(target, 4, 4))
        t.disconnected(5, 5)
        assertTrue(t.observe(6, 6, first, second))
        assertEquals(SleepScheduleWriteTracker.State.RECONCILED, t.state)
    }
}
