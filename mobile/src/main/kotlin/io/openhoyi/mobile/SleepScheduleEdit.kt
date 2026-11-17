package io.openhoyi.mobile

import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule

/** Explicit all-days intent only permits a uniform week, never arbitrary multi-day writes. */
internal object SleepScheduleEdit {
    fun target(baseline: WeeklySleepSchedule, day: Int?, value: WeeklySleepDay): WeeklySleepSchedule {
        require(day == null || day in 0..6)
        return WeeklySleepSchedule(baseline.days.mapIndexed { index, previous ->
            if (day == null || index == day) value else previous
        })
    }

    fun accepts(baseline: WeeklySleepSchedule, target: WeeklySleepSchedule, allDays: Boolean): Boolean {
        val changed = baseline.days.indices.count { baseline.days[it] != target.days[it] }
        return changed > 0 && if (allDays) target.days.distinct().size == 1 else changed == 1
    }
}
