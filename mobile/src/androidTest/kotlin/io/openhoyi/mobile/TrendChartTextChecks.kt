package io.openhoyi.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** Draw the production Canvas view, checking actual text calls rather than child TextViews. */
internal object TrendChartTextChecks {
    fun run(activity: ExtractionActivity, fixture: String) {
        for ((field, metric) in listOf("chart" to ExtractionTrendMetric.PRESSURE,
            "cupFlowChart" to ExtractionTrendMetric.CUP_FLOW)) {
            val actual = ExtractionActivity::class.java.getDeclaredField(field)
                .apply { isAccessible = true }.get(activity) as ExtractionTrendView
            check(actual.width > 0 && actual.height > 0)
            val samples = (0..3).map { index ->
                ShotPoint(index * 800L, 90, 0, 0, 9300, index * 100, brewing = true)
            }
            val cases = listOf(emptyList<ShotPoint>() to emptyList(), samples to emptyList(),
                samples to samples.map { it.copy(elapsedMs = it.elapsedMs + 60_000, pressureTenthsBar = 1234) })
            for ((index, data) in cases.withIndex()) {
                val probe = ExtractionTrendView(activity, metric)
                probe.layout(0, 0, actual.width, actual.height)
                probe.points = data.first
                probe.reference = data.second
                val bitmap = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
                try {
                    val nativeTextSize = android.widget.TextView(activity).apply { textSize = 11f }.textSize
                    val canvas = TextBoundsCanvas(bitmap, nativeTextSize,
                        "$fixture $metric case=$index")
                    probe.draw(canvas)
                    check(canvas.labels >= if (index == 0) 1 else 10) { "Chart labels were not drawn" }
                } finally { bitmap.recycle() }
            }
        }
    }

    private class TextBoundsCanvas(bitmap: Bitmap, private val nativeTextSize: Float,
        private val fixture: String) : Canvas(bitmap) {
        var labels = 0
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            check(kotlin.math.abs(paint.textSize - nativeTextSize) < .1f) {
                "$fixture label does not follow system font scale: $text"
            }
            val textWidth = paint.measureText(text)
            val left = x - when (paint.textAlign) {
                Paint.Align.CENTER -> textWidth / 2
                Paint.Align.RIGHT -> textWidth
                else -> 0f
            }
            val metrics = paint.fontMetrics
            check(left >= -1f && left + textWidth <= width + 1f &&
                y + metrics.ascent >= -1f && y + metrics.descent <= height + 1f) {
                "$fixture chart text outside actual viewport: $text at $left,$y size=${width}x$height"
            }
            labels++
            super.drawText(text, x, y, paint)
        }
    }
}
