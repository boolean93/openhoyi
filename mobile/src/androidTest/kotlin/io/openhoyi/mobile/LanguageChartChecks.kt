package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

/** Actual native Canvas rendering, without screenshots or changing service/controller state. */
internal class LanguageChartChecks(private val test: Instrumentation) {
    fun run() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        test.runOnMainSync {
            for (language in AppLanguage.entries) for (dark in listOf(false, true)) {
                val context = AppLanguageContext.wrap(test.targetContext, language, dark)
                val native = ShotChartView(context).apply {
                    points = listOf(ShotPoint(0, 20, 0, 0, 9200, null), ShotPoint(1000, 100, 0, 0, 9200, null))
                }
                val legacy = LegacyShotChartView(context).apply {
                    points = listOf(LegacyPoint(0.0, 2.0, 0.0, 0.0, 0.0), LegacyPoint(1.0, 10.0, 0.0, 0.0, 0.0))
                }
                // Targets are tenths of a bar on the new fixed 0..12 bar scale.
                // 2,4,6,8 meant only 0.2..0.8 bar, below the 20dp rise assertion.
                val stages = CurveStageView(context).apply { targets = listOf(20, 40, 60, 80) }
                verifyIncreasing(native, context.getColor(R.color.chart_pressure), 42f, 18f, 74f, 28f, language)
                verifyIncreasing(legacy, context.getColor(R.color.chart_pressure), 42f, 18f, 52f, 30f, language)
                verifyIncreasing(stages, context.getColor(R.color.mobile_accent), 20f, 20f, 18f, 28f, language)
            }
        }
    }
    private fun verifyIncreasing(view: View, color: Int, leftDp: Float, rightDp: Float,
        topDp: Float, bottomDp: Float, language: AppLanguage) {
        val density = view.resources.displayMetrics.density
        val width = (600 * density).toInt()
        val height = (340 * density).toInt()
        view.layoutDirection = if (language.rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val left = leftDp * density
            val right = width - rightDp * density
            val top = (topDp * density).toInt() + 1
            val bottom = (height - bottomDp * density).toInt() - 1
            fun meanY(low: Float, high: Float): Double {
                var total = 0L
                var count = 0
                for (x in (left + (right - left) * low).toInt()..(left + (right - left) * high).toInt())
                    for (y in top..bottom) if (pixels[y * width + x] == color) { total += y; count++ }
                check(count > 0) { "No rendered series pixels for ${view.javaClass.simpleName} ${language.tag}" }
                return total.toDouble() / count
            }
            // The fixture rises with time/stage. Its right-side trace must remain higher on screen in RTL too.
            check(meanY(.1f, .3f) - meanY(.7f, .9f) > 20 * density) {
                "Native scientific ordering reversed for ${view.javaClass.simpleName} ${language.tag}"
            }
        } finally { bitmap.recycle() }
    }
}
