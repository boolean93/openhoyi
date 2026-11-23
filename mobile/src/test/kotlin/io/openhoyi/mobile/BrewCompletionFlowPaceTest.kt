package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrewCompletionFlowPaceTest {
    @Test fun existingMeasuredLevelsMapToFlowLabelsOnly() {
        assertEquals(R.string.completion_flow_pace_slow, completionFlowPaceLabel(BrewFeedbackClips.Level.LOW_FLOW))
        assertEquals(R.string.completion_flow_pace_balanced, completionFlowPaceLabel(BrewFeedbackClips.Level.BRAVO))
        assertEquals(R.string.completion_flow_pace_fast, completionFlowPaceLabel(BrewFeedbackClips.Level.HIGH_FLOW))
        assertNull(completionFlowPaceLabel(null))
    }
}
