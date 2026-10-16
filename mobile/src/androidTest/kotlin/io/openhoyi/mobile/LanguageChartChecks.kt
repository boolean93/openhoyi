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
                ScaleTrendChartChecks.verify(context)
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
                verifyIncreasing(stages, context.getColor(R.color.chart_pressure), 20f, 20f, 18f, 28f, language,
                    listOf(R.color.stage_one, R.color.stage_two, R.color.stage_three, R.color.stage_four).map(context::getColor))
            }
        }
    }
    private fun verifyIncreasing(view: View, color: Int, leftDp: Float, rightDp: Float,
        topDp: Float, bottomDp: Float, language: AppLanguage, stageColors: List<Int> = emptyList()) {
        val density = view.resources.displayMetrics.density
        val width = (600 * density).toInt()
        val height = (340 * density).toInt()
        view.layoutDirection = if (language.rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val target = if (view is ShotChartView) view.findViewWithTag<View>("chart-plot") else view
        val drawWidth = target.width
        val drawHeight = target.height
        val bitmap = Bitmap.createBitmap(drawWidth, drawHeight, Bitmap.Config.ARGB_8888)
        try {
            target.draw(Canvas(bitmap))
            val pixels = IntArray(drawWidth * drawHeight)
            bitmap.getPixels(pixels, 0, drawWidth, 0, 0, drawWidth, drawHeight)
            val left = (if (view is ShotChartView) 90f else leftDp) * density
            val right = drawWidth - (if (view is ShotChartView) 60f else rightDp) * density
            val top = ((if (view is ShotChartView) 32f else topDp) * density).toInt() + 1
            val bottom = (drawHeight - (if (view is ShotChartView) 42f else bottomDp) * density).toInt() - 1
            val seriesColors = if (stageColors.isEmpty()) setOf(color) else stageColors.toSet()
            if (stageColors.isNotEmpty()) {
                stageColors.forEachIndexed { index, expected ->
                    val x = (left + (right - left) * (index + .5f) / stageColors.size).toInt()
                    check((top..bottom).any { y -> pixels[y * drawWidth + x] == expected }) {
                        "Stage ${index + 1} color missing from its actual rendered column in ${language.tag}"
                    }
                }
            }
            fun meanY(low: Float, high: Float): Double {
                var total = 0L
                var count = 0
                for (x in (left + (right - left) * low).toInt()..(left + (right - left) * high).toInt())
                    for (y in top..bottom) if (pixels[y * drawWidth + x] in seriesColors) { total += y; count++ }
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
