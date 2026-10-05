package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic authenticated endpoint and fake driver only; checks representative writes in seven control families. */
class MachineDispatchFreshnessTest {
    private enum class Control { SETTING, CUPS, SLEEP, PREHEAT, CANCEL_PREHEAT, START, SCHEDULE }
    private val badClocks = listOf(-1L to 0L, Long.MIN_VALUE to Long.MAX_VALUE, 501L to 500L, 0L to 1501L)
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
        receive(f, "400023F02F1C770B00000000000000190321AF")
    }
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
    @Test fun everyControlRejectsInvalidIdleBeforeAnyWrite() {
        for (control in Control.entries) for ((at, now) in badClocks) {
            val f = CoffeeSessionFixture()
            freshOtherReadbacks(f, now); idle(f, at); f.now = now
            val before = f.calls.size
            var result: OperationResult? = null
            submit(f, control) { result = it }
            assertTrue("$control/$at/$now: $result", result is OperationResult.Failed)
            assertEquals(before, f.calls.size)
            f.session.disconnect()
        }
    }
    @Test fun everyQueuedControlRechecksIdleWithoutAControllerTick() {
        for (control in Control.entries) for ((at, now) in badClocks) {
            val f = CoffeeSessionFixture()
            freshOtherReadbacks(f, now); idle(f, now)
            f.session.setBrewWait(0, { true }) { }
            val before = f.calls.size
            var result: OperationResult? = null
            submit(f, control) { result = it }
            assertNull("$control must actually queue", result)
            assertEquals(before, f.calls.size)
            idle(f, at); f.now = now
            f.complete()
            assertTrue("$control/$at/$now: $result", result is OperationResult.Failed)
            assertEquals(before, f.calls.size)
            f.session.disconnect()
        }
    }
    @Test fun everyControlKeepsInclusiveFreshBoundaryAndValidLargeClocks() {
        for (control in Control.entries) for ((at, now) in listOf(0L to 0L, 0L to 1500L, (Long.MAX_VALUE - 1500) to Long.MAX_VALUE)) {
            val f = CoffeeSessionFixture()
            freshOtherReadbacks(f, now); idle(f, at); f.now = now
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
        for ((at, now) in badClocks) {
            val f = CoffeeSessionFixture()
            val dispatchAt = maxOf(500, now)
            val firstAt = dispatchAt - 500
            freshOtherReadbacks(f, firstAt); idle(f, firstAt)
            var result: OperationResult? = null
            submit(f, Control.SCHEDULE) { result = it }
            f.complete()
            val before = f.calls.size
            freshOtherReadbacks(f, dispatchAt); idle(f, at); f.now = dispatchAt
            f.session.tick()
            assertTrue("$at/$now: $result", result is OperationResult.Unknown)
            assertEquals(before, f.calls.size)
            f.session.disconnect()
        }
    }
    @Test fun emergencyStopStillSendsWhenIdleEvidenceIsInvalid() {
        for ((at, now) in badClocks) {
            val f = CoffeeSessionFixture()
            freshOtherReadbacks(f, now); idle(f, at); f.now = now
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
