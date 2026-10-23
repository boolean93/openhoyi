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
    fun verify(activity: Activity, expectedLegacyShot: LegacyShot? = null) {
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
        val config = activity.resources.configuration
        if (activity is ScaleActivity && config.screenWidthDp in 600..839 &&
            content.height >= HoyiUi.dp(activity, 850) && config.fontScale <= 1.3f &&
            config.locales[0].language == "zh") {
            val all = mutableListOf<View>()
            fun descend(view: View) {
                all += view
                if (view is ViewGroup) for (i in 0 until view.childCount) descend(view.getChildAt(i))
            }
            descend(content)
            // On the user's tall portrait tablet, routine tools must not sit below a local fold.
            listOf(R.string.scale_timer_reset, R.string.scale_connect, R.string.scale_settings).forEach { id ->
                val button = all.filterIsInstance<Button>().single { it.text.toString() == activity.getString(id) }
                visibleFully(button)
                LanguageUiLayoutChecks.textFits(button, "$fixture portrait scale tool")
            }
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
        if (activity is LegacyHistoryDetailActivity) {
            val expected=requireNotNull(expectedLegacyShot) { "$fixture exact LegacyShot fixture must be supplied by its caller" }
            check(activity.intent.getStringExtra("legacyId")==expected.id) { "$fixture legacyId does not match original fixture" }
            // Generic visit intentionally stops at ScrollView. Inspect only this explicit local viewport,
            // not every scrolling subtree, and do not add its long chart to the global visibleFully list.
            val outers=views.filterIsInstance<LegacyDetailContentScroll>()
            check(outers.size==1) { "$fixture Legacy Detail path requires exactly one visible content-scroll; found ${outers.size}" }
            val outer=outers.single()
            check(outer.tag=="legacy/detail/content-scroll" && outer.isAttachedToWindow && outer.isShown &&
                outer.rootView===content.rootView) { "$fixture Legacy Detail path invalid content-scroll tag/attachment/root" }
            check(outer.childCount==1) { "$fixture Legacy Detail path content-scroll must have one direct content child" }
            val column=outer.getChildAt(0)
            check(column is ViewGroup && column.tag=="legacy/detail/content" && column.parent===outer) {
                "$fixture Legacy Detail path invalid direct content column/tag/parent" }
            val workspaces=(0 until column.childCount).map {column.getChildAt(it)}.filterIsInstance<TaskWorkspaceLayout>()
            check(workspaces.size==1) { "$fixture Legacy Detail path requires one direct TaskWorkspaceLayout; found ${workspaces.size}" }
            val workspace=workspaces.single()
            check(workspace.parent===column && workspace.primaryBody.parent===workspace) {
                "$fixture Legacy Detail path invalid workspace/primaryBody parent" }
            val primary=workspace.primaryBody
            val viewports=(0 until primary.childCount).map {primary.getChildAt(it)}.filterIsInstance<LegacyChartViewport>()
            check(viewports.size==1) { "$fixture Legacy Detail path requires one direct LegacyChartViewport; found ${viewports.size}" }
            val viewport=viewports.single()
            check(viewport.parent===primary && viewport.tag=="legacy/chart/viewport" &&
                viewport.chart.tag=="legacy/detail/chart") {
                "$fixture Legacy Detail path invalid viewport/parent/chart tag" }
            val chart=viewport.chart
            check(viewport.childCount==1 && viewport.getChildAt(0)===chart && chart.parent===viewport)
            check(viewport.isAttachedToWindow && chart.isAttachedToWindow && viewport.isShown && chart.isShown &&
                chart.rootView===viewport.rootView && viewport.rootView===content.rootView &&
                chart.windowToken!=null && chart.windowToken===viewport.windowToken && chart.display?.displayId==viewport.display?.displayId)
            check(chart.points.size>=2 && chart.points==expected.points) { "$fixture legacy chart differs from the supplied original samples" }
            visibleFully(viewport)
            val root=viewport.rootView
            check(root.isAttachedToWindow && viewport.windowToken===root.windowToken && root.display!=null &&
                viewport.display?.displayId==root.display?.displayId && chart.display!=null)
            var ancestor:View?=chart
            while(ancestor!=null) {
                val target=ancestor
                check(target.matrix.isIdentity && target.scaleX==1f && target.scaleY==1f && target.rotation==0f &&
                    target.rotationX==0f && target.rotationY==0f && target.translationX==0f && target.translationY==0f)
                if(target===root)break
                ancestor=target.parent as? View
            }
            check(ancestor===root)
            val rootScreen=IntArray(2);root.getLocationOnScreen(rootScreen)
            val rootRaw=Rect();val rootGlobalOffset=android.graphics.Point()
            check(root.getGlobalVisibleRect(rootRaw,rootGlobalOffset))
            val raw=Rect();val viewportOffset=android.graphics.Point()
            check(viewport.getGlobalVisibleRect(raw,viewportOffset))
            val dx=rootScreen[0]-rootGlobalOffset.x-root.scrollX
            val dy=rootScreen[1]-rootGlobalOffset.y-root.scrollY
            val visibleScreen=Rect(raw).apply {offset(dx,dy)}
            val viewportScreen=IntArray(2);viewport.getLocationOnScreen(viewportScreen)
            check(viewportOffset.x+viewport.scrollX+dx==viewportScreen[0] && viewportOffset.y+viewport.scrollY+dy==viewportScreen[1])
            check(visibleScreen==Rect(viewportScreen[0],viewportScreen[1],viewportScreen[0]+viewport.width,viewportScreen[1]+viewport.height))
            val chartScreen=IntArray(2);chart.getLocationOnScreen(chartScreen)
            val bounds=chart.visualEvidence.getJSONArray("plotBoundsPx")
            check(bounds.length()==4)
            val localPlot=android.graphics.RectF(bounds.getDouble(0).toFloat(),bounds.getDouble(1).toFloat(),
                bounds.getDouble(2).toFloat(),bounds.getDouble(3).toFloat())
            val minimum=96*activity.resources.displayMetrics.density
            check(localPlot.height()>=minimum && localPlot.width()>=minimum && localPlot.left>=0 && localPlot.top>=0 &&
                localPlot.right<=chart.width && localPlot.bottom<=chart.height) { "$fixture actual legacy plot has no usable 96dp extent: $localPlot" }
            val screenPlot=android.graphics.RectF(localPlot).apply {offset(chartScreen[0].toFloat(),chartScreen[1].toFloat())}
            val visiblePlot=android.graphics.RectF(screenPlot)
            check(visiblePlot.intersect(android.graphics.RectF(visibleScreen)) && visiblePlot.height()>=minimum && visiblePlot.width()>=minimum) {
                "$fixture initial legacy plot is not visible for 96dp: screenPlot=$screenPlot viewportScreen=$visibleScreen rawGlobal=$raw rootGlobalPoint=$rootGlobalOffset rootScreen=${rootScreen.toList()}"
            }
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
