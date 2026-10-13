package io.openhoyi.mobile

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import android.widget.TextView

/** Actual attached-window geometry, not a detached component or a synthetic height budget. */
internal object SingleScreenPageChecks {
    /** Call on the UI thread after layout. This never clicks or changes device state. */
    fun verify(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        if (activity is ExtractionActivity) TrendChartTextChecks.run(activity, "single-screen overlay")
        val fixture = activity.javaClass.simpleName + " " + activity.resources.configuration
        fun visibleFully(view: View) {
            val rect = Rect()
            check(view.width > 0 && view.height > 0 && view.getGlobalVisibleRect(rect) &&
                rect.width() == view.width && rect.height() == view.height) {
                "$fixture clipped ${view.javaClass.simpleName}: ${view.width}x${view.height} visible=$rect text=${(view as? TextView)?.text} parent=${(view.parent as? View)?.height} top=${view.top} bottom=${view.bottom}"
            }
        }
        val views = mutableListOf<View>()
        fun visit(view: View) {
            if (!view.isShown) return
            views += view
            // List rows intentionally scroll inside their bounded local viewport.
            if (view is ViewGroup && view !is ListView && view !is ScrollView && view !is HorizontalScrollView) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(content)
        views.filterIsInstance<TextView>().filter { it.text.isNotEmpty() }.forEach {
            visibleFully(it)
            if (it.tag != "full-text-details") LanguageUiLayoutChecks.textFits(it, fixture)
        }
        views.filter { it.isClickable && it !is ScrollView && it !is HorizontalScrollView && it !is ListView }.forEach {
            visibleFully(it)
            check(it.height >= HoyiUi.dp(activity, 48)) { "$fixture clickable target below 48dp: ${it.javaClass.simpleName} ${it.height}" }
        }
        if (activity is HistoryDetailActivity || activity is BrewComparisonActivity) {
            val charts = views.filterIsInstance<ShotChartView>()
            check(charts.size == if (activity is BrewComparisonActivity) 2 else 1) { "$fixture missing historical chart" }
            check(charts.all { it.points.size >= 2 }) { "$fixture historical charts are not populated" }
        }
        if (activity is LegacyHistoryDetailActivity) check(views.any { it is LegacyShotChartView }) {
            "$fixture populated legacy chart not inspected"
        }
        views.filterIsInstance<ListView>().forEach { visibleFully(it) }
        views.filterIsInstance<ScrollView>().forEach { viewport ->
            visibleFully(viewport)
            check(viewport.parent !== content.getChildAt(0)) { "$fixture whole page is a ScrollView" }
            if (activity is CurveActivity) check(viewport.childCount == 0 || viewport.getChildAt(0).height <= viewport.height - viewport.paddingTop - viewport.paddingBottom) {
                "$fixture page preview unexpectedly scrolls"
            }
        }
        views.filter { it is ShotChartView || it is CurveStageView || it is ExtractionOverlayView || it is LegacyShotChartView }.forEach { chart ->
            visibleFully(chart)
            check(chart.height >= HoyiUi.dp(activity, 96)) { "$fixture chart lost its usable viewport: ${chart.height}" }
        }
    }
}
