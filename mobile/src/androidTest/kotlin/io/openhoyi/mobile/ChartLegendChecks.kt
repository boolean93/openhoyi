package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

/** Historical product chart: native legends, physical plot text and actual series drawing. */
internal class ChartLegendChecks(private val test: Instrumentation) {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

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
                        val fixture = "historical ${language.tag}/$widthDp/$dark"
                        val chart = ShotChartView(context).apply {
                            points = listOf(ShotPoint(0, 20, 999, 0, 9200, 0, 999),
                                ShotPoint(1000, 100, 999, 300, 9200, 3000, 999))
                        }
                        fun layout() {
                            chart.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                            chart.layout(0, 0, width, height)
                        }
                        layout()
                        val expected = listOf(
                            Triple("PRESSURE", R.string.task_chart_pressure, "bar"),
                            Triple("WATER", R.string.task_chart_water, "ml"),
                            Triple("WEIGHT", R.string.task_chart_weight, "g"),
                            Triple("TEMPERATURE", R.string.task_chart_temperature, "°C"))
                        val controls = descendants(chart).filterIsInstance<Button>().filter { it.tag.toString().startsWith("chart-metric:") }
                        check(controls.map { it.tag } == expected.map { "chart-metric:${it.first}" }) { "$fixture missing metric" }
                        controls.zip(expected).forEach { (control, metric) ->
                            val label = control.text.toString()
                            val expectedRange = when (metric.first) {
                                "PRESSURE" -> "0.0–12.0 bar"
                                "WATER" -> "0.0–60.0 ml"
                                "WEIGHT" -> "0.0–50.0 g"
                                else -> "80.0–100.0 °C"
                            }
                            // Fixture's final sample: 10 bar, 30 ml, 30 g, 92 °C.
                            val reading = when (metric.first) {
                                "PRESSURE" -> "10.0"
                                "WATER", "WEIGHT" -> "30.0"
                                else -> "92.0"
                            }
                            val name = "${context.getString(metric.second)} $reading ${metric.third}"
                            check(label == "$name\n$expectedRange" || label == name) {
                                "$fixture metric name, physical range or unit changed: $label"
                            }
                            check(control.contentDescription.toString().contains("$name;$expectedRange")) {
                                "$fixture accessible physical range missing"
                            }
                            if (widthDp <= 300) check(label.contains(expectedRange)) { "$fixture focused axis lost visible range" }
                            check(!label.contains("ml/s") && !label.contains("g/s")) { "$fixture flow replaced quantity" }
                            check(control.width >= 48 * density && control.height >= 48 * density)
                            LanguageUiLayoutChecks.textFits(control, fixture)
                            val rect = Rect(control.left, control.top, control.right, control.bottom)
                            controls.filter { it !== control }.forEach { other ->
                                check(!Rect.intersects(rect, Rect(other.left, other.top, other.right, other.bottom))) { "$fixture legends overlap" }
                            }
                        }
                        val plot = descendants(chart).single { it.tag == "chart-plot" }
                        check(plot.visibility == View.VISIBLE && plot.height >= 96 * density) { "$fixture usable plot missing" }
                        val bitmap = Bitmap.createBitmap(plot.width, plot.height, Bitmap.Config.ARGB_8888)
                        try {
                            val textCanvas = TrendChartTextChecks.TextBoundsCanvas(bitmap,
                                TextView(context).apply { textSize = 11f }.textSize, fixture)
                            plot.draw(textCanvas)
                            check(textCanvas.labels >= 6 && "bar" in textCanvas.texts)
                            controls.filter { !it.text.contains('–') }.forEach { control ->
                                val description = control.contentDescription.toString()
                                val unit = expected.single { "chart-metric:${it.first}" == control.tag }.third
                                val endpoints = description.substringAfter(';').substringBefore(' ').split('–')
                                check(unit in textCanvas.texts && endpoints.size == 2 && endpoints.all { it in textCanvas.texts }) {
                                    "$fixture compact legend without complete visible axis: $description"
                                }
                            }
                            if (widthDp >= 540) check(listOf("bar", "ml", "g", "°C").all { it in textCanvas.texts }) {
                                "$fixture independent physical axes missing"
                            }
                            var paths = 0
                            plot.draw(object : Canvas(bitmap) {
                                override fun drawPath(path: Path, paint: Paint) {
                                    if (!path.isEmpty) paths++
                                    super.drawPath(path, paint)
                                }
                            })
                            check(paths == 4) { "$fixture expected four physical series, got $paths" }
                            controls.forEach { control ->
                                check(control.performClick() && control.isSelected)
                                layout()
                                LanguageUiLayoutChecks.textFits(control, "$fixture focused")
                            }
                        } finally { bitmap.recycle() }
                        chart.points = chart.points.map { it.copy(weightHundredthsGram = null) }
                        layout()
                        check(descendants(chart).none { it.tag == "chart-metric:WEIGHT" }) { "$fixture manufactured missing scale" }
                    }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
