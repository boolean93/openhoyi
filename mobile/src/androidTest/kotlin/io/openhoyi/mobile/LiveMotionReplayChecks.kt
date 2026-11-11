package io.openhoyi.mobile

import android.app.Instrumentation
import android.view.View
import android.view.ViewGroup
import android.view.Choreographer
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Attached display-only input comparison. No service commands or business-store writes. */
internal class LiveMotionReplayChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) {
        var error: Throwable?=null
        test.runOnMainSync { try { action() } catch(e:Throwable) { error=e } }
        error?.let { throw it }
    }
    fun run(activity: MainActivity, actual: View, directory: File) {
        val fixture=JSONArray(test.context.assets.open("live-curve-real-replay.json").bufferedReader().use { it.readText() }).getJSONObject(0)
        val rows=fixture.getJSONArray("rows")
        val real=(0 until minOf(rows.length(),40)).map { i ->
            val r=rows.getJSONArray(i)
            ShotPoint(r.getString(0).toLong(),r.getString(1).toInt(),r.getString(2).toInt(),r.getString(3).toInt(),r.getString(4).toInt(),r.optString(5).toIntOrNull(),r.optString(6).toIntOrNull(),r.optString(7).toBooleanStrictOrNull(),r.optString(8).toIntOrNull())
        }
        check(real.size==40)
        val streams=JSONArray()
        for(source in listOf("recorded-real-input","synthetic-mock-input")) {
            lateinit var view: ExtractionOverlayView
            lateinit var callback: Choreographer.FrameCallback
            lateinit var container: android.widget.FrameLayout
            var cleanupContainer: android.widget.FrameLayout?=null
            var cleanupCallback: Choreographer.FrameCallback?=null
            var failure: Throwable?=null
            val frames=JSONArray(); val inputs=JSONArray()
            var recording=true; var latest=0L
            val decor=activity.window.decorView as ViewGroup
            val collected=mutableListOf<ShotPoint>()
            try {
                main {
                    view=ExtractionOverlayView(activity,ExtractionHistoryVisualStyle(activity)).apply { liveCompactChrome=true }
                    val location=IntArray(2); actual.getLocationInWindow(location)
                    container=android.widget.FrameLayout(activity)
                    cleanupContainer=container
                    container.addView(view,android.widget.FrameLayout.LayoutParams(actual.width,actual.height).apply { leftMargin=location[0]; topMargin=location[1] })
                    val label=android.widget.TextView(activity).apply {
                        text=if(source=="recorded-real-input") "显示验证 · 真实记录回放（原始约200ms输入）" else "显示验证 · Mock模拟输入（250ms，非真实机器）"
                        textSize=14f; setTextColor(android.graphics.Color.BLACK); setBackgroundColor(android.graphics.Color.WHITE)
                    }
                    container.addView(label,android.widget.FrameLayout.LayoutParams(actual.width-HoyiUi.dp(activity,128),HoyiUi.dp(activity,40)).apply { leftMargin=location[0]; topMargin=location[1] })
                    decor.addView(container,ViewGroup.LayoutParams(-1,-1))
                    callback=Choreographer.FrameCallback { at ->
                        if(recording) {
                            fun field(name:String)=view.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(view)
                            val shown=JSONObject()
                            for(metric in liveMetrics) shown.put(metric.name,view.findViewWithTag<android.widget.TextView>("product/extraction/metric/$metric/number")?.text?.toString())
                            frames.put(JSONObject().put("frameNs",at).put("revealMs",field("revealThroughMs") as Long? ?: latest)
                                .put("latestReceivedMs",latest).put("axisMs",field("renderTimeEnd")).put("displayedValues",shown))
                            Choreographer.getInstance().postFrameCallback(callback)
                        }
                    }
                    cleanupCallback=callback
                    Choreographer.getInstance().postFrameCallback(callback)
                }
                val runtime=MockDeviceRuntime { "" }
                val base=SystemClock.elapsedRealtime()
                runtime.start(base)
                val count=if(source=="recorded-real-input") real.size else 33
                for(i in 0 until count) {
                    val delay=if(i==0) 0L else if(source=="recorded-real-input") real[i].elapsedMs-real[i-1].elapsedMs else 250L
                    check(delay in 0L..1500L)
                    if(delay>0) SystemClock.sleep(delay)
                    main {
                        val deliveredNs=SystemClock.elapsedRealtimeNanos()
                        var wireSeconds:Int?=null
                        val point=if(source=="recorded-real-input") real[i] else {
                            val now=SystemClock.elapsedRealtime()
                            val snapshot=runtime.sample(now)
                            val frame=snapshot.coffee as io.openhoyi.protocol.ExtractionTelemetry
                            wireSeconds=frame.elapsedSeconds
                            ShotPoint(now-base,frame.pressureTenthsBar,frame.flowTenthsMlPerSecond,frame.totalWaterTenthsMl,
                                frame.brewTemperatureHundredthsC,snapshot.scaleObservation?.hundredthsGram,brewing=frame.valveOpen&&!frame.brewWait,statusBits=frame.statusBits)
                        }
                        inputs.put(JSONObject().put("sourceElapsedMs",point.elapsedMs).put("deliveredAtNs",deliveredNs)
                            .put("scheduledDelayMs",delay).put("wireElapsedSeconds",wireSeconds ?: JSONObject.NULL)
                            .put("pressureTenthsBar",point.pressureTenthsBar).put("waterTenthsMl",point.waterTenthsMl)
                            .put("weightHundredthsGram",point.weightHundredthsGram ?: JSONObject.NULL).put("temperatureHundredthsC",point.temperatureHundredthsC))
                        collected+=point; latest=point.elapsedMs; view.points=collected.toList()
                    }
                }
                check(inputs.length()==count)
                SystemClock.sleep(550)
                main { recording=false; cleanupCallback?.let { Choreographer.getInstance().removeFrameCallback(it) } }
                check(frames.length()>0)
                for(i in 0 until frames.length()) check(frames.getJSONObject(i).getLong("revealMs")<=frames.getJSONObject(i).getLong("latestReceivedMs"))
                for(i in 1 until frames.length()) check(frames.getJSONObject(i).getLong("revealMs")>=frames.getJSONObject(i-1).getLong("revealMs"))
                if(source=="synthetic-mock-input") check(collected.zipWithNext().all { (a,b) -> b.pressureTenthsBar>=a.pressureTenthsBar && (b.elapsedMs>=9000 || b.pressureTenthsBar>a.pressureTenthsBar) && b.waterTenthsMl>a.waterTenthsMl && b.weightHundredthsGram!!>a.weightHundredthsGram!! })
            } catch(error: Throwable) { failure=error; throw error } finally {
                var cleanupFailure: Throwable?=null
                fun cleanup(action: () -> Unit) {
                    try { action() } catch(error: Throwable) {
                        val primary=failure ?: cleanupFailure
                        if(primary==null) cleanupFailure=error else if(primary!==error) primary.addSuppressed(error)
                    }
                }
                cleanup { main { recording=false; cleanupCallback?.let { Choreographer.getInstance().removeFrameCallback(it) } } }
                cleanup { main { cleanupContainer?.let { if(it.parent===decor) decor.removeView(it) } } }
                cleanup {
                    streams.put(JSONObject().put("source",source).put("complete",failure==null&&cleanupFailure==null).put("expectedInputCount",if(source=="recorded-real-input")40 else 33).put("inputs",inputs).put("frames",frames))
                    File(directory,"attached-input-cadence-comparison.json").writeText(JSONObject().put("displayOnly",true).put("streams",streams).put("recordedSampleId",fixture.getString("id")).toString())
                }
                if(failure==null) cleanupFailure?.let { throw it }
            }
        }
    }
}
