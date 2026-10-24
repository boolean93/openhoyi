package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import java.io.File

/** Native component fixtures, not fabricated machine sessions or hardware evidence. */
internal class FourMetricChartChecks(private val test: Instrumentation) {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun onMain(action: () -> Unit) {
        var error: Throwable? = null
        test.runOnMainSync { try { action() } catch (failure: Throwable) { error = failure } }
        error?.let { throw it }
    }

    fun run() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        val activity = test.startActivitySync(Intent(test.targetContext, ExtractionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ExtractionActivity
        try {
            test.waitForIdleSync()
            val directory = File(test.targetContext.getExternalFilesDir(null), "four-metric-chart-checks").apply { mkdirs() }
            val samples = (0..30).map { index ->
                ShotPoint(index * 800L, if (index < 5) index * 18 else 90 - (index - 5),
                    999, index * 18, 9300 + index * 2, index * 120, 999, true)
            }
            val cases = listOf(
                Triple("machine-only", samples.map { it.copy(weightHundredthsGram = null) }, emptyList()),
                Triple("four-metrics", samples, emptyList()),
                Triple("gaps-and-reference", samples.filterIndexed { index, _ -> index !in 12..16 }.mapIndexed { index, point ->
                    point.copy(weightHundredthsGram = point.weightHundredthsGram.takeUnless { index in 5..7 || point.elapsedMs == samples.last().elapsedMs },
                        waterTenthsMl = if (index > 15) (index - 16) * 15 else point.waterTenthsMl)
                }, samples.map { it.copy(elapsedMs = it.elapsedMs * 2) })
            )
            onMain {
                val actual = ExtractionActivity::class.java.getDeclaredField("chart").apply { isAccessible = true }.get(activity) as View
                check(actual.width > 0 && actual.height > 0)
                for ((name, points, reference) in cases) {
                    val probe = ExtractionOverlayView(activity)
                    probe.points = points
                    probe.reference = reference
                    probe.measure(View.MeasureSpec.makeMeasureSpec(actual.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(actual.height, View.MeasureSpec.EXACTLY))
                    probe.layout(0, 0, actual.width, actual.height)
                    val expected = if (name == "machine-only") listOf("PRESSURE", "WATER", "TEMPERATURE")
                        else listOf("PRESSURE", "WATER", "WEIGHT", "TEMPERATURE")
                    val controls = descendants(probe).filter { it.tag?.toString()?.startsWith("chart-metric:") == true }
                    check(controls.map { it.tag } == expected.map { "chart-metric:$it" }) {
                        "$name: expected usable four-metric legend, got ${controls.map { it.tag }}"
                    }
                    for (control in controls) {
                        check(control.isClickable && control.width >= HoyiUi.dp(activity, 48) && control.height >= HoyiUi.dp(activity, 48))
                        check(!control.contentDescription.isNullOrBlank())
                        val metric = ChartMetric.valueOf(control.tag.toString().substringAfter(':'))
                        val value = ChartInspection.values(points.last())[metric]
                        val reading = value?.let { java.util.Locale.ROOT.let { locale ->
                            String.format(locale, "%.1f", it)
                        } } ?: "—"
                        check((control as android.widget.TextView).text.lineSequence().first().endsWith("$reading ${metric.unit}")) {
                            "$name: legend must show the latest observation, not carry a missing value forward"
                        }
                        check(control.performClick() && control.isSelected) { "$name: legend cannot focus ${control.tag}" }
                        // Native font weight/selection can request layout; emulate the next real UI frame.
                        probe.measure(View.MeasureSpec.makeMeasureSpec(actual.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(actual.height, View.MeasureSpec.EXACTLY))
                        probe.layout(0, 0, actual.width, actual.height)
                        LanguageUiLayoutChecks.textFits(control as android.widget.TextView, "$name selected legend")
                    }
                    val bitmap = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
                    try {
                        probe.draw(android.graphics.Canvas(bitmap))
                        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                }
                val configuration = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = 1.3f }
                val compactContext = android.view.ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.MobileTheme)
                val compact = ExtractionOverlayView(compactContext).apply { points = samples }
                val compactWidth = (320 * compact.resources.displayMetrics.density).toInt()
                val compactHeight = (100 * compact.resources.displayMetrics.density).toInt()
                compact.measure(View.MeasureSpec.makeMeasureSpec(compactWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(compactHeight, View.MeasureSpec.EXACTLY))
                compact.layout(0, 0, compactWidth, compactHeight)
                val notice = descendants(compact).single { it.tag == "chart-compact-notice" } as android.widget.TextView
                check(notice.visibility == View.VISIBLE && notice.text.isNotBlank()) { "Short plot silently disappears" }
                check(descendants(compact).single { it.tag == "chart-plot" }.visibility == View.GONE) { "Short chart draws ambiguous clipped axes" }
                LanguageUiLayoutChecks.textFits(notice, "short chart fontScale=1.3")
                val noticeBounds = android.graphics.Rect(0, 0, notice.width, notice.height)
                compact.offsetDescendantRectToMyCoords(notice, noticeBounds)
                check(noticeBounds.top >= 0 && noticeBounds.bottom <= compact.height && noticeBounds.left >= 0 && noticeBounds.right <= compact.width)
                val compactBitmap = Bitmap.createBitmap(compactWidth, compactHeight, Bitmap.Config.ARGB_8888)
                try {
                    compact.draw(android.graphics.Canvas(compactBitmap))
                    File(directory, "short-height-font-1.3.png").outputStream().use { check(compactBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { compactBitmap.recycle() }
                val restoredHeight = (300 * compact.resources.displayMetrics.density).toInt()
                compact.measure(View.MeasureSpec.makeMeasureSpec(compactWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(restoredHeight, View.MeasureSpec.EXACTLY))
                compact.layout(0, 0, compactWidth, restoredHeight)
                check(notice.visibility == View.GONE) { "Short-height notice did not clear after resize" }
                val restoredPlot = descendants(compact).single { it.tag == "chart-plot" }
                check(restoredPlot.visibility == View.VISIBLE && restoredPlot.height >= HoyiUi.dp(activity, 64)) { "Plot did not recover after resize" }
            }
            test.sendStatus(0, Bundle().apply { putString("stream", "FOUR_METRIC_COMPONENT_FIXTURES machine-only,four-metrics,gaps-and-reference,short-height-font-1.3\n") })
        } finally { onMain { activity.finish() }; test.waitForIdleSync() }
    }
}
