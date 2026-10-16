package io.openhoyi.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Draw the production Canvas view, checking actual text calls rather than child TextViews. */
internal object TrendChartTextChecks {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    fun run(activity: ExtractionActivity, fixture: String) {
        val actual = ExtractionActivity::class.java.getDeclaredField("chart")
            .apply { isAccessible = true }.get(activity) as ExtractionOverlayView
        check(actual.width > 0 && actual.height > 0)
        val samples = (0..3).map { index ->
            ShotPoint(index * 800L, 90, 0, 0, 9300, index * 100, brewing = true)
        }
        val cases = listOf(emptyList<ShotPoint>() to emptyList(), samples to emptyList(),
            samples to samples.map { it.copy(elapsedMs = it.elapsedMs + 60_000, pressureTenthsBar = 1234) })
        for ((index, data) in cases.withIndex()) {
            val probe = ExtractionOverlayView(activity)
            probe.points = data.first
            probe.reference = data.second
            probe.measure(View.MeasureSpec.makeMeasureSpec(actual.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(actual.height, View.MeasureSpec.EXACTLY))
            probe.layout(0, 0, actual.width, actual.height)
            val children = descendants(probe)
            val controls = children.filter { it.tag?.toString()?.startsWith("chart-metric:") == true }
            val expected = if (data.first.any { it.weightHundredthsGram != null } || data.second.any { it.weightHundredthsGram != null })
                listOf("PRESSURE", "WATER", "WEIGHT", "TEMPERATURE") else listOf("PRESSURE", "WATER", "TEMPERATURE")
            check(controls.map { it.tag } == expected.map { "chart-metric:$it" }) { "$fixture missing four-metric legend" }
            controls.forEach { control ->
                val label = (control as TextView).text.toString()
                check(label.contains("–") && label.contains(when (control.tag) {
                    "chart-metric:PRESSURE" -> "bar"
                    "chart-metric:WATER" -> "ml"
                    "chart-metric:WEIGHT" -> "g"
                    else -> "°C"
                })) { "$fixture metric has no independent unit/range: $label" }
                LanguageUiLayoutChecks.textFits(control, "$fixture metric legend")
            }
            check(controls.none { (it as TextView).text.contains("g/s") || it.text.contains("ml/s") })
            if (data.second.isNotEmpty()) {
                val explanation = children.single { it.tag == "chart-reference-caption" } as TextView
                check(explanation.visibility == View.VISIBLE && explanation.text.isNotBlank()) { "$fixture historical reference is not explained" }
                LanguageUiLayoutChecks.textFits(explanation, "$fixture reference")
            }
            val plot = children.single { it.tag == "chart-plot" }
            check(plot.width > 0 && plot.height >= HoyiUi.dp(activity, 64)) { "$fixture missing usable plot viewport: ${plot.width}x${plot.height}" }
            val bitmap = Bitmap.createBitmap(plot.width, plot.height, Bitmap.Config.ARGB_8888)
            try {
                val nativeTextSize = android.widget.TextView(activity).apply { textSize = 11f }.textSize
                val canvas = TextBoundsCanvas(bitmap, nativeTextSize, "$fixture overlay case=$index")
                plot.draw(canvas)
                check(canvas.labels >= 6) { "Four-metric plot has no usable labeled viewport" }
                check("bar" in canvas.texts && "g/s" !in canvas.texts && "ml/s" !in canvas.texts) { "Physical units missing or replaced with flow" }
            } finally { bitmap.recycle() }
        }
    }

    internal class TextBoundsCanvas(bitmap: Bitmap, private val nativeTextSize: Float,
        private val fixture: String) : Canvas(bitmap) {
        var labels = 0
        val texts = mutableListOf<String>()
        private val bounds = mutableListOf<Pair<String, android.graphics.RectF>>()
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
            val current = android.graphics.RectF(left, y + metrics.ascent, left + textWidth, y + metrics.descent)
            bounds.forEach { (oldText, old) ->
                val horizontal = minOf(old.right, current.right) - maxOf(old.left, current.left)
                val vertical = minOf(old.bottom, current.bottom) - maxOf(old.top, current.top)
                check(horizontal <= 1f || vertical <= 1f) { "$fixture chart labels overlap: $oldText / $text" }
            }
            bounds += text to current
            texts += text
            labels++
            super.drawText(text, x, y, paint)
        }
    }
}
