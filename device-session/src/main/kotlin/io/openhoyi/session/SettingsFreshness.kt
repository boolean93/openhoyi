package io.openhoyi.session

/** Conservative age policy; one firmware 1.1.3 capture has a 132-second settings interval. */
object SettingsFreshness {
    const val MAX_AGE_MS = 180_000L
    fun isFresh(receivedAtMs: Long?, nowMs: Long): Boolean =
        TelemetryFreshness.isFresh(receivedAtMs, nowMs, MAX_AGE_MS)
}
