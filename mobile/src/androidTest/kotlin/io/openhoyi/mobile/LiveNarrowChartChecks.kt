package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Isolated Android live-view probe; does not change device display settings. */
internal class LiveNarrowChartChecks(private val test: Instrumentation) {
    fun run(base: Context, directory: File) {
        var failure: Throwable? = null
        test.runOnMainSync {
            try {
                verifyStackedHeadingGeometry(base, directory)
                val context = android.view.ContextThemeWrapper(base.createConfigurationContext(Configuration(base.resources.configuration).apply { fontScale = 1.3f }), R.style.MobileTheme)
                val density = context.resources.displayMetrics.density
                // 360dp total chart height was an exploratory unsupported case; preserved failure evidence documents its 53dp plot view.
                for (heightDp in listOf(520)) for (missingScale in listOf(false,true)) {
                    val width = (328*density).toInt() // 360dp window minus 16dp content padding per side.
                    val height = (heightDp*density).toInt()
                    val chart = ExtractionOverlayView(context, ExtractionHistoryVisualStyle(context)).apply {
                        liveCompactChrome = true
                        points = (0..20).map { i -> ShotPoint(i*1600L,i*5,0,i*30,9300,
                            if(missingScale) null else i*150,brewing=true) }
                    }
                    for (probeWidth in listOf(width,(940*density).toInt(),width)) {
                        chart.measure(View.MeasureSpec.makeMeasureSpec(probeWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
                        chart.layout(0,0,probeWidth,height)
                    }
                    val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                    try {
                        chart.draw(Canvas(bitmap))
                        val plotView = requireNotNull(chart.findViewWithTag<View>("chart-plot"))
                        val probeId="live-isolated-360dp-font1.3-height$heightDp-missing$missingScale"
                        File(directory,"$probeId.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                        File(directory,"$probeId-debug.json").writeText(JSONObject().put("plotWidth",plotView.width).put("plotHeight",plotView.height).put("plotVisibility",plotView.visibility).put("density",density).put("geometry",chart.visualEvidence).toString())
                        val ops = (0 until chart.visualEvidence.length()).map { chart.visualEvidence.getJSONObject(it) }
                        val plot = ops.single { it.optString("operation")=="plot" }.getJSONArray("boundsPx")
                        check(plot.getDouble(2)-plot.getDouble(0)>=96*density)
                        check(plot.getDouble(3)-plot.getDouble(1)>=96*density)
                        val glyphs = ops.filter { it.optString("role") in setOf("axis-unit","axis-tick") }
                        val projection = FourMetricChartProjection.build(chart.points, emptyList(), liveFlow = true)
                        for(metric in liveMetrics) {
                            check(glyphs.count { it.optString("role")=="axis-unit" && it.optString("metric")==metric.name }==1)
                            val ticks=glyphs.filter { it.optString("role")=="axis-tick" && it.optString("metric")==metric.name }.map { it.getString("text").toFloat() }
                            val range = requireNotNull(projection.series.firstOrNull { it.metric == metric }).range
                            check(ticks.size>=2 && ticks.minOrNull()==range.low && ticks.maxOrNull()==range.high)
                            val name = requireNotNull(chart.findViewWithTag<android.widget.TextView>("product/extraction/metric/$metric/name"))
                            check(name.visibility==View.VISIBLE && name.width>0)
                            LanguageUiLayoutChecks.textFits(name,"narrow $metric")
                            for (role in listOf("name","number","unit")) {
                                val label=requireNotNull(chart.findViewWithTag<android.widget.TextView>("product/extraction/metric/$metric/$role"))
                                LanguageUiLayoutChecks.textFits(label,"narrow $metric/$role")
                                check(label.layout.lineCount==1 && label.layout.getEllipsisCount(0)==0)
                                var child: View=label
                                while(child !== chart) {
                                    check(child.visibility==View.VISIBLE)
                                    val parent=child.parent as android.view.ViewGroup
                                    check(child.left>=0 && child.top>=0 && child.right<=parent.width && child.bottom<=parent.height) { "narrow $metric/$role clipped by ${parent.javaClass.simpleName}" }
                                    child=parent
                                }
                            }
                        }
                        glyphs.forEachIndexed { index, label ->
                            val a=label.getJSONArray("glyphBoundsPx")
                            check(a.getDouble(0)>=0 && a.getDouble(1)>=0 && a.getDouble(2)<=plotView.width && a.getDouble(3)<=plotView.height)
                            glyphs.drop(index+1).forEach { other ->
                                val b=other.getJSONArray("glyphBoundsPx")
                                check(minOf(a.getDouble(2),b.getDouble(2))<=maxOf(a.getDouble(0),b.getDouble(0)) || minOf(a.getDouble(3),b.getDouble(3))<=maxOf(a.getDouble(1),b.getDouble(1)))
                            }
                        }
                        val timeTicks = ops.filter { it.optString("role") == "time-tick" }
                            .sortedBy { it.getJSONArray("glyphBoundsPx").getDouble(0) }
                        check(timeTicks.size >= 2)
                        timeTicks.zipWithNext().forEach { (left, right) ->
                            check(right.getJSONArray("glyphBoundsPx").getDouble(0) -
                                left.getJSONArray("glyphBoundsPx").getDouble(2) >= 8 * density) {
                                "Time labels overlap or lack the required 8dp gap"
                            }
                        }
                        if(missingScale) {
                            check(ops.none { it.optString("operation")=="path" && it.optString("metric")==ChartMetric.FLOW.name })
                            check(chart.findViewWithTag<android.widget.TextView>("product/extraction/metric/FLOW/number").text.toString()=="—")
                        }
                        val id="live-isolated-360dp-font1.3-height$heightDp-missing$missingScale"
                        File(directory,"$id.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
                        File(directory,"$id.json").writeText(JSONObject().put("isolatedProbe",true).put("wholeScreen",false).put("windowWidthDp",360).put("contentWidthDp",328).put("fontScale",1.3).put("geometry",chart.visualEvidence).toString())
                    } finally { bitmap.recycle() }
                }
            } catch(error: Throwable) { failure=error }
        }
        failure?.let { throw it }
    }

    private fun verifyStackedHeadingGeometry(base: Context, directory: File) {
        val names = mapOf(
            ChartMetric.TEMPERATURE to R.string.task_chart_temperature,
            ChartMetric.PRESSURE to R.string.task_chart_pressure,
            ChartMetric.FLOW to R.string.task_chart_flow,
            ChartMetric.YIELD to R.string.chart_series_yield,
            ChartMetric.WATER to R.string.task_chart_water
        )
        val samples = (0..40).map { i ->
            ShotPoint(i * 1_025L, minOf(i * 18, 93), 0, i * 28, 9_300 + i, i * 101,
                brewing = true, beverageHundredthsGram = i * 101)
        }
        val series = FourMetricChartProjection.build(samples, emptyList(), liveFlow = true, includeLiveWeight = true)
        data class Case(val id: String, val locale: Locale, val font: AppFontSize, val widthDp: Int,
            val heightDp: Int, val count: Int)
        val cases = listOf(
            Case("square-four", Locale.SIMPLIFIED_CHINESE, AppFontSize.MEDIUM, 640, 250, 4),
            Case("square-five", Locale.SIMPLIFIED_CHINESE, AppFontSize.MEDIUM, 640, 250, 5),
            Case("short-small", Locale.SIMPLIFIED_CHINESE, AppFontSize.SMALL, 328, 200, 5),
            Case("short-large", Locale.ENGLISH, AppFontSize.LARGE, 300, 230, 5)
        )
        fun JSONObject.glyph() = getJSONArray("glyphBoundsPx")
        fun JSONArray.coord(i: Int) = getDouble(i)
        for (case in cases) {
            val context = android.view.ContextThemeWrapper(base.createConfigurationContext(
                Configuration(base.resources.configuration).apply {
                    setLocale(case.locale); fontScale = case.font.scale
                }), R.style.MobileTheme)
            val density = context.resources.displayMetrics.density
            val width = (case.widthDp * density).toInt()
            val height = (case.heightDp * density).toInt()
            val visual = ExtractionHistoryVisualStyle(context)
            val renderer = ProductHistoryChartRenderer(context, visual, liveMode = true)
            val data = series.copy(series = series.series.filter {
                it.metric in names && (case.count == 5 || it.metric != ChartMetric.YIELD)
            })
            check(data.series.size == case.count)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(visual.palette.surface)
                val plot = renderer.draw(Canvas(bitmap), width, height, data, ChartMetric.PRESSURE, null, comparison = false)
                check(plot.width() >= 96 * density && plot.height() >= 96 * density) { "${case.id}: $plot" }
                val ops = (0 until renderer.evidence.length()).map(renderer.evidence::getJSONObject)
                val texts = ops.filter { it.optString("operation") == "text" }
                fun get(role: String, metric: ChartMetric) = texts.filter {
                    it.optString("role") == role && it.optString("metric") == metric.name
                }
                fun unit(metric: ChartMetric) = if (metric == ChartMetric.WATER) "mL" else metric.unit
                for (item in data.series) {
                    val name = get("axis-name", item.metric).single()
                    val u = get("axis-unit", item.metric).single()
                    check(name.getString("text") == context.getString(names.getValue(item.metric)))
                    check(u.getString("text") == unit(item.metric))
                    check(name.getJSONArray("positionPx").coord(0) == u.getJSONArray("positionPx").coord(0) &&
                        name.glyph().coord(3) <= u.glyph().coord(1) && u.glyph().coord(3) < plot.top)
                    val ticks = get("axis-tick", item.metric)
                    val numbers = ticks.map { it.getString("text").toFloat() }
                    check(numbers.first() == item.range.low && numbers.last() == item.range.high)
                    check(ticks.all {
                        val ink = it.glyph()
                        if (item.metric == ChartMetric.TEMPERATURE || item.metric == ChartMetric.PRESSURE) ink.coord(2) < plot.left
                        else ink.coord(0) > plot.right
                    })
                }
                val time = texts.filter { it.optString("role") == "time-tick" }
                check(time.any { it.getString("text") == "0" } &&
                    time.any { it.getString("text") == String.format(Locale.ROOT, "%.1f s", data.timeEndMs / 1000.0) })
                val labels = texts.filter { it.optString("role") in setOf("axis-name", "axis-unit", "axis-tick", "time-tick") }
                labels.forEachIndexed { index, text ->
                    val a = text.glyph()
                    check(a.coord(0) >= 0 && a.coord(1) >= 0 && a.coord(2) <= width && a.coord(3) <= height) {
                        "${case.id}: clipped ${text.getString("text")}: $a"
                    }
                    check(labels.drop(index + 1).none {
                        val b = it.glyph()
                        minOf(a.coord(2), b.coord(2)) - maxOf(a.coord(0), b.coord(0)) > 1 &&
                            minOf(a.coord(3), b.coord(3)) - maxOf(a.coord(1), b.coord(1)) > 1
                    }) { "${case.id}: overlaps ${text.getString("text")}: $a" }
                }
                // Same-frame legacy renderer gutter formula on the same Android Paint
                // and projection. This is a numeric baseline, not a before screenshot.
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = TaskTypography.face(context, 400); textSize = visual.palette.sp(14)
                }
                fun oldExtent(text: String): Float {
                    val ink = Rect()
                    paint.getTextBounds(text, 0, text.length, ink)
                    return maxOf(paint.measureText(text), ink.right.toFloat()) - minOf(0f, ink.left.toFloat())
                }
                fun oldTick(value: Float) = if (value == value.toLong().toFloat()) value.toLong().toString()
                    else String.format(Locale.ROOT, "%.1f", value)
                fun oldColumn(item: MetricChartSeries, compact: Boolean): Float =
                    (ChartAxisScale.majorTicks(item.range, item.step).map(::oldTick) +
                        if (compact) unit(item.metric) else context.getString(names.getValue(item.metric)) + " · " + unit(item.metric))
                        .maxOf(::oldExtent) + 12 * density
                val oldCompact = case.widthDp <= 600 || width -
                    data.series.sumOf { oldColumn(it, false).toDouble() } - 24 * density < 96 * density
                val oldAll = case.count <= 4 || width -
                    data.series.sumOf { oldColumn(it, true).toDouble() } - 16 * density >= 120 * density
                val oldPlotWidth = width - 16 * density -
                    data.series.filter { oldAll || it.metric == ChartMetric.PRESSURE }.sumOf { oldColumn(it, oldCompact).toDouble() }
                if (case.id == "square-four") check(plot.width() - oldPlotWidth >= 80 * density) {
                    "Expected meaningful gain in the same four-axis plot: $oldPlotWidth → ${plot.width()}"
                }
                File(directory, "axis-stack-${case.id}.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                File(directory, "axis-stack-${case.id}.json").writeText(JSONObject()
                    .put("rendering", "native Canvas component in hosted Mock, not hardware or whole window")
                    .put("widthPx", width).put("heightPx", height).put("density", density)
                    .put("appFont", case.font.key).put("axisCount", data.series.size)
                    .put("beforeWidthPxFromPriorFormula", oldPlotWidth).put("beforeAllAxes", oldAll)
                    .put("afterWidthPxFromCanvas", plot.width()).put("geometry", renderer.evidence).toString())
            } finally { bitmap.recycle() }
        }
    }

}
