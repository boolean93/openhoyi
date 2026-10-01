package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.StopReason
import org.junit.Assert.*
import org.junit.Test

class BrewFeedbackCompletionTest {
    @Test fun onlyKnownUnalarmedObservedEndCanShowLocalFeedback() {
        for(state in DeviceState.entries) for(observed in listOf(false, true))
            for(alarms in listOf(null, 0, 1, 255, -1))
                assertEquals("$state/$observed/$alarms", observed && state == DeviceState.READY && alarms == 0,
                    BrewFeedbackCompletion.allowed(observed, state, alarms, null))
    }
    @Test fun safetyStopsNeverBecomeAnEncouragement() {
        for(reason in StopReason.entries)
            assertEquals(reason.name, reason == StopReason.MANUAL || reason == StopReason.TARGET_WEIGHT,
                BrewFeedbackCompletion.allowed(true, DeviceState.READY, 0, reason))
        assertTrue(BrewFeedbackCompletion.allowed(true, DeviceState.READY, 0, null))
    }
}
