package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import java.io.File

/** Native chart fixtures, never writes fake samples to the journal or controls a machine. */
internal class TwoCupChartChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    fun run(expectedFontScale: Float) {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        val host = MainPageTestHost.launch(test)
        val activity = host.activity
        try {
            test.waitForIdleSync()
            host.active("root:BREW")
            main {
                check(kotlin.math.abs(activity.resources.configuration.fontScale - expectedFontScale) < .01f)
                val first = listOf(0L, 800L, 1600L, 4000L).mapIndexed { index, time ->
                    ShotPoint(time, 90, 999, index * 150, 9300, index * 100 - 100, 999, true)
                }
                val second = (0..5).map { index ->
                    ShotPoint(index * 800L, 60, 999, if (index < 3) index * 120 else (index - 3) * 120,
                        9500, index * 200, 999, true)
                }
                val chart = TwoCupChartView(activity).apply { firstPoints = first; secondPoints = second }
                val directory = File(test.targetContext.getExternalFilesDir(null), "two-cup-chart-checks").apply { mkdirs() }
                fun measure(heightDp: Int) {
                    chart.measure(View.MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, 320), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, heightDp), View.MeasureSpec.EXACTLY))
                    chart.layout(0, 0, chart.measuredWidth, chart.measuredHeight)
                }
                fun capture(name: String, requirePlot: Boolean = true) {
                    val bitmap = Bitmap.createBitmap(chart.width, chart.height, Bitmap.Config.ARGB_8888)
                    try {
                        chart.draw(Canvas(bitmap))
                        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                    if (requirePlot) {
                        val plot = chart.findViewWithTag<View>("comparison-plot")
                        check(plot.visibility == View.VISIBLE && chart.plotBounds.height() >= HoyiUi.dp(activity, 96))
                        val pixels = Bitmap.createBitmap(plot.width, plot.height, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = TrendChartTextChecks.TextBoundsCanvas(pixels,
                                TextView(activity).apply { textSize = 12f }.textSize, "two-cup-$name")
                            plot.draw(canvas)
                            val endLabel = String.format(java.util.Locale.ROOT, "%.1f s", chart.projection.timeEndMs / 1000.0)
                            check(canvas.labels >= 6 && chart.metric.unit in canvas.texts && endLabel in canvas.texts)
                        } finally { pixels.recycle() }
                    }
                }
                measure(320)
                for (metric in ChartMetric.entries) {
                    chart.metric = metric
                    measure(320)
                    val series = requireNotNull(chart.focusedSeries)
                    check(series.metric == metric && series.current.isNotEmpty() && series.reference.isNotEmpty())
                    check(series.current.flatten().all { it.value in series.range.low..series.range.high })
                    check(series.reference.flatten().all { it.value in series.range.low..series.range.high })
                    if (metric == ChartMetric.PRESSURE) {
                        check(series.current.size == 2 && series.current.first().first().value == 9f)
                        check(series.reference.first().first().value == 6f)
                    }
                    if (metric == ChartMetric.WATER) {
                        check(series.reference.size == 2 && series.current.flatten().last().value == 45f)
                    }
                    if (metric == ChartMetric.WEIGHT) check(series.range.low <= -1f)
                    check(chart.firstPoints == first && chart.secondPoints == second)
                    capture(metric.name.lowercase())
                }
                chart.metric = ChartMetric.WEIGHT
                chart.secondPoints = second.map { it.copy(weightHundredthsGram = null) }
                measure(320)
                check(chart.focusedSeries!!.reference.isEmpty() && chart.focusedSeries!!.current.isNotEmpty())
                capture("one-missing-weight")
                chart.firstPoints = first.map { it.copy(weightHundredthsGram = null) }
                measure(320)
                check(chart.focusedSeries == null && chart.plotBounds.isEmpty)
                check(chart.findViewWithTag<View>("comparison-plot-notice").visibility == View.VISIBLE)
                capture("both-missing-weight", false)
                chart.firstPoints = first; chart.secondPoints = second; chart.metric = ChartMetric.PRESSURE
                measure(220)
                check(chart.plotBounds.height() >= HoyiUi.dp(activity, 128)) {
                    "Comparison axes consumed too much of the compact plot"
                }
                capture("compact-readable")
                measure(80)
                check(chart.plotBounds.isEmpty && chart.findViewWithTag<View>("comparison-plot-notice").visibility == View.VISIBLE)
                capture("short-height", false)
                measure(320)
                capture("recovered")
                chart.firstPoints = second; chart.secondPoints = first
                measure(320)
                check(chart.focusedSeries!!.current.first().first().value == 6f && chart.focusedSeries!!.reference.first().first().value == 9f)
                capture("swapped")
            }
        } finally { main { activity.finish() }; test.waitForIdleSync() }
    }
}
