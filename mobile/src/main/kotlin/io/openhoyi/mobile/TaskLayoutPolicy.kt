package io.openhoyi.mobile

internal enum class TaskNavigationMode {
    RAIL,
    BOTTOM,
}

internal enum class TaskChartAxisMode {
    ALL_SCALES,
    FOCUSED_SCALE,
}

internal data class TaskLayoutDecision(
    val navigation: TaskNavigationMode,
    val twoPaneAllowed: Boolean,
    val toolsInTopBar: Boolean,
)

internal object TaskLayoutPolicy {
    const val RAIL_WIDTH_DP = 72

    private const val CONTENT_HORIZONTAL_PADDING_DP = 32
    private const val PANE_GAP_DP = 24
    private const val RAIL_NAVIGATION_WIDTH_DP = 600
    private const val TWO_PANE_WINDOW_WIDTH_DP = 840
    private const val TWO_PANE_MINIMUM_HEIGHT_DP = 360
    private const val TWO_PANE_MINIMUM_CONTENT_WIDTH_DP = 320

    fun decide(
        windowWidthDp: Int,
        availableHeightDp: Int,
        primaryMinimumWidthDp: Int = 320,
        secondaryMinimumWidthDp: Int = 280,
        twoPaneMinimumHeightDp: Int = TWO_PANE_MINIMUM_HEIGHT_DP,
    ): TaskLayoutDecision {
        require(windowWidthDp > 0) { "windowWidthDp must be positive" }
        require(availableHeightDp >= 0) { "availableHeightDp must be non-negative" }
        require(primaryMinimumWidthDp >= 0) { "primaryMinimumWidthDp must be non-negative" }
        require(secondaryMinimumWidthDp >= 0) { "secondaryMinimumWidthDp must be non-negative" }
        require(twoPaneMinimumHeightDp >= 0) { "twoPaneMinimumHeightDp must be non-negative" }

        val navigation = if (windowWidthDp >= RAIL_NAVIGATION_WIDTH_DP) {
            TaskNavigationMode.RAIL
        } else {
            TaskNavigationMode.BOTTOM
        }
        val contentWidthDp = windowWidthDp - if (navigation == TaskNavigationMode.RAIL) {
            RAIL_WIDTH_DP
        } else {
            0
        }
        val requiredContentWidthDp = primaryMinimumWidthDp.toLong() +
            secondaryMinimumWidthDp.toLong() +
            PANE_GAP_DP +
            CONTENT_HORIZONTAL_PADDING_DP
        val twoPaneAllowed = windowWidthDp >= TWO_PANE_WINDOW_WIDTH_DP &&
            contentWidthDp.toLong() >= requiredContentWidthDp &&
            availableHeightDp >= twoPaneMinimumHeightDp

        return TaskLayoutDecision(
            navigation = navigation,
            twoPaneAllowed = twoPaneAllowed,
            toolsInTopBar = navigation == TaskNavigationMode.BOTTOM ||
                availableHeightDp < TWO_PANE_MINIMUM_HEIGHT_DP,
        )
    }

    fun chartAxes(availableWidthDp: Int, measuredAxisGuttersDp: Int): TaskChartAxisMode {
        require(availableWidthDp >= 0) { "availableWidthDp must be non-negative" }
        require(measuredAxisGuttersDp >= 0) { "measuredAxisGuttersDp must be non-negative" }

        return if (availableWidthDp.toLong() - measuredAxisGuttersDp >= TWO_PANE_MINIMUM_CONTENT_WIDTH_DP) {
            TaskChartAxisMode.ALL_SCALES
        } else {
            TaskChartAxisMode.FOCUSED_SCALE
        }
    }
}
