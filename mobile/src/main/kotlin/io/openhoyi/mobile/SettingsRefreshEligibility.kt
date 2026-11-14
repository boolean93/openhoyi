package io.openhoyi.mobile

/** A finalized unknown lever write remains blocked for control, not for settings sync.
 * Sync never acknowledges or removes the journal. Active writes and other recovery stay blocked. */
internal object SettingsRefreshEligibility {
    fun allowed(foreground: Boolean, readyIdle: Boolean, controlRecoveryBlocked: Boolean,
        leverActive: Boolean, operationActive: Boolean): Boolean =
        foreground && readyIdle && !controlRecoveryBlocked && !leverActive && !operationActive
}
