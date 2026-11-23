package io.openhoyi.mobile

/** Present existing machine-water flow evidence; unavailable evidence has no label. */
internal fun completionFlowPaceLabel(level: BrewFeedbackClips.Level?): Int? = when (level) {
    BrewFeedbackClips.Level.LOW_FLOW -> R.string.completion_flow_pace_slow
    BrewFeedbackClips.Level.BRAVO -> R.string.completion_flow_pace_balanced
    BrewFeedbackClips.Level.HIGH_FLOW -> R.string.completion_flow_pace_fast
    null -> null
}
