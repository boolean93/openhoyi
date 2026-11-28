package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.TextView

/** Mock-only layout smoke check, no machine/service operations. Restores the display preference. */
internal class FixedChartAxesChecks(private val test: Instrumentation) {
    fun run() {
        check(BuildConfig.MOCK_MODE)
        var failure: Throwable? = null
        test.runOnMainSync {
            val prefs = test.targetContext.getSharedPreferences(FixedChartAxes.PREFERENCES, Context.MODE_PRIVATE)
            val prior = prefs.all.toMap()
            val series = test.targetContext.getSharedPreferences("chart_series", Context.MODE_PRIVATE)
            val priorSeries = series.getStringSet("visible", null)?.toSet()
            try {
                check(prefs.edit().putBoolean(FixedChartAxes.KEY, true)
                    .putInt(FixedChartAxes.INITIAL_TIME_SECONDS_KEY, 60).commit())
                check(series.edit().remove("visible").commit())
                for ((language, font, dark, widthDp) in listOf(
                    Quad(AppLanguage.CHINESE, AppFontSize.LARGE, false, 320),
                    Quad(AppLanguage.ENGLISH, AppFontSize.LARGE, true, 320),
                    Quad(AppLanguage.CHINESE, AppFontSize.MEDIUM, true, 800))) {
                    val context = AppLanguageContext.wrap(test.targetContext, language, dark, font)
                    val chart = ExtractionOverlayView(context, ExtractionHistoryVisualStyle(context)).apply {
                        liveCompactChrome = true
                        points = listOf(ShotPoint(61000, 130, 0, 1100, 7900, 6500, brewing = true,
                            beverageHundredthsGram = 6500))
                    }
                    draw(chart, widthDp)
                    val notice = chart.findViewWithTag<TextView>("product/extraction/fixed-axis-notice")
                    check(notice.visibility == View.VISIBLE && notice.height > 0)
                    check(notice.text.contains(context.getString(R.string.task_chart_water)))
                    check(notice.layout.getLineBottom(notice.lineCount - 1) <= notice.height - notice.paddingBottom)
                    check(notice.bottom <= chart.height)
                    check(chart.metricReadout(ChartMetric.PRESSURE) == "13.0 bar")
                    check(chart.visualEvidence.length() > 0)
                }
                checkExpandingTime(AppLanguageContext.wrap(test.targetContext, AppLanguage.ENGLISH,
                    false, AppFontSize.MEDIUM))
            } catch (error: Throwable) { failure = error }
            finally {
                restoreMockPreferences(prefs, prior)
                check(series.edit().putStringSet("visible", priorSeries).commit())
            }
        }
        failure?.let { throw it }
    }

    private fun checkExpandingTime(context: Context) {
        val chart = ExtractionOverlayView(context, ExtractionHistoryVisualStyle(context)).apply {
            liveCompactChrome = true
        }
        for ((lastSecond, endLabel) in listOf(59 to "60.0 s", 65 to "65.0 s")) {
            chart.points = (0..lastSecond).map { second ->
                ShotPoint(second * 1_000L, 60 + second, 0, second * 10, 9200, null, brewing = true)
            }
            val drawnText = draw(chart, 800)
            check(chart.metricReadout(ChartMetric.PRESSURE) in drawnText) { "Pressure endpoint label not drawn" }
            check(chart.findViewWithTag<TextView>("product/extraction/fixed-axis-notice").visibility == View.GONE)
            val evidence = chart.visualEvidence
            val operations = (0 until evidence.length()).map(evidence::getJSONObject)
            val timeTicks = operations.filter { it.optString("role") == "time-tick" }.map { it.getString("text") }
            check("0" in timeTicks && endLabel in timeTicks) { "Wrong time labels: $timeTicks" }
            val plot = operations.single { it.optString("operation") == "plot" }
            val bounds = plot.getJSONArray("boundsPx")
            val left = bounds.getDouble(0)
            val right = bounds.getDouble(2)
            val end = plot.getLong("projectionTimeEndMs")
            check(end == maxOf(60, lastSecond) * 1_000L)
            val path = operations.single {
                it.optString("operation") == "path" && it.optString("metric") == "PRESSURE" && !it.getBoolean("reference")
            }.getJSONArray("verticesPx")
            val first = path.getJSONArray(0)
            val last = path.getJSONArray(path.length() - 1)
            val observed = operations.single { it.optString("role") == "observed-through" }.getJSONArray("endpointsPx")
            val expectedX = left + (right - left) * lastSecond * 1_000L / end
            check(path.length() == lastSecond + 1 && first.getDouble(0) == left)
            check(last.getDouble(1) in bounds.getDouble(1)..bounds.getDouble(3) && last.getDouble(1) < first.getDouble(1))
            check(last.getDouble(0) in left..(right + .5) && kotlin.math.abs(last.getDouble(0) - expectedX) < .5)
            check(observed.getDouble(0) == last.getDouble(0) && observed.getDouble(2) == last.getDouble(0))
        }
    }

    private fun draw(chart: ExtractionOverlayView, widthDp: Int): List<String> {
        val width = (widthDp * chart.resources.displayMetrics.density).toInt()
        val height = chart.liveHeightForPlotAspect(width, .6f).coerceAtLeast(chart.minimumLiveHeight(width))
        chart.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        chart.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val drawnText = mutableListOf<String>()
        val canvas = object : Canvas(bitmap) {
            override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
                drawnText += text
                super.drawText(text, x, y, paint)
            }
        }
        try { chart.draw(canvas) } finally { bitmap.recycle() }
        return drawnText
    }

    private data class Quad(val language: AppLanguage, val font: AppFontSize, val dark: Boolean, val width: Int)
}
