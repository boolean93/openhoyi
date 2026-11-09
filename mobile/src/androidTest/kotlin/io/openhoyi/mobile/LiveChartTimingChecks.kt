package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.os.SystemClock

/** Actual animator listeners; synthetic timestamps never enter service or storage. */
internal class LiveChartTimingChecks(private val test: Instrumentation) {
    fun run(context: Context) {
        var failure: Throwable? = null
        test.runOnMainSync {
            try {
                fun chart(gap: Long): ExtractionOverlayView {
                    val view = ExtractionOverlayView(context, ExtractionHistoryVisualStyle(context))
                    val base = listOf(ShotPoint(29_900, 80, 0, 10, 9300, null, brewing = true))
                    view.points = base
                    view.javaClass.getDeclaredField("lastPointUpdateAt").apply { isAccessible = true }
                        .set(view, SystemClock.elapsedRealtime() - gap)
                    view.points = base + base.last().copy(elapsedMs = 30_100)
                    return view
                }
                fun field(view: ExtractionOverlayView, name: String): Any? =
                    view.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(view)
                for (gap in listOf(100L, 250L, 400L)) {
                    val view = chart(gap)
                    val animator = field(view,"renderAnimator") as android.animation.ValueAnimator
                    animator.currentPlayTime = animator.duration / 2
                    val reveal = field(view,"revealThroughMs") as Long
                    val axis = field(view,"renderTimeEnd") as Long
                    check(reveal in 29_901L..30_099L)
                    // Unattached androidTest probes snap instead of animating the axis; the
                    // reveal animation itself is still exercised above.
                    // The realtime time axis keeps start=0 and follows the observed duration:
                    // 30.1 s observed becomes a 0-30.1 s scale, and unattached probes snap to it.
                    check(axis == 30_100L) { "unattached probe must see the scaled time end" }
                    val axisStart = field(view, "renderTimeStart") as Long
                    check(axisStart == 0L) { "unattached probe must keep the time axis at zero" }
                    view.points = view.points + view.points.last().copy(elapsedMs = 30_300)
                    check(field(view,"revealThroughMs") == reveal)
                    check(field(view,"renderTimeEnd") == axis)
                    val resumed = field(view,"renderAnimator")
                    view.points = view.points
                    check(field(view,"renderAnimator") === resumed)
                    view.unavailableLiveMetrics = setOf(ChartMetric.PRESSURE)
                }
                for (cancel in listOf("stale","trace","reference","phase")) {
                    val view = chart(250)
                    check(field(view,"renderAnimator") != null)
                    when(cancel) {
                        "stale" -> view.unavailableLiveMetrics = setOf(ChartMetric.PRESSURE)
                        "trace" -> view.traceIdentity = "next-cup"
                        "reference" -> view.reference = view.points
                        else -> view.points = view.points + view.points.last().copy(elapsedMs=30_300,brewing=false,statusBits=0)
                    }
                    check(field(view,"renderAnimator") == null) { "$cancel failed to cancel" }
                }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
