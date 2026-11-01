package io.openhoyi.mobile

import android.app.Instrumentation
import android.view.View
import android.view.ViewGroup
import android.view.Choreographer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Attached display-only replay. Removes the overlay without changing service or user storage. */
internal class LiveMotionReplayChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) {
        var error: Throwable?=null
        test.runOnMainSync { try { action() } catch(e:Throwable) { error=e } }
        error?.let { throw it }
    }
    fun run(activity: MainActivity, actual: View, directory: File) {
        val fixture=JSONArray(test.context.assets.open("live-curve-real-replay.json").bufferedReader().use { it.readText() }).getJSONObject(0)
        val rows=fixture.getJSONArray("rows")
        val points=(0 until rows.length()).map { i ->
            val r=rows.getJSONArray(i)
            ShotPoint(r.getString(0).toLong(),r.getString(1).toInt(),r.getString(2).toInt(),r.getString(3).toInt(),r.getString(4).toInt(),r.optString(5).toIntOrNull(),r.optString(6).toIntOrNull(),r.optString(7).toBooleanStrictOrNull(),r.optString(8).toIntOrNull())
        }
        lateinit var view: ExtractionOverlayView
        lateinit var callback: Choreographer.FrameCallback
        val frames=JSONArray()
        var recording=true
        var latest=0L
        val decor=activity.window.decorView as ViewGroup
        try {
            main {
                view=ExtractionOverlayView(activity,ExtractionHistoryVisualStyle(activity)).apply { liveCompactChrome=true }
                val location=IntArray(2); actual.getLocationInWindow(location)
                view.measure(View.MeasureSpec.makeMeasureSpec(actual.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(actual.height,View.MeasureSpec.EXACTLY))
                view.layout(location[0],location[1],location[0]+actual.width,location[1]+actual.height)
                decor.overlay.add(view)
                callback=Choreographer.FrameCallback { at ->
                    if(recording) {
                        val reveal=view.javaClass.getDeclaredField("revealThroughMs").apply { isAccessible=true }.get(view) as Long? ?: latest
                        frames.put(JSONObject().put("frameNs",at).put("revealMs",reveal).put("latestReceivedMs",latest))
                        Choreographer.getInstance().postFrameCallback(callback)
                    }
                }
                Choreographer.getInstance().postFrameCallback(callback)
            }
            for(i in 0 until minOf(points.size,40)) {
                if(i>0) android.os.SystemClock.sleep((points[i].elapsedMs-points[i-1].elapsedMs).coerceAtMost(500))
                main { latest=points[i].elapsedMs; view.points=points.take(i+1) }
            }
            android.os.SystemClock.sleep(550)
        } finally {
            main { recording=false; Choreographer.getInstance().removeFrameCallback(callback); decor.overlay.remove(view) }
            File(directory,"attached-real-replay-motion.json").writeText(JSONObject().put("sampleId",fixture.getString("id")).put("displayOnly",true).put("rawPointsUnchanged",true).put("frames",frames).toString())
        }
        for(i in 0 until frames.length()) check(frames.getJSONObject(i).getLong("revealMs")<=frames.getJSONObject(i).getLong("latestReceivedMs"))
    }
}
