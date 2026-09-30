package io.openhoyi.session

import io.openhoyi.protocol.SleepPart
import io.openhoyi.protocol.WeeklySleepSchedule

/** Software policy, not a firmware transaction identifier or guaranteed notification cadence. */
object SleepScheduleFreshness {
    const val MAX_PAIR_GAP_MS = 10_000L
    fun isFresh(first: SleepPart?, firstAt: Long?, second: SleepPart?, secondAt: Long?, now: Long): Boolean {
        if (!SettingsFreshness.isFresh(firstAt, now) || !SettingsFreshness.isFresh(secondAt, now)) return false
        if (firstAt == null || secondAt == null || secondAt < firstAt || secondAt - firstAt > MAX_PAIR_GAP_MS) return false
        return WeeklySleepSchedule.fromReadback(first, second) != null
    }
}
