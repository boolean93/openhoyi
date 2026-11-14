package io.openhoyi.mobile

/** The settings card owns progress there; a popup is only a lease on its original other page. */
internal fun showReleaseDownloadProgress(
    settingsPage: Boolean,
    suppressed: Boolean,
    sameActivity: Boolean,
    samePage: Boolean,
    blocked: Boolean,
    phase: ReleaseDownloadController.Phase,
): Boolean = !settingsPage && !suppressed && sameActivity && samePage && !blocked &&
    phase != ReleaseDownloadController.Phase.IDLE
