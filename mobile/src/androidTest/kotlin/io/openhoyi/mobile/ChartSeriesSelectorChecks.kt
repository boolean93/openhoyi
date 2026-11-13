package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas

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
    }
}
