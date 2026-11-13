package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView

/** Mock-only layout smoke check, no machine/service operations. Restores the display preference. */
internal class FixedChartAxesChecks(private val test: Instrumentation) {
    fun run() {
        check(BuildConfig.MOCK_MODE)
        var failure: Throwable? = null
        test.runOnMainSync {
            val prefs = test.targetContext.getSharedPreferences(FixedChartAxes.PREFERENCES, Context.MODE_PRIVATE)
            val present = prefs.contains(FixedChartAxes.KEY)
            val prior = prefs.getBoolean(FixedChartAxes.KEY, false)
            try {
                check(prefs.edit().putBoolean(FixedChartAxes.KEY, true).commit())
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
                    val density = context.resources.displayMetrics.density
                    val width = (widthDp * density).toInt()
                    val height = chart.liveHeightForPlotAspect(width, .6f).coerceAtLeast(chart.minimumLiveHeight())
                    chart.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    chart.layout(0, 0, width, height)
                    val notice = chart.findViewWithTag<TextView>("product/extraction/fixed-axis-notice")
                    check(notice.visibility == View.VISIBLE && notice.height > 0)
                    check(notice.layout.getLineBottom(notice.lineCount - 1) <= notice.height - notice.paddingBottom)
                    check(notice.bottom <= height)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    chart.draw(Canvas(bitmap))
                    bitmap.recycle()
                    check(chart.metricReadout(ChartMetric.PRESSURE) == "13.0 bar")
                    check(chart.visualEvidence.length() > 0)
                }
            } catch (error: Throwable) { failure = error }
            finally {
                val edit = prefs.edit()
                if (present) edit.putBoolean(FixedChartAxes.KEY, prior) else edit.remove(FixedChartAxes.KEY)
                check(edit.commit())
            }
        }
        failure?.let { throw it }
    }
    private data class Quad(val language: AppLanguage, val font: AppFontSize, val dark: Boolean, val width: Int)
}
