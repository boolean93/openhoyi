package io.openhoyi.mobile

import android.app.Instrumentation
import android.os.SystemClock
import android.view.View
import android.widget.Spinner

/** Real controls and actual cup identities. No journal or sample mutations. */
internal object TwoCupComparisonPageChecks {
    fun run(test: Instrumentation, activity: BrewComparisonActivity) {
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
        val selector = root.findViewWithTag<Spinner>("comparison-metric")
        val swap = root.findViewWithTag<View>("comparison-swap")
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
            originalMetric = selector.selectedItemPosition
            if (activity.resources.configuration.let {
                it.screenWidthDp >= 1000 && it.screenHeightDp >= 600 && it.fontScale <= 1.05f
            }) {
                listOf(selector, swap, root.findViewWithTag<View>("comparison-first"),
                    root.findViewWithTag<View>("comparison-second")).forEach { control ->
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
            for (metric in ChartMetric.entries) {
                main { selector.setSelection(metric.ordinal) }
                test.waitForIdleSync()
                await { chart.metric == metric && !chart.isLayoutRequested }
                main {
                    check(chart.firstPoints == firstSamples && chart.secondPoints == secondSamples)
                    check(chart.focusedSeries?.metric == metric && chart.plotBounds.height() >= HoyiUi.dp(activity, 96))
                }
            }
            // The real placeholder must clear identity and data, not only its displayed chip.
            val firstSelector = root.findViewWithTag<Spinner>("comparison-first")
            var originalFirstPosition = 0
            main { originalFirstPosition = firstSelector.selectedItemPosition; firstSelector.setSelection(0) }
            test.waitForIdleSync()
            await { id("firstId") == null && !chart.isLayoutRequested }
            main {
                check(chart.firstPoints.isEmpty() && chart.secondPoints.isEmpty() && chart.comparisonMessage != null)
                firstSelector.setSelection(originalFirstPosition)
            }
            test.waitForIdleSync()
            await { id("firstId") == first && chart.firstPoints == firstSamples && chart.secondPoints == secondSamples }
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
                check(root.findViewWithTag<Spinner>("comparison-first").selectedItemPosition !=
                    root.findViewWithTag<Spinner>("comparison-second").selectedItemPosition)
            }
        } finally {
            main {
                if (id("firstId") == second && id("secondId") == first) check(swap.performClick())
                val firstSelector = root.findViewWithTag<Spinner>("comparison-first")
                if (id("firstId") == null) {
                    val entries = (activity.application as MobileApplication).journalResult.getOrThrow().entries()
                    firstSelector.setSelection(entries.indexOfFirst { it.observation.id == first } + 1)
                }
                selector.setSelection(originalMetric)
            }
            test.waitForIdleSync()
            await { id("firstId") == first && id("secondId") == second && chart.metric.ordinal == originalMetric }
        }
    }
}
