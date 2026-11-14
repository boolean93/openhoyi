package io.openhoyi.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseDownloadPresentationTest {
    private fun visible(settings: Boolean = false, suppressed: Boolean = false,
        sameActivity: Boolean = true, samePage: Boolean = true, blocked: Boolean = false,
        phase: ReleaseDownloadController.Phase = ReleaseDownloadController.Phase.DOWNLOADING) =
        showReleaseDownloadProgress(settings, suppressed, sameActivity, samePage, blocked, phase)

    @Test fun settingsUsesInlineCardForEveryDownloadState() {
        ReleaseDownloadController.Phase.values().forEach { assertFalse(visible(settings = true, phase = it)) }
    }

    @Test fun otherPageKeepsProgressRetryAndInstallFeedback() {
        ReleaseDownloadController.Phase.values().filter { it != ReleaseDownloadController.Phase.IDLE }
            .forEach { assertTrue(visible(phase = it)) }
        assertFalse(visible(phase = ReleaseDownloadController.Phase.IDLE))
    }

    @Test fun navigationRotationDismissalAndSafetyNeverResurrectPopup() {
        assertFalse(visible(samePage = false))
        assertFalse(visible(sameActivity = false))
        assertFalse(visible(suppressed = true))
        assertFalse(visible(blocked = true))
        assertFalse(visible(settings = true, samePage = false))
    }
}
