package io.openhoyi.mobile

import io.openhoyi.protocol.SleepPart

/** Enabling a stored schedule is allowed only after both device fragments are understood. */
object SleepScheduleSafety {
    fun canEnable(first: SleepPart?, second: SleepPart?): Boolean {
        if (first == null || second == null) return false
        if (first.firstDaySundayIndex != 0 || first.enabledBits == null || first.days.size != 4 ||
            second.firstDaySundayIndex != 4 || second.days.size != 3) return false
        return (first.days + second.days).all { day ->
            day.sleepHour in 0..23 && day.sleepMinute in 0..59 &&
                day.wakeHour in 0..23 && day.wakeMinute in 0..59
        }
    }
}
