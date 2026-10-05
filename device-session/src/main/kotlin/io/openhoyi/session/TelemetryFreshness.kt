package io.openhoyi.session

/** Monotonic sample-age qualification, separate from elapsed transaction timers. */
object TelemetryFreshness {
    fun isFresh(atMs: Long?, nowMs: Long, maxAgeMs: Long = 1500): Boolean =
        atMs != null && atMs >= 0 && nowMs >= atMs && maxAgeMs >= 0 &&
            nowMs - atMs <= maxAgeMs
}
