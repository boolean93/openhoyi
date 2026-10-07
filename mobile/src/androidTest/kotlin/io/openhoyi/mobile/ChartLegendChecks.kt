package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/** Measures actual product Canvas text with Android fonts; no screenshot, Service or BLE. */
internal class ChartLegendChecks(private val test: Instrumentation) {
    private data class Label(val text: String, val bounds: RectF)
    private class RecordingCanvas(bitmap: Bitmap, private val colors: Set<Int>) : Canvas(bitmap) {
        val labels = mutableListOf<Label>()
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            if (paint.color in colors) {
                val ink = Rect()
                paint.getTextBounds(text, 0, text.length, ink)
                val advance = paint.measureText(text)
                val offset = when (paint.textAlign) {
                    Paint.Align.CENTER -> advance / 2
                    Paint.Align.RIGHT -> advance
                    else -> 0f
                }
                labels += Label(text, RectF(x - offset + ink.left, y + ink.top,
                    x - offset + ink.right, y + ink.bottom))
            }
            super.drawText(text, x, y, paint)
        }
    }
    fun run() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        var failure: Throwable? = null
        test.runOnMainSync {
            try {
                for (language in AppLanguage.entries) for (dark in listOf(false, true))
                    for (widthDp in listOf(240, 300, 540, 940)) {
                        val context = AppLanguageContext.wrap(test.targetContext, language, dark)
                        val density = context.resources.displayMetrics.density
                        val width = (widthDp * density).toInt()
                        val height = (220 * density).toInt()
                        val colors = listOf(R.color.chart_pressure, R.color.chart_water, R.color.chart_coffee,
                            R.color.chart_weight, R.color.chart_temperature).map(context::getColor).toSet()
                        check(colors.size == 5)
                        val chart = ShotChartView(context).apply {
                            points = listOf(ShotPoint(0, 20, 0, 0, 9200, 0, 0),
                                ShotPoint(1000, 100, 30, 30, 9200, 3000, 200))
                            layout(0, 0, width, height)
                        }
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = RecordingCanvas(bitmap, colors)
                            chart.draw(canvas)
                            check(canvas.labels.size == 5) { "Missing chart legend: ${language.tag}/$widthDp/$dark" }
                            canvas.labels.forEach { label ->
                                check(label.bounds.left >= 0 && label.bounds.right <= width &&
                                    label.bounds.top >= 0 && label.bounds.bottom <= height) {
                                    "Chart legend clipped: ${language.tag}/$widthDp/$dark ${label.text} ${label.bounds}"
                                }
                            }
                            canvas.labels.forEachIndexed { i, first ->
                                canvas.labels.drop(i + 1).forEach { second ->
                                    check(!RectF.intersects(first.bounds, second.bounds)) {
                                        "Chart legends overlap: ${language.tag}/$widthDp/$dark ${first.text} / ${second.text}"
                                    }
                                }
                            }
                        } finally { bitmap.recycle() }
                    }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
