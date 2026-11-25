package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic authenticated endpoint and fake driver only; checks representative writes in seven control families. */
class MachineDispatchFreshnessTest {
    private enum class Control { SETTING, CUPS, SLEEP, PREHEAT, CANCEL_PREHEAT, START, SCHEDULE }
    private val expiredAt = 1_501L
    private val badCaptures = listOf(-1L, Long.MIN_VALUE, expiredAt + 1, 0L)
    private fun receive(f: CoffeeSessionFixture, hex: String) =
        f.session.onNotification(f.session.generation, KnownGatt.coffeeNotify, f.hex(hex))
    private fun freshOtherReadbacks(f: CoffeeSessionFixture, now: Long) {
        f.now = now
        receive(f, "830113FD5C007D0F350019006E")
        receive(f, "8340FE0A00071E0A00071E0A00071E0A00071E3D")
        receive(f, "83800A00071E0A00071E0A00071E10")
    }
    private fun idle(f: CoffeeSessionFixture, at: Long) {
        f.now = at
        queuedIdle(f, at)
    }
    private fun queuedIdle(f: CoffeeSessionFixture, capturedAt: Long) =
        f.session.onNotification(f.session.generation, KnownGatt.coffeeNotify,
            f.hex("400023F02F1C770B00000000000000190321AF"), capturedAt)
    private fun plan(): WeeklySleepSchedule {
        // Decode only: no additional fixture or transport belongs to this plan.
        fun decode(hex: String) = (HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value as SleepPart
        return requireNotNull(WeeklySleepSchedule.fromReadback(
            decode("8340FE0A00071E0A00071E0A00071E0A00071E3D"), decode("83800A00071E0A00071E0A00071E10")))
    }
    private fun target(expected: WeeklySleepSchedule) = WeeklySleepSchedule(expected.days.mapIndexed { index, day ->
        if (index == 0) day.copy(enabled = !day.enabled) else day
    })
    private fun submit(f: CoffeeSessionFixture, control: Control, done: (OperationResult) -> Unit) {
        when (control) {
            Control.SETTING -> f.session.writeSetting(MachineSettingChange.BrewTemperature(93), done)
            Control.CUPS -> f.session.resetCupCount(25, done)
            Control.SLEEP -> f.session.enterSleep(done)
            Control.PREHEAT -> f.session.setBrewWait(92, { true }, done)
            Control.CANCEL_PREHEAT -> f.session.setBrewWait(0, { true }, done)
            Control.START -> f.session.startExtraction(StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0), done)
            Control.SCHEDULE -> plan().let { f.session.writeSleepSchedule(target(it), it, done) }
        }
    }
    @Test fun stopOwnsAdmissionDuringReentrantScheduleCancellation() {
        val f = CoffeeSessionFixture()
        freshOtherReadbacks(f, 10); idle(f, 10)
        var scheduleResult: OperationResult? = null
        var reentrantResult: OperationResult? = null
        var refreshResult: OperationResult? = null
        submit(f, Control.SCHEDULE) {
            scheduleResult = it
            f.session.enterSleep { result -> reentrantResult = result }
            f.session.refreshStaleSettings({ true },forceFreshReadback=true) { refreshResult = it }
        }
        f.complete() // Between the two schedule writes: there is no in-flight GATT operation.
        val beforeStop = f.calls.size
        var stopResult: OperationResult? = null
        f.session.stopExtraction(7) { stopResult = it }
        assertTrue(scheduleResult is OperationResult.Unknown)
        assertTrue(reentrantResult is OperationResult.Failed)
        assertTrue(refreshResult is OperationResult.Failed)
        assertEquals(beforeStop + 1, f.calls.size)
        val next = f.calls.last().third as GattOperation.Write
        assertArrayEquals(CoffeeCommands.stop(7).frame.toByteArray(), next.bytes)
        f.complete()
        assertTrue(stopResult is OperationResult.Success)
        f.now = 510; f.session.tick()
        assertEquals(beforeStop + 1, f.calls.size)
    }

    @Test fun everyControlRejectsInvalidIdleBeforeAnyWrite() {
        for (control in Control.entries) for (at in badCaptures) {
            val f = CoffeeSessionFixture()
            freshOtherReadbacks(f, expiredAt); queuedIdle(f, at)
            val before = f.calls.size
            var result: OperationResult? = null
            submit(f, control) { result = it }
            assertTrue("$control/$at: $result", result is OperationResult.Failed)
            assertEquals(before, f.calls.size)
            f.session.disconnect()
        }
    }
    @Test fun everyQueuedControlRechecksIdleWithoutAControllerTick() {
        for (control in Control.entries) for (at in badCaptures) {
            val f = CoffeeSessionFixture()
            freshOtherReadbacks(f, 0); idle(f, 0)
            f.session.setBrewWait(0, { true }) { }
            val before = f.calls.size
            var result: OperationResult? = null
            submit(f, control) { result = it }
            assertNull("$control must actually queue", result)
            assertEquals(before, f.calls.size)
            f.now = expiredAt
            queuedIdle(f, at)
            f.complete()
            assertTrue("$control/$at: $result", result is OperationResult.Failed)
            assertEquals(before, f.calls.size)
            f.session.disconnect()
        }
    }
    @Test fun everyControlKeepsInclusiveFreshBoundaryAndValidLargeClocks() {
        for (control in Control.entries) for ((at, now) in listOf(0L to 0L, 0L to 1500L, (Long.MAX_VALUE - 1500) to Long.MAX_VALUE)) {
            val f = CoffeeSessionFixture()
            freshOtherReadbacks(f, now); queuedIdle(f, at)
            val before = f.calls.size
            var result: OperationResult? = null
            submit(f, control) { result = it }
            assertNull("$control/$at/$now must submit", result)
            assertEquals(before + 1, f.calls.size)
            assertTrue(f.calls.last().third is GattOperation.Write)
            f.session.disconnect()
        }
    }
    @Test fun partiallySentScheduleCannotSendSecondPartWithInvalidIdle() {
        for (at in badCaptures) {
            val f = CoffeeSessionFixture()
            freshOtherReadbacks(f, 0); idle(f, 0)
            var result: OperationResult? = null
            submit(f, Control.SCHEDULE) { result = it }
            f.complete()
            val before = f.calls.size
            freshOtherReadbacks(f, expiredAt); queuedIdle(f, at)
            f.session.tick()
            assertTrue("$at: $result", result is OperationResult.Unknown)
            assertEquals(before, f.calls.size)
            f.session.disconnect()
        }
    }
    @Test fun emergencyStopStillSendsWhenIdleEvidenceIsInvalid() {
        for (at in badCaptures) {
            val f = CoffeeSessionFixture()
            freshOtherReadbacks(f, expiredAt); queuedIdle(f, at)
            val before = f.calls.size
            var result: OperationResult? = null
            f.session.stopExtraction(7) { result = it }
            assertNull(result)
            assertEquals(before + 1, f.calls.size)
            assertTrue((f.calls.last().third as GattOperation.Write).bytes.contentEquals(f.hex("0200070000")))
            f.complete()
            assertTrue(result is OperationResult.Success)
            f.session.disconnect()
        }
    }

}
