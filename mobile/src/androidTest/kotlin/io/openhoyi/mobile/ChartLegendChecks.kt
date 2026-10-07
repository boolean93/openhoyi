package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF

/** Measures actual product Canvas text with Android fonts; no screenshot, Service or BLE. */
internal class ChartLegendChecks(private val test: Instrumentation) {
    private data class Label(val text: String, val color: Int, val bounds: RectF)
    private class RecordingCanvas(bitmap: Bitmap, private val colors: Set<Int>, private val axisColor: Int) : Canvas(bitmap) {
        var plotTop: Float? = null
        var plotBottom: Float? = null
        var renderedSeries = 0
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            if (paint.color == axisColor && startX == stopX) {
                plotTop = minOf(startY, stopY)
                plotBottom = maxOf(startY, stopY)
            }
            super.drawLine(startX, startY, stopX, stopY, paint)
        }
        override fun drawPath(path: Path, paint: Paint) {
            if (paint.color in colors && !path.isEmpty) renderedSeries++
            super.drawPath(path, paint)
        }
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
                labels += Label(text, paint.color, RectF(x - offset + ink.left, y + ink.top,
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
                            val canvas = RecordingCanvas(bitmap, colors, context.getColor(R.color.chart_axis))
                            chart.draw(canvas)
                            val expected = mapOf(
                                context.getColor(R.color.chart_pressure) to context.getString(R.string.chart_pressure_legend, "15"),
                                context.getColor(R.color.chart_water) to context.getString(R.string.chart_water_legend, "10"),
                                context.getColor(R.color.chart_coffee) to context.getString(R.string.chart_scale_flow_legend, "10"),
                                context.getColor(R.color.chart_weight) to context.getString(R.string.chart_weight_legend, "50"),
                                context.getColor(R.color.chart_temperature) to context.getString(R.string.chart_temperature_legend, "80", "100"),
                            )
                            val actual = canvas.labels.groupBy(Label::color).mapValues { (_, pieces) ->
                                pieces.joinToString("") { it.text }
                            }
                            check(actual == expected) { "Chart legend name, range or unit lost: ${language.tag}/$widthDp/$dark" }
                            canvas.labels.forEach { label ->
                                check(label.bounds.left >= 0 && label.bounds.right <= width &&
                                    label.bounds.top >= 0 && label.bounds.bottom <= height) {
                                    "Chart legend clipped: ${language.tag}/$widthDp/$dark ${label.text} ${label.bounds}"
                                }
                            }
                            val plotTop = checkNotNull(canvas.plotTop) { "Chart plot missing: ${language.tag}/$widthDp/$dark" }
                            val plotBottom = checkNotNull(canvas.plotBottom)
                            check(plotBottom - plotTop >= 48 * density && canvas.renderedSeries == 5) {
                                "Chart legend displaced plot or series: ${language.tag}/$widthDp/$dark"
                            }
                            check(plotTop >= canvas.labels.maxOf { it.bounds.bottom } + 14 * density - 1) {
                                "Chart legend intrudes on plot: ${language.tag}/$widthDp/$dark"
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
