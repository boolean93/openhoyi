package io.openhoyi.mobile

import android.app.Instrumentation
import android.os.SystemClock
import android.view.View
import android.widget.Button

/** Real controls and actual cup identities. No journal or sample mutations. */
internal object TwoCupComparisonPageChecks {
    fun run(test: Instrumentation, activity: BrewComparisonActivity, captureMetric: (ChartMetric) -> Unit) {
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
            failure?.let { throw it }
        }
        fun id(name: String) = BrewComparisonActivity::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.get(activity) as String?
        fun await(condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (true) {
                var ready = false
                main { ready = condition() }
                if (ready) return
                check(SystemClock.elapsedRealtime() < deadline) { "Two-cup control did not settle" }
                SystemClock.sleep(25)
            }
        }
        val root = activity.window.decorView
        val chart = root.findViewWithTag<TwoCupChartView>("two-cup-chart")
        fun metricButton(metric: ChartMetric) = root.findViewWithTag<android.widget.Button>("comparison-metric-${metric.name}")
        fun selectMetric(metric: ChartMetric) { check(metricButton(metric).performClick()) }
        val swap = root.findViewWithTag<View>("comparison-swap")
        val changeSecond = root.findViewWithTag<View>("comparison-change-second")
        fun picker() = (activity.supportFragmentManager.findFragmentByTag(HistoryCupPicker.TAG) as HistoryCupPicker)
            .requireDialog().window!!.decorView
        fun summary(column: Int) = (0..2).map {
            val row = root.findViewWithTag<android.widget.LinearLayout>("comparison-summary-row-$it")
            (row.getChildAt(column + 1) as android.widget.TextView).text.toString()
        }
        val differences = root.findViewWithTag<ComparisonDifferenceSummary>("comparison-difference-summary")
        fun difference(metric: ComparisonDifferences.Metric) =
            root.findViewWithTag<android.widget.TextView>("comparison-difference-${metric.name}")
        fun assertDifference(label: Int, description: Int, metric: ComparisonDifferences.Metric, vararg args: String) {
            val line = difference(metric)
            val expected = activity.getString(R.string.comparison_difference_line, activity.getString(label),
                activity.getString(description, *args))
            check(line.text.toString() == expected) { "${metric.name}: expected $expected, got ${line.text}" }
        }
        fun assertDifferences(swapped: Boolean) {
            val aHigher = if (swapped) R.string.comparison_difference_b_higher else R.string.comparison_difference_a_higher
            assertDifference(R.string.comparison_difference_peak_pressure, aHigher, ComparisonDifferences.Metric.PRESSURE, "0.5 bar")
            assertDifference(R.string.comparison_difference_last_water, aHigher, ComparisonDifferences.Metric.WATER, "0.4 mL")
            assertDifference(R.string.comparison_difference_median_temperature, aHigher, ComparisonDifferences.Metric.TEMPERATURE, "0.5 °C")
            val noNetWeight = activity.getString(R.string.comparison_difference_no_net_weight)
            assertDifference(R.string.comparison_difference_last_net_weight, R.string.comparison_difference_unavailable,
                ComparisonDifferences.Metric.NET_WEIGHT, noNetWeight, noNetWeight)
        }
        var originalSummaries = emptyList<List<String>>()
        var first: String? = null
        var second: String? = null
        var originalMetric = 0
        var firstSamples = emptyList<ShotPoint>()
        var secondSamples = emptyList<ShotPoint>()
        main {
            first = id("firstId"); second = id("secondId")
            check(first != null && second != null && first != second)
            firstSamples = chart.firstPoints; secondSamples = chart.secondPoints
            check(firstSamples.size >= 2 && secondSamples.size >= 2)
            originalSummaries = listOf(summary(0), summary(1))
            originalMetric = chart.metric.ordinal
            assertDifferences(swapped = false)
            check(differences.isShown)
            if (activity.resources.configuration.let {
                it.screenWidthDp >= 1000 && it.screenHeightDp >= 600 && it.fontScale <= 1.05f
            }) {
                (historicalMetrics.map(::metricButton) + listOf(swap, changeSecond)).forEach { control ->
                    val bounds = android.graphics.Rect()
                    check(control.isShown && control.getGlobalVisibleRect(bounds) &&
                        bounds.width() == control.width && bounds.height() == control.height && control.height > 0) {
                        "Comparison toolbar control clipped: ${control.tag} $bounds / ${control.width}x${control.height}"
                    }
                }
                check(chart.width >= root.width * .8f) { "Comparison plot must span the landscape task area" }
            }
        }
        try {
            for (metric in historicalMetrics) {
                main { selectMetric(metric) }
                test.waitForIdleSync()
                await { chart.metric == metric && !chart.isLayoutRequested }
                main {
                    check(chart.firstPoints == firstSamples && chart.secondPoints == secondSamples)
                    check(metricButton(metric).isSelected && metricButton(metric).height >= HoyiUi.dp(activity, 48))
                    check(chart.focusedSeries?.metric == metric && chart.plotBounds.height() >= HoyiUi.dp(activity, 96))
                    val series = requireNotNull(chart.focusedSeries)
                    check(series.current.flatten().isNotEmpty() && series.reference.flatten().isNotEmpty() &&
                        series.current != series.reference) { "Distinct two-cup fixture collapsed for $metric" }
                }
                captureMetric(metric)
            }
            main { check(changeSecond.performClick()) }
            test.waitForIdleSync()
            main {
                val dialog = picker()
                val candidates = dialog.findViewWithTag<android.widget.ListView>("history-cup-options")
                val entries = (0 until candidates.count).map { candidates.getItemAtPosition(it) as BrewJournal.Observation }
                check(entries.none { it.id == first })
                candidates.setSelection(entries.indexOfFirst { it.id == second }.also { check(it >= 0) })
            }
            test.waitForIdleSync()
            main {
                val dialog = picker()
                val confirm = dialog.findViewWithTag<Button>("history-cup-confirm")
                check(!confirm.isEnabled)
                check(dialog.findViewWithTag<View>("history-cup-option-$first") == null)
                check(dialog.findViewWithTag<View>("history-cup-option-$second").performClick())
                check(confirm.isEnabled && id("secondId") == second)
                check(dialog.findViewWithTag<Button>("history-cup-cancel").performClick())
            }
            test.waitForIdleSync()
            await { id("firstId") == first && id("secondId") == second &&
                chart.firstPoints == firstSamples && chart.secondPoints == secondSamples }
            main {
                swap.requestRectangleOnScreen(android.graphics.Rect(0, 0, swap.width, swap.height), true)
            }
            test.waitForIdleSync()
            main {
                val bounds = android.graphics.Rect()
                check(swap.getGlobalVisibleRect(bounds) && bounds.height() == swap.height && bounds.width() == swap.width)
                check(swap.isEnabled && swap.isClickable && swap.height >= HoyiUi.dp(activity, 48) && swap.performClick())
            }
            test.waitForIdleSync()
            await { id("firstId") == second && id("secondId") == first && !chart.isLayoutRequested }
            main {
                check(chart.firstPoints == secondSamples && chart.secondPoints == firstSamples)
                assertDifferences(swapped = true)
                val lastDifference = difference(ComparisonDifferences.Metric.NET_WEIGHT)
                lastDifference.requestRectangleOnScreen(android.graphics.Rect(0, 0, lastDifference.width, lastDifference.height), true)
                check(summary(0) == originalSummaries[1] && summary(1) == originalSummaries[0]) { "Outcome table did not follow cup identities" }
            }
            test.waitForIdleSync()
            main {
                val lastDifference = difference(ComparisonDifferences.Metric.NET_WEIGHT)
                val bounds = android.graphics.Rect()
                check(lastDifference.getGlobalVisibleRect(bounds) && bounds.height() == lastDifference.height &&
                    bounds.width() == lastDifference.width && lastDifference.height > 0) {
                    "Last difference row is not fully scrollable into view: $bounds"
                }
            }
        } finally {
            main {
                if (id("firstId") == second && id("secondId") == first) check(swap.performClick())
                root.findViewWithTag<android.widget.ScrollView>("comparison-details-scroll").scrollTo(0, 0)
                selectMetric(historicalMetrics.single { it.ordinal == originalMetric })
            }
            test.waitForIdleSync()
            await { id("firstId") == first && id("secondId") == second && chart.metric.ordinal == originalMetric }
        }
    }
}
