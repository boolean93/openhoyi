package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.widget.LinearLayout

/** Display-only drawing probes; no service, BLE, preferences, or machine actions. */
internal class ChartSeriesSelectorChecks(private val test: Instrumentation) {
    fun run() {
        check(BuildConfig.MOCK_MODE)
        var failure: Throwable? = null
        test.runOnMainSync {
            try {
                val points = (0..12).map { index ->
                    ShotPoint(index * 500L, 10 + index * 6, 10, index * 15, 9200 + index * 5,
                        index * 180, brewing=true, beverageHundredthsGram=index * 180, beverageSource=BeverageWeightSource.ESTIMATED, beverageSegmentId=0)
                }
                for (fontSize in AppFontSize.entries) for (dark in listOf(false, true)) {
                    val context = AppLanguageContext.wrap(test.targetContext, AppLanguage.CHINESE, dark, fontSize)
                    val style = ExtractionHistoryVisualStyle(context)
                    val paused = ExtractionOverlayView(context, style).apply {
                        this.points = listOf(ShotPoint(0,90,10,0,9300,null,brewing=true,statusBits=64),
                            ShotPoint(500,77,10,5,9300,null,brewing=false,statusBits=0))
                    }
                    check(paused.metricReadout(ChartMetric.PRESSURE) == "— bar") {
                        "Display pause baseline must not be reported as observed zero pressure"
                    }
                    val density = context.resources.displayMetrics.density
                    val full = FourMetricChartProjection.build(points, emptyList(), liveFlow=true, includeLiveWeight=true)
                    // Selected net weight must explain its absence, even while machine samples exist.
                    for (emptyPoints in listOf(emptyList(), points.map { it.copy(
                        weightHundredthsGram = null, beverageHundredthsGram = null,
                        beverageSource = null, beverageSegmentId = null) })) {
                        val empty = FourMetricChartProjection.build(emptyPoints, emptyList(),
                            liveFlow = true, includeLiveWeight = true).let { projection ->
                            projection.copy(series = projection.series.filter { it.metric == ChartMetric.YIELD })
                        }
                        val renderer = ProductHistoryChartRenderer(context, style, liveMode = true)
                        val bitmap = Bitmap.createBitmap((400*density).toInt(), (320*density).toInt(), Bitmap.Config.ARGB_8888)
                        renderer.draw(Canvas(bitmap), bitmap.width, bitmap.height, empty, ChartMetric.YIELD,
                            null, false, simpleLivePresentation = true)
                        val labels = (0 until renderer.evidence.length()).map { renderer.evidence.getJSONObject(it) }
                        check(labels.any { it.optString("role") == "empty-notice" && it.optString("text") ==
                            context.getString(R.string.chart_series_yield_unavailable) }) { "Missing yield empty notice: $fontSize/$dark" }
                        bitmap.recycle()
                    }
                    for (widthDp in listOf(320, 568, 888)) for (metrics in listOf(liveMetrics, selectableLiveMetrics, listOf(ChartMetric.YIELD))) {
                        val renderer = ProductHistoryChartRenderer(context, style, liveMode=true)
                        val width = (widthDp*density).toInt(); val height=(320*density).toInt()
                        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                        val data=full.copy(series=full.series.filter { it.metric in metrics })
                        val box=renderer.draw(Canvas(bitmap),width,height,data,metrics.first(),null,false,simpleLivePresentation=true)
                        check(box.width() >= 96*density) { "$fontSize $widthDp $metrics plot clipped: $box" }
                        val evidence=(0 until renderer.evidence.length()).map { renderer.evidence.getJSONObject(it) }
                        metrics.forEach { metric -> check(evidence.any { it.optString("metric")==metric.name }) {
                            "$widthDp $metrics missing $metric"
                        } }
                        bitmap.recycle()
                    }
                }
            } catch (error: Throwable) { failure=error }
        }
        failure?.let { throw it }
        panelEntryPoints()
    }

    /** Invoke the actual button listener and both real window types, not just the renderer. */
    private fun panelEntryPoints() {
        val home = MainPageTestHost.launch(test, timeoutMs = 10000).activity
        try {
            for (size in AppFontSize.entries) for (orientation in listOf(
                Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE)) {
                var failure: Throwable? = null
                test.runOnMainSync {
                    try {
                        val configured = ContextThemeWrapper(home, home.theme).apply {
                            applyOverrideConfiguration(AppLanguageContext.configuration(home, AppLanguage.CHINESE,
                                fontSize = size).apply { this.orientation = orientation })
                        }
                        val visual = ExtractionHistoryVisualStyle(configured)
                        val chart = ExtractionOverlayView(configured, visual)
                        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                            val density = configured.resources.displayMetrics.density
                            chart.liveCompactChrome = true
                            chart.liveHeaderHeightPx = (80 * density).toInt()
                            val workspace = TaskExtractionWorkspace(configured, visual)
                            workspace.primaryBody.addView(chart, LinearLayout.LayoutParams(-1, 0, 1f))
                            // Short-portrait production keeps duplicate readouts in the series panel.
                            workspace.setFooter(android.view.View(configured).apply { minimumHeight = (88*density).toInt() })
                            workspace.onPrimarySpaceAvailable = { width, height -> chart.liveHeightForPlotAspect(width, 1.4f)
                                .coerceAtLeast(chart.minimumLiveHeight()).coerceAtMost(height.coerceAtLeast(chart.minimumLiveHeight())) }
                            workspace.measure(android.view.View.MeasureSpec.makeMeasureSpec((400*density).toInt(), android.view.View.MeasureSpec.EXACTLY),
                                android.view.View.MeasureSpec.makeMeasureSpec((480*density).toInt(), android.view.View.MeasureSpec.EXACTLY))
                            workspace.layout(0, 0, workspace.measuredWidth, workspace.measuredHeight)
                            val scroll = workspace.getChildAt(0)
                            check(workspace.primaryBody.height <= scroll.height + 1) { "Chart/legend clipped below fixed dock: $size" }
                            val rendered = Bitmap.createBitmap(workspace.width, workspace.height, Bitmap.Config.ARGB_8888)
                            workspace.draw(Canvas(rendered))
                            val plot = (0 until chart.visualEvidence.length()).map { chart.visualEvidence.getJSONObject(it) }
                                .first { it.optString("operation") == "plot" }.getJSONArray("boundsPx")
                            check(plot.getDouble(3) - plot.getDouble(1) >= 160*density - 2) { "Portrait plot reduced to thumbnail: $size/$plot" }
                            check(plot.getDouble(2) - plot.getDouble(0) >= 200*density - 2) { "Portrait plot too narrow: $size/$plot" }
                            rendered.recycle()
                            workspace.primaryBody.removeView(chart)
                        }
                        val selector = ChartSeriesSelector(chart)
                        val container = LinearLayout(configured).apply {
                            this.orientation = LinearLayout.VERTICAL
                            addView(selector.button)
                            addView(chart, LinearLayout.LayoutParams(-1, 200))
                        }
                        home.setContentView(container)
                        container.measure(android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY))
                        container.layout(0, 0, 800, 1000)
                        try {
                            repeat(2) {
                                check(selector.button.performClick())
                                check(selector.isOpen) { "Panel not shown: $size/$orientation" }
                                selector.refreshReadings()
                                selector.close()
                                check(!selector.isOpen)
                            }
                        } finally { selector.close() }
                    } catch (error: Throwable) { failure = error }
                }
                failure?.let { throw it }
                test.waitForIdleSync()
            }
        } finally { test.runOnMainSync { home.finish() } }
    }
}
