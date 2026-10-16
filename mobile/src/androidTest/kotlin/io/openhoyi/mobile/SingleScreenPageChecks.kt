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
        if (activity is HistoryDetailActivity) {
            val charts = views.filterIsInstance<ShotChartView>()
            check(charts.size == 1 && charts.single().points.size >= 2) { "$fixture missing populated historical chart" }
            val plot = charts.single().findViewWithTag<View>("chart-plot")
            check(plot.visibility == View.VISIBLE && plot.height >= HoyiUi.dp(activity, 96)) {
                "$fixture historical four-metric chart has no usable plot"
            }
        }
        if (activity is BrewComparisonActivity) {
            val chart = views.filterIsInstance<TwoCupChartView>().single()
            check(chart.firstPoints.size >= 2 && chart.secondPoints.size >= 2) { "$fixture two real comparison sources not populated" }
            check(chart.plotBounds.height() >= HoyiUi.dp(activity, 96)) { "$fixture comparison lost its usable shared plot" }
        }
        if (activity is LegacyHistoryDetailActivity) check(views.any { it is LegacyShotChartView }) {
            "$fixture populated legacy chart not inspected"
        }
        if (activity is LegacyCurveActivity) check(views.filterIsInstance<ListView>().single().count > 0) {
            "$fixture imported read-only curves not displayed"
        }
        views.filterIsInstance<ListView>().forEach { visibleFully(it) }
        views.filterIsInstance<ScrollView>().forEach { viewport ->
            visibleFully(viewport)
            check(viewport.parent !== content.getChildAt(0)) { "$fixture whole page is a ScrollView" }
            if (activity is CurveActivity) {
                val fallback = CurveActivity::class.java.getDeclaredField("previewFallbackScroll")
                    .apply { isAccessible = true }.get(activity) as ScrollView
                if (viewport === fallback) {
                    check(viewport.height > 0 && viewport.childCount == 1) { "$fixture explicit stage details are unreachable" }
                    val text = CurveActivity::class.java.getDeclaredField("previewFallback")
                        .apply { isAccessible = true }.get(activity) as TextView
                    check(text.text.isNotBlank())
                    LanguageUiLayoutChecks.textFits(text, "$fixture local stage fallback")
                } else check(viewport.childCount == 0 || viewport.getChildAt(0).height <= viewport.height - viewport.paddingTop - viewport.paddingBottom) {
                    "$fixture normal graphical preview unexpectedly scrolls"
                }
            }
        }
        views.filter { it is TwoCupChartView || it is ShotChartView || it is CurveStageView || it is ExtractionOverlayView || it is LegacyShotChartView }.forEach { chart ->
            visibleFully(chart)
            check(chart.height >= HoyiUi.dp(activity, 96)) { "$fixture chart lost its usable viewport: ${chart.height}" }
            if (chart is ExtractionOverlayView && HoyiUi.wide(activity)) check(chart.height >= content.height * .4f) {
                "$fixture wide extraction plot is not the primary area: ${chart.height}/${content.height}"
            }
        }
    }
}
