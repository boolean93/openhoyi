package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.StopReason

/** Eligibility for local encouragement only. Never a shot permission or an end detector. */
internal object BrewFeedbackCompletion {
    fun allowed(observedEnd: Boolean, coffeeState: DeviceState, alarmBits: Int?, stopReason: StopReason?): Boolean =
        observedEnd && coffeeState == DeviceState.READY && alarmBits == 0 &&
            (stopReason == null || stopReason == StopReason.MANUAL || stopReason == StopReason.TARGET_WEIGHT)
}
