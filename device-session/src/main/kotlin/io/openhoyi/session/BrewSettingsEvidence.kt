package io.openhoyi.session

/**
 * Brewing configuration is an observed fact of the current authenticated connection, not live telemetry.
 * Owners must clear the settings and timestamp on disconnect/replacement. This does not authorize
 * setting/cup/schedule writes, and does not replace fresh awake idle, alarms or dispatch guards.
 */
object BrewSettingsEvidence {
    fun isAvailable(ready: Boolean, hasSettings: Boolean, receivedAtMs: Long?, nowMs: Long): Boolean =
        ready && hasSettings && receivedAtMs != null && receivedAtMs >= 0 && nowMs >= receivedAtMs
}
