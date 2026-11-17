package io.openhoyi.mobile

import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule
import org.junit.Assert.*
import org.junit.Test

class SleepScheduleEditTest {
    private val baseline = WeeklySleepSchedule((0..6).map { WeeklySleepDay(it % 2 == 0, SleepDay(21, it, 7, it)) })
    private val value = WeeklySleepDay(true, SleepDay(23, 30, 6, 45))

    @Test fun allDaysSetsEveryDayWithoutMutatingBaseline() {
        val before = baseline.days.toList()
        val target = SleepScheduleEdit.target(baseline, null, value)
        assertEquals(List(7) { value }, target.days)
        assertEquals(before, baseline.days)
        assertTrue(SleepScheduleEdit.accepts(baseline, target, true))
        assertFalse(SleepScheduleEdit.accepts(baseline, target, false))
    }
    @Test fun eachDayCanOverrideUniformWeekWithoutChangingOtherDays() {
        val week = SleepScheduleEdit.target(baseline, null, value)
        for (day in 0..6) {
            val override = value.copy(enabled = false)
            val target = SleepScheduleEdit.target(week, day, override)
            assertEquals(override, target.days[day])
            assertTrue(target.days.indices.filter { it != day }.all { target.days[it] == value })
            assertTrue(SleepScheduleEdit.accepts(week, target, false))
            assertFalse(SleepScheduleEdit.accepts(week, target, true))
        }
    }
    @Test fun arbitraryMultiDayAndUnchangedWritesAreRejected() {
        val mixed = SleepScheduleEdit.target(SleepScheduleEdit.target(baseline, 1, value), 3, value)
        assertFalse(SleepScheduleEdit.accepts(baseline, mixed, true))
        assertFalse(SleepScheduleEdit.accepts(baseline, mixed, false))
        assertFalse(SleepScheduleEdit.accepts(baseline, baseline, false))
        val uniform = SleepScheduleEdit.target(baseline, null, value)
        assertFalse(SleepScheduleEdit.accepts(uniform, uniform, true))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidDayRejected() {
        SleepScheduleEdit.target(baseline, 7, value)
    }
}
