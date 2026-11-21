package io.openhoyi.mobile

private const val DIAL_FLOOR_C = 30f

internal fun homeBoilerArcFraction(reading: Float?, target: Float?): Float =
    if (reading == null || !reading.isFinite() || target == null || !target.isFinite() || target <= DIAL_FLOOR_C) 0f
    else ((reading - DIAL_FLOOR_C) / (target - DIAL_FLOOR_C)).coerceIn(0f, 1f)

internal fun homeBoilerShouldAnimate(old: Float?, next: Float?, oldTarget: Float?, target: Float?,
    oldSession: Any?, session: Any?): Boolean =
    // Presentation guard only. Out-of-band sensor values still render immediately.
    session != null && oldSession == session && target == oldTarget &&
        target != null && target.isFinite() && target > DIAL_FLOOR_C &&
        old != null && next != null && old in 0f..200f && next in 0f..200f && next > old
