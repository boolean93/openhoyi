package io.openhoyi.mobile

import android.view.View
import android.widget.LinearLayout
import java.util.Locale

/** The text wraps naturally at every app font size and scrolls with the existing details. */
internal class ComparisonDifferenceSummary(
    private val activity: ThemedActivity, style: TaskProductVisualStyle,
) : LinearLayout(activity) {
    private val rows = ComparisonDifferences.Metric.entries.associateWith { metric ->
        style.text("", TaskProductVisualStyle.auxiliary, "comparison-difference-${metric.name}")
    }

    init {
        orientation = VERTICAL
        tag = "comparison-difference-summary"
        val padding = style.dp(12)
        setPadding(padding, padding, padding, padding)
        background = style.shape(style.surface, true)
        val title = style.text(activity.getString(R.string.comparison_difference_title), TaskProductVisualStyle.body)
        if (android.os.Build.VERSION.SDK_INT >= 28) title.isAccessibilityHeading = true
        style.add(this, title)
        rows.values.forEach { style.add(this, it, 4) }
        visibility = View.GONE
    }

    sealed interface Cup {
        data class Readings(val values: Map<ComparisonDifferences.Metric, ComparisonDifferences.Reading>) : Cup
        data class Pending(@androidx.annotation.StringRes val message: Int) : Cup
    }

    fun update(first: Cup?, second: Cup?) {
        visibility = if (first != null && second != null) View.VISIBLE else View.GONE
        if (first == null || second == null) return
        rows.forEach { (metric, row) ->
            val firstReading = (first as? Cup.Readings)?.values?.getValue(metric)
            val secondReading = (second as? Cup.Readings)?.values?.getValue(metric)
            val difference = if (firstReading != null && secondReading != null)
                ComparisonDifferences.difference(firstReading, secondReading) else null
            val label = activity.getString(when (metric) {
                ComparisonDifferences.Metric.PRESSURE -> R.string.comparison_difference_peak_pressure
                ComparisonDifferences.Metric.WATER -> R.string.comparison_difference_last_water
                ComparisonDifferences.Metric.TEMPERATURE -> R.string.comparison_difference_median_temperature
                ComparisonDifferences.Metric.NET_WEIGHT -> R.string.comparison_difference_last_net_weight
            })
            val value = when {
                difference == null -> activity.getString(R.string.comparison_difference_unavailable,
                    describe(first, metric), describe(second, metric))
                difference.displayUnits == 0L -> activity.getString(R.string.comparison_difference_equal)
                else -> activity.getString(if (difference.firstHigher) R.string.comparison_difference_a_higher
                    else R.string.comparison_difference_b_higher, format(difference.displayUnits, metric))
            }
            val line = activity.getString(R.string.comparison_difference_line, label, value)
            if (row.text.toString() != line) row.text = line
        }
    }

    private fun describe(cup: Cup, metric: ComparisonDifferences.Metric): String = when (cup) {
        is Cup.Pending -> activity.getString(cup.message)
        is Cup.Readings -> when (val reading = cup.values.getValue(metric)) {
            is ComparisonDifferences.Reading.Value -> format(reading.displayUnits, metric)
            is ComparisonDifferences.Reading.Missing -> activity.getString(when (reading.reason) {
                ComparisonDifferences.Unavailable.NOT_ENDED -> R.string.comparison_difference_not_ended
                ComparisonDifferences.Unavailable.NO_SAMPLES -> R.string.history_no_samples
                ComparisonDifferences.Unavailable.NO_BREWING -> R.string.comparison_difference_no_brewing
                ComparisonDifferences.Unavailable.WATER_RESET -> R.string.comparison_difference_water_reset
                ComparisonDifferences.Unavailable.NO_NET_WEIGHT -> R.string.comparison_difference_no_net_weight
                ComparisonDifferences.Unavailable.ESTIMATED -> R.string.comparison_difference_estimated
                ComparisonDifferences.Unavailable.NET_SEGMENTS -> R.string.comparison_difference_net_segments
            })
        }
    }

    private fun format(units: Long, metric: ComparisonDifferences.Metric): String {
        val value = String.format(Locale.ROOT, "%.${metric.decimals}f", units / if (metric.decimals == 1) 10.0 else 100.0)
        val unit = when (metric) {
            ComparisonDifferences.Metric.PRESSURE -> R.string.comparison_difference_bar
            ComparisonDifferences.Metric.WATER -> R.string.comparison_difference_ml
            ComparisonDifferences.Metric.TEMPERATURE -> R.string.comparison_difference_celsius
            ComparisonDifferences.Metric.NET_WEIGHT -> R.string.comparison_difference_grams
        }
        return activity.getString(unit, value)
    }
}
