package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import java.io.File
import org.json.JSONObject

/** Isolated Android live-view probe; does not change device display settings. */
internal class LiveNarrowChartChecks(private val test: Instrumentation) {
    fun run(base: Context, directory: File) {
        var failure: Throwable? = null
        test.runOnMainSync {
            try {
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
                        for(metric in ChartMetric.entries) {
                            check(glyphs.count { it.optString("role")=="axis-unit" && it.optString("metric")==metric.name }==1)
                            val ticks=glyphs.filter { it.optString("role")=="axis-tick" && it.optString("metric")==metric.name }.map { it.getString("text").toFloat() }
                            check(ticks.size>=2 && ticks.minOrNull()==metric.floor && ticks.maxOrNull()==metric.ceiling)
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
                        if(missingScale) {
                            check(ops.none { it.optString("operation")=="path" && it.optString("metric")==ChartMetric.WEIGHT.name })
                            check(chart.findViewWithTag<android.widget.TextView>("product/extraction/metric/WEIGHT/number").text.toString()=="—")
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
}
