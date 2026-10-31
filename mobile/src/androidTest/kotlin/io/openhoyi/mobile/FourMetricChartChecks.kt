package io.openhoyi.mobile

import android.app.Instrumentation
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
        val host = MainPageTestHost.launch(test, extraction = true)
        val activity = host.activity
        try {
            test.waitForIdleSync()
            val directory = File(test.targetContext.getExternalFilesDir(null), "four-metric-chart-checks").apply { mkdirs() }
            val samples = (0..30).map { index ->
                ShotPoint(index * 800L, if (index < 5) index * 18 else 90 - (index - 5),
                    999, index * 18, 9300 + index * 2, index * 120, 999, true)
            }
            val pauseSamples = samples.mapIndexed { index,point ->
                if (index in 8..14) point.copy(brewing=false,statusBits=0,pressureTenthsBar=73)
                else point.copy(brewing=true,statusBits=64)
            }
            val cases = listOf(
                Triple("preinfusion-pause",pauseSamples,emptyList()),
                Triple("machine-only", samples.map { it.copy(weightHundredthsGram = null) }, emptyList()),
                Triple("four-metrics", samples, emptyList()),
                Triple("gaps-and-reference", samples.filterIndexed { index, _ -> index !in 12..16 }.mapIndexed { index, point ->
                    point.copy(weightHundredthsGram = point.weightHundredthsGram.takeUnless { index in 5..7 || point.elapsedMs == samples.last().elapsedMs },
                        waterTenthsMl = if (index > 15) (index - 16) * 15 else point.waterTenthsMl)
                }, samples.map { it.copy(elapsedMs = it.elapsedMs * 2) })
            )
            onMain {
                val actual = host.viewField<View>(host.active("extraction"), "chart")
                check(actual.width > 0 && actual.height > 0)
                for ((name, points, reference) in cases) {
                    val probe = ExtractionOverlayView(activity, ExtractionHistoryVisualStyle(activity)).apply { liveCompactChrome = true }
                    probe.points = points
                    probe.reference = reference
                    probe.measure(View.MeasureSpec.makeMeasureSpec(actual.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(actual.height, View.MeasureSpec.EXACTLY))
                    probe.layout(0, 0, actual.width, actual.height)
                    val expected = listOf("PRESSURE", "WATER", "WEIGHT", "TEMPERATURE")
                    val controls = descendants(probe).filter { it.tag?.toString()?.startsWith("chart-metric:") == true }
                    check(controls.map { it.tag } == expected.map { "chart-metric:$it" }) {
                        "$name: expected usable four-metric legend, got ${controls.map { it.tag }}"
                    }
                    for (control in controls) {
                        check(control.width >= HoyiUi.dp(activity, 48) && control.height >= HoyiUi.dp(activity, 48))
                        check(!control.contentDescription.isNullOrBlank())
                        val metric = ChartMetric.valueOf(control.tag.toString().substringAfter(':'))
                        val value = ChartInspection.values(points.last())[metric]
                        val reading = value?.let { java.util.Locale.ROOT.let { locale ->
                            String.format(locale, "%.1f", it)
                        } } ?: "—"
                        val number = requireNotNull(probe.findViewWithTag<android.widget.TextView>("product/extraction/metric/$metric/number"))
                        val unit = requireNotNull(probe.findViewWithTag<android.widget.TextView>("product/extraction/metric/$metric/unit"))
                        check(number.text.toString() == reading && unit.text.toString() ==
                            if (metric == ChartMetric.WATER) "mL" else metric.unit) {
                            "$name: atomic legend must show latest observation and physical unit"
                        }
                        LanguageUiLayoutChecks.textFits(number, "$name current metric")
                        LanguageUiLayoutChecks.textFits(unit, "$name current unit")
                        if (value == null && metric == ChartMetric.WEIGHT) {
                            check(!control.isEnabled && control.contentDescription.contains(activity.getString(R.string.task_chart_missing_scale))) {
                                "$name: missing scale reading must remain explicit and cannot focus a nonexistent axis"
                            }
                        } else check(control.performClick() && control.isSelected) { "$name: legend cannot focus ${control.tag}" }
                        // Native font weight/selection can request layout; emulate the next real UI frame.
                        probe.measure(View.MeasureSpec.makeMeasureSpec(actual.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(actual.height, View.MeasureSpec.EXACTLY))
                        probe.layout(0, 0, actual.width, actual.height)
                        LanguageUiLayoutChecks.textFits(control as android.widget.TextView, "$name selected legend")
                    }
                    val bitmap = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
                    try {
                        probe.draw(android.graphics.Canvas(bitmap))
                        val evidence = probe.visualEvidence
                        val texts = (0 until evidence.length()).map { evidence.getJSONObject(it) }
                            .filter { it.optString("operation") == "text" }
                        val unit = texts.single { it.optString("role") == "axis-unit" }
                        val ticks = texts.filter { it.optString("role") == "axis-tick" }
                        fun overlaps(a: org.json.JSONObject, b: org.json.JSONObject): Boolean {
                            val x = a.getJSONArray("glyphBoundsPx"); val y = b.getJSONArray("glyphBoundsPx")
                            return minOf(x.getDouble(2), y.getDouble(2)) - maxOf(x.getDouble(0), y.getDouble(0)) > 1 &&
                                minOf(x.getDouble(3), y.getDouble(3)) - maxOf(x.getDouble(1), y.getDouble(1)) > 1
                        }
                        check(ticks.none { overlaps(unit, it) }) { "$name: focused axis unit overlaps a tick" }
                        check(texts.none { it.optString("role") == "time-unit" } &&
                            texts.any { it.optString("role") == "time-tick" && it.optString("text").endsWith(" s") }) {
                            "$name: time unit must share the last tick row"
                        }
                        if (name == "preinfusion-pause") {
                            val phase = FourMetricChartProjection.build(points,reference,livePauseBaseline=true)
                            check(phase.pauseBaselineMs == (8..14).map { it*800L }.toSet())
                            check(points.slice(8..14).all { it.pressureTenthsBar==73 })
                            val ops = (0 until evidence.length()).map { evidence.getJSONObject(it) }
                            check(ops.any { it.optString("operation")=="phase-zero-baseline" })
                            check(ops.any { it.optString("operation")=="gradient-area" && it.optString("metric")=="PRESSURE" })
                            check(ops.filter { it.optString("role")=="endpoint-name" }.size == 4)
                            val labels = ops.filter { it.optString("role")=="endpoint-name" }.map { it.getJSONArray("glyphBoundsPx") }
                            labels.forEachIndexed { i,a -> labels.drop(i+1).forEach { b ->
                                check(minOf(a.getDouble(2),b.getDouble(2)) <= maxOf(a.getDouble(0),b.getDouble(0)) ||
                                    minOf(a.getDouble(3),b.getDouble(3)) <= maxOf(a.getDouble(1),b.getDouble(1))) {
                                    "Tail labels overlap" }
                            } }
                            val wait = points.map { if (it.brewing==false) it.copy(statusBits=32) else it }
                            check(FourMetricChartProjection.pauseBaselines(wait).isEmpty())
                            val gap = listOf(points.first(),points[8].copy(elapsedMs=2000))
                            check(FourMetricChartProjection.pauseBaselines(gap).isEmpty())
                        }
                        if (name == "machine-only") {
                            check(FourMetricChartProjection.build(points, reference).series.none { it.metric == ChartMetric.WEIGHT })
                            check((0 until evidence.length()).map { evidence.getJSONObject(it) }.none {
                                it.optString("operation") == "path" && it.optString("metric") == ChartMetric.WEIGHT.name
                            }) { "$name: missing weight must have no drawn line" }
                        }
                        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                }
                val shellProbe = TaskShellStatusView(activity, {}, {}, {}, {})
                val railWidth = HoyiUi.dp(activity, 104)
                val railBudget = shellProbe.requiredRailHeight(railWidth)
                shellProbe.renderMode(TaskNavigationMode.BOTTOM)
                shellProbe.measure(View.MeasureSpec.makeMeasureSpec(actual.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                shellProbe.layout(0, 0, actual.width, shellProbe.measuredHeight)
                check(shellProbe.requiredRailHeight(railWidth) == railBudget) {
                    "Fallback measurement changed rail eligibility and can oscillate presentations" }
                shellProbe.requestLayout()
                shellProbe.measure(View.MeasureSpec.makeMeasureSpec(actual.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                shellProbe.layout(0, 0, actual.width, shellProbe.measuredHeight)
                for (tag in listOf("task-shell-coffee-name", "task-shell-coffee-status", "task-shell-scale-name", "task-shell-scale")) {
                    val fact = requireNotNull(shellProbe.findViewWithTag<android.widget.TextView>(tag))
                    check(!fact.isClickable && !fact.isFocusable)
                    LanguageUiLayoutChecks.textFits(fact, "compact shell $tag")
                }
                for (tag in listOf("task-shell-sleep", "task-shell-scale-reconnect")) {
                    val action = requireNotNull(shellProbe.findViewWithTag<android.widget.Button>(tag))
                    check(action.height >= HoyiUi.dp(activity, 48))
                }
                shellProbe.renderMode(TaskNavigationMode.RAIL)
                check(shellProbe.requiredRailHeight(railWidth) == railBudget)
                val lifecycleProbe = ExtractionOverlayView(activity, ExtractionHistoryVisualStyle(activity))
                lifecycleProbe.traceIdentity = "old-cup"
                lifecycleProbe.points = listOf(samples.first(), samples.last().copy(elapsedMs = 90_000))
                fun axisEnd() = (ExtractionOverlayView::class.java.getDeclaredField("data")
                    .apply { isAccessible = true }.get(lifecycleProbe) as FourMetricChartData).timeEndMs
                check(axisEnd() == 90_000L)
                lifecycleProbe.traceIdentity = "new-cup"
                lifecycleProbe.points = samples.take(2)
                check(axisEnd() == 30_000L) { "New cup retained the previous cup time budget" }
                val liveNumber = requireNotNull(lifecycleProbe.findViewWithTag<android.widget.TextView>(
                    "product/extraction/metric/PRESSURE/number"))
                check(liveNumber.text.toString() != "—")
                val observations = lifecycleProbe.points.toList()
                val livePlot = requireNotNull(lifecycleProbe.findViewWithTag<View>("chart-plot"))
                val latestBefore = livePlot.contentDescription.toString()
                lifecycleProbe.unavailableLiveMetrics = ChartMetric.entries.toSet()
                ChartMetric.entries.forEach { metric ->
                    val nameId = when (metric) {
                        ChartMetric.PRESSURE -> R.string.task_chart_pressure
                        ChartMetric.WATER -> R.string.task_chart_water
                        ChartMetric.WEIGHT -> R.string.task_extraction_scale
                        ChartMetric.TEMPERATURE -> R.string.task_chart_temperature
                    }
                    val unit = if (metric == ChartMetric.WATER) "mL" else metric.unit
                    val missingLatest = activity.getString(R.string.task_chart_latest, activity.getString(nameId), "—", unit)
                    check(livePlot.contentDescription.toString().contains(missingLatest)) {
                        "Lost freshness retained a spoken current value for $metric"
                    }
                    val number = requireNotNull(lifecycleProbe.findViewWithTag<android.widget.TextView>(
                        "product/extraction/metric/$metric/number"))
                    check(number.text.toString() == "—")
                }
                check(liveNumber.text.toString() == "—" && lifecycleProbe.points == observations) {
                    "Lost freshness retained a current value or changed recorded points" }
                lifecycleProbe.unavailableLiveMetrics = emptySet()
                check(liveNumber.text.toString() != "—" && lifecycleProbe.points == observations &&
                    livePlot.contentDescription.toString() == latestBefore)
                val configuration = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = 1.3f }
                val compactContext = android.view.ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.MobileTheme)
                val enlargedLive = ExtractionOverlayView(compactContext, ExtractionHistoryVisualStyle(compactContext)).apply {
                    liveCompactChrome = true; points = samples
                }
                fun layoutEnlarged(widthDp: Int) {
                    val width = HoyiUi.dp(activity, widthDp)
                    enlargedLive.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, 500), View.MeasureSpec.EXACTLY))
                    enlargedLive.layout(0, 0, width, enlargedLive.measuredHeight)
                }
                val liveLegend = ExtractionOverlayView::class.java.getDeclaredField("legend")
                    .apply { isAccessible = true }.get(enlargedLive) as ViewGroup
                layoutEnlarged(940)
                check((0 until liveLegend.childCount).map { liveLegend.getChildAt(it).top }.distinct().size == 1) {
                    "Font 1.3 must retain one metric row when all four natural content widths fit" }
                check(enlargedLive.getChildAt(0).height == HoyiUi.dp(activity,96)) {
                    "Wide compact header and metric strip must share the 96dp budget" }
                ChartMetric.entries.filter { it != ChartMetric.PRESSURE }.forEach { metric ->
                    val number = requireNotNull(enlargedLive.findViewWithTag<android.widget.TextView>("product/extraction/metric/$metric/number"))
                    val unit = requireNotNull(enlargedLive.findViewWithTag<android.widget.TextView>("product/extraction/metric/$metric/unit"))
                    val name = requireNotNull(enlargedLive.findViewWithTag<android.widget.TextView>("product/extraction/metric/$metric/name"))
                    val cell = number.parent as ViewGroup
                    val body = cell.parent as ViewGroup
                    check(name.layoutParams.width == ViewGroup.LayoutParams.WRAP_CONTENT && number.width > 0 && unit.width > 0)
                    check(name.right <= cell.left && cell.right <= body.width && number.right <= cell.width && unit.right <= cell.width) {
                        "Compact horizontal name swallowed or clipped $metric current value/unit" }
                }
                descendants(liveLegend).filterIsInstance<android.widget.TextView>().filter { it.text.isNotBlank() && it.visibility != View.GONE }
                    .forEach { LanguageUiLayoutChecks.textFits(it, "font1.3 single metric row") }
                val liveGauge = requireNotNull(enlargedLive.findViewWithTag<LivePressureGauge>("product/extraction/pressure-gauge"))
                val gaugeBitmap = Bitmap.createBitmap(liveGauge.width,liveGauge.height,Bitmap.Config.ARGB_8888)
                try {
                    check(liveGauge.width==HoyiUi.dp(activity,112) && liveGauge.height==HoyiUi.dp(activity,96))
                    liveGauge.draw(android.graphics.Canvas(gaugeBitmap))
                    val glyphs = (0 until liveGauge.drawingEvidence.length()).map { liveGauge.drawingEvidence.getJSONObject(it) }
                    val ticks = glyphs.filter { it.getString("role")=="tick" }
                    check(ticks.size==3 && ticks.map { it.getString("text") }==listOf("0","6","12"))
                    val values = glyphs.filter { it.getString("role") in setOf("number","unit") }
                    fun bounds(item: org.json.JSONObject): android.graphics.RectF {
                        val b=item.getJSONArray("boundsPx")
                        return android.graphics.RectF(b.getDouble(0).toFloat(),b.getDouble(1).toFloat(),b.getDouble(2).toFloat(),b.getDouble(3).toFloat())
                    }
                    glyphs.forEach { item ->
                        val b=bounds(item)
                        check(b.left>=0 && b.top>=0 && b.right<=liveGauge.width && b.bottom<=liveGauge.height) {
                            "Gauge native glyph outside 112x96 at font1.3: $item" }
                    }
                    ticks.forEach { tick -> values.forEach { value ->
                        check(!android.graphics.RectF.intersects(bounds(tick),bounds(value))) {
                            "Gauge tick overlaps actual value glyph at font1.3: $tick / $value" }
                    } }
                    File(directory,"gauge-proportions-font1.3.png").outputStream().use {
                        check(gaugeBitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
                } finally { gaugeBitmap.recycle() }
                layoutEnlarged(200)
                check(liveLegend.getChildAt(2).top > liveLegend.getChildAt(0).top) {
                    "Narrow metrics must still wrap when natural text widths do not fit" }
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
