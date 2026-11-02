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
                        val endpoints = mapOf(
                            "PRESSURE" to ("0.0" to "12.0"),
                            "WATER" to ("0.0" to "60.0"),
                            "WEIGHT" to ("0.0" to "50.0"),
                            "TEMPERATURE" to ("80.0" to "100.0"))
                        val controls = descendants(chart).filterIsInstance<Button>().filter { it.tag.toString().startsWith("chart-metric:") }
                        check(controls.map { it.tag } == expected.map { "chart-metric:${it.first}" }) { "$fixture missing metric" }
                        controls.zip(expected).forEach { (control, metric) ->
                            val label = control.text.toString()
                            val range = endpoints.getValue(metric.first)
                            val expectedRange = "${range.first}–${range.second} ${metric.third}"
                            // Fixture's final sample: 10 bar, 30 ml, 30 g, 92 °C.
                            val reading = when (metric.first) {
                                "PRESSURE" -> "10.0"
                                "WATER", "WEIGHT" -> "30.0"
                                else -> "92.0"
                            }
                            val name = "${context.getString(metric.second)} $reading ${metric.third}"
                            check(label == "$name\n$expectedRange") {
                                "$fixture metric name, physical range or unit changed: $label"
                            }
                            check(control.contentDescription.toString().contains("$name;$expectedRange")) {
                                "$fixture accessible physical range missing"
                            }
                            check(!label.contains("ml/s") && !label.contains("g/s")) { "$fixture flow replaced quantity" }
                            check(control.width >= 48 * density && control.height >= 48 * density)
                            LanguageUiLayoutChecks.textFits(control, fixture)
                            val rect = Rect(control.left, control.top, control.right, control.bottom)
                            controls.filter { it !== control }.forEach { other ->
                                check(!Rect.intersects(rect, Rect(other.left, other.top, other.right, other.bottom))) { "$fixture legends overlap" }
                            }
                        }
                        val plot = descendants(chart).single { it.tag == "chart-plot" }
                        check(plot.visibility == View.VISIBLE && plot.height >= 96 * density) {
                            val diagnostic = runCatching {
                                fun member(owner: Any?, name: String): Any? = runCatching {
                                    owner?.javaClass?.getDeclaredField(name)?.apply { isAccessible = true }?.get(owner)
                                }.getOrElse { "unavailable:${it.javaClass.simpleName}" }
                                fun size(view: View?): String = if (view == null) "unavailable" else
                                    "measured=${view.measuredWidth}x${view.measuredHeight},layout=${view.width}x${view.height}," +
                                        "visibility=${view.visibility},scroll=${view.scrollX},${view.scrollY}"
                                val overlay = member(chart, "chart") as? View
                                val header = member(overlay, "header") as? View
                                val headerContent = member(overlay, "headerContent") as? View
                                val legend = member(overlay, "legend") as? View
                                val explanation = member(overlay, "explanation") as? View
                                val minimum = runCatching {
                                    plot.javaClass.getDeclaredMethod("minimumAxisHeight").apply { isAccessible = true }.invoke(plot)
                                }.getOrElse { "unavailable:${it.javaClass.simpleName}" }
                                val fontLine = runCatching {
                                    val labels = member(plot, "labels") as android.graphics.Paint
                                    val metrics = labels.fontMetrics
                                    "${metrics.descent - metrics.ascent}px(ascent=${metrics.ascent},descent=${metrics.descent})"
                                }.getOrElse { "unavailable:${it.javaClass.simpleName}" }
                                "density=$density,scaledDensity=${context.resources.displayMetrics.scaledDensity}," +
                                    "fontScale=${context.resources.configuration.fontScale}," +
                                    "chart=[${size(chart)}],overlay=[${size(overlay)}],plot=[${size(plot)}]," +
                                    "header=[${size(header)}],headerContent=[${size(headerContent)}]," +
                                    "legend=[${size(legend)}],explanation=[${size(explanation)}]," +
                                    "minimumAxisHeight=$minimum,fontMetricsLine=$fontLine," +
                                    "compact=${member(overlay, "compactMode")},notice=[${size(member(overlay, "compactNotice") as? View)}]"
                            }.getOrElse { "diagnostic_unavailable:${it.javaClass.simpleName}" }
                            "$fixture usable plot missing; $diagnostic"
                        }
                        val bitmap = Bitmap.createBitmap(plot.width, plot.height, Bitmap.Config.ARGB_8888)
                        try {
                            val textCanvas = TrendChartTextChecks.TextBoundsCanvas(bitmap,
                                TextView(context).apply { textSize = 11f }.textSize, fixture)
                            plot.draw(textCanvas)
                            check(textCanvas.labels >= 6 && "bar" in textCanvas.texts)
                            var paths = 0
                            plot.draw(object : Canvas(bitmap) {
                                override fun drawPath(path: Path, paint: Paint) {
                                    if (!path.isEmpty) paths++
                                    super.drawPath(path, paint)
                                }
                            })
                            check(paths == 4) { "$fixture expected four physical series, got $paths" }
                            controls.zip(expected).forEach { (control, metric) ->
                                check(control.performClick() && control.isSelected && controls.count { it.isSelected } == 1)
                                layout()
                                LanguageUiLayoutChecks.textFits(control, "$fixture focused")
                                val focusedBitmap = Bitmap.createBitmap(plot.width, plot.height, Bitmap.Config.ARGB_8888)
                                try {
                                    val focusedCanvas = TrendChartTextChecks.TextBoundsCanvas(focusedBitmap,
                                        TextView(context).apply { textSize = 11f }.textSize, "$fixture focused ${metric.first}")
                                    plot.draw(focusedCanvas)
                                    val axisUnits = focusedCanvas.texts.filter { it in listOf("bar", "ml", "g", "°C") }
                                    val range = endpoints.getValue(metric.first)
                                    check(axisUnits == listOf(metric.third) &&
                                        range.first in focusedCanvas.texts && range.second in focusedCanvas.texts) {
                                        "$fixture selected ${metric.first} axis lost its unit or physical endpoints: ${focusedCanvas.texts}"
                                    }
                                    var focusedPaths = 0
                                    plot.draw(object : Canvas(focusedBitmap) {
                                        override fun drawPath(path: Path, paint: Paint) {
                                            if (!path.isEmpty) focusedPaths++
                                            super.drawPath(path, paint)
                                        }
                                    })
                                    check(focusedPaths == 4) { "$fixture selecting ${metric.first} hid a physical series: $focusedPaths" }
                                } finally { focusedBitmap.recycle() }
                            }
                        } finally { bitmap.recycle() }
                        chart.points = chart.points.map { it.copy(weightHundredthsGram = null) }
                        layout()
                        val missingControls = descendants(chart).filterIsInstance<Button>()
                            .filter { it.tag?.toString()?.startsWith("chart-metric:") == true }
                        check(missingControls.map { it.tag } == expected.map { "chart-metric:${it.first}" }) {
                            "$fixture missing-scale chart lost its four-metric legend"
                        }
                        val weight = missingControls.single { it.tag == "chart-metric:WEIGHT" }
                        val missing = context.getString(R.string.task_chart_missing_scale)
                        check(!weight.isEnabled && !weight.isSelected &&
                            weight.text.contains("g") && weight.text.contains(missing) &&
                            weight.contentDescription.contains("g") && weight.contentDescription.contains(missing)) {
                            "$fixture missing scale must be explicit and cannot focus a nonexistent axis"
                        }
                        LanguageUiLayoutChecks.textFits(weight, "$fixture missing scale")
                        check(FourMetricChartProjection.build(chart.points, emptyList()).series.none { it.metric == ChartMetric.WEIGHT }) {
                            "$fixture missing scale generated a weight series"
                        }
                        val missingBitmap = Bitmap.createBitmap(plot.width, plot.height, Bitmap.Config.ARGB_8888)
                        try {
                            var paths = 0
                            plot.draw(object : Canvas(missingBitmap) {
                                override fun drawPath(path: Path, paint: Paint) {
                                    if (!path.isEmpty) paths++
                                    super.drawPath(path, paint)
                                }
                            })
                            check(paths == 3) { "$fixture missing scale drew a false weight curve: $paths paths" }
                        } finally { missingBitmap.recycle() }
                    }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
