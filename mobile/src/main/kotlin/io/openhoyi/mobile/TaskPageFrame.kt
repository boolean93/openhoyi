package io.openhoyi.mobile

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Moves views, never recreates page content or owns machine state. Insets remain on the parent. */
internal class TaskPageFrame private constructor(private val activity: Activity, private val productVisualStyle: TaskProductVisualStyle? = null,
    destinationProvider: () -> TaskDestination? = { TaskNavigationModel.forPage(activity.javaClass.simpleName) },
    onDestination: (TaskDestination) -> Unit = {}) : LinearLayout(activity) {
    val pageContent = LinearLayout(activity).apply { orientation = VERTICAL }
    val navigation = if (onDestination === NO_ACTION) TaskNavigationView(activity, productVisualStyle)
        else TaskNavigationView(activity, productVisualStyle, destinationProvider, onDestination)
    private val railScroll = ScrollView(activity).apply { isFillViewport = true }
    private val railColumn = LinearLayout(activity).apply { orientation = VERTICAL }
    private var shellStatus: View? = null
    private val fallbackTools = TaskNavigationView.createToolsHost(activity, if (onDestination === NO_ACTION) { destination ->
        activity.startActivity(android.content.Intent(activity, when (destination) {
            TaskDestination.BREW -> HomeActivity::class.java
            TaskDestination.CURVES -> CurveActivity::class.java
            TaskDestination.HISTORY -> HistoryActivity::class.java
            TaskDestination.BEANS -> BeanInventoryActivity::class.java
            TaskDestination.SCALE -> ScaleActivity::class.java
            TaskDestination.SETTINGS -> AppSettingsActivity::class.java
        }))
    } else onDestination).apply { tag = "task-tools-fallback"; productVisualStyle?.styleHeader(this) }
    private var fullRailHeight = -1
    private var measuredFontScale = -1f
    var mode = TaskNavigationMode.BOTTOM
        private set
    var toolsInTopBar = true
        private set
    private var configuredMode: TaskNavigationMode? = null
    private var allDestinations = false
    private var secondaryPage = false
    private val railWidthDp get() = if (allDestinations && activity is MainActivity) 104
        else if (productVisualStyle == null) TaskLayoutPolicy.RAIL_WIDTH_DP else 80

    init {
        tag = TAG
        railScroll.addView(navigation)
        railColumn.addView(railScroll, LayoutParams(-1, 0, 1f))
        // FragmentManager restores pages in FragmentActivity.onStart, before this view's
        // first measure. Keep the page container attached as soon as setContentView runs.
        railScroll.removeView(navigation)
        orientation = VERTICAL
        addView(pageContent, LayoutParams(-1, 0, 1f))
        addView(navigation, LayoutParams(-1, -2))
        configuredMode = TaskNavigationMode.BOTTOM
    }

    fun installShellStatus(view: View) {
        require(allDestinations) { "Shell status belongs to MainActivity only" }
        shellStatus = view
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val policy = TaskLayoutPolicy.decide(resources.configuration.screenWidthDp.coerceAtLeast(1), (height / density).toInt())
        // Measure actual labels at the rail width; font scaling can require the compact tool menu.
        if (fullRailHeight < 0 || measuredFontScale != resources.configuration.fontScale) {
            navigation.render(TaskNavigationMode.RAIL, true)
            navigation.measure(MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, railWidthDp), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            fullRailHeight = navigation.measuredHeight
            measuredFontScale = resources.configuration.fontScale
        }
        val compactTools = !secondaryPage && !allDestinations &&
            (policy.toolsInTopBar || policy.navigation == TaskNavigationMode.RAIL && fullRailHeight > height)
        mode = policy.navigation
        toolsInTopBar = compactTools
        navigation.render(mode, allDestinations || mode == TaskNavigationMode.RAIL && !compactTools,
            compactRail = allDestinations && height / density <= 360f)
        navigation.visibility = if (secondaryPage) GONE else VISIBLE
        railColumn.visibility = if (secondaryPage) GONE else VISIBLE
        if (configuredMode != mode) {
            configuredMode = mode
            removeAllViews()
            if (navigation.parent === this) removeView(navigation)
            if (navigation.parent === railScroll) railScroll.removeView(navigation)
            orientation = if (mode == TaskNavigationMode.RAIL) HORIZONTAL else VERTICAL
            if (mode == TaskNavigationMode.RAIL) {
                railScroll.addView(navigation, android.widget.FrameLayout.LayoutParams(-1, -2))
                addView(railColumn, LayoutParams(HoyiUi.dp(activity, railWidthDp), -1))
                addView(pageContent, LayoutParams(0, -1, 1f))
            } else {
                addView(pageContent, LayoutParams(-1, 0, 1f))
                addView(navigation, LayoutParams(-1, -2))
            }
        }
        shellStatus?.let { status ->
            val railStatus = mode == TaskNavigationMode.RAIL && status is TaskShellStatusView &&
                height >= fullRailHeight + status.requiredRailHeight(HoyiUi.dp(activity, railWidthDp))
            val target = if (railStatus) railColumn else pageContent
            if (status.parent !== target) {
                (status.parent as? android.view.ViewGroup)?.removeView(status)
                if (target === railColumn) target.addView(status, LayoutParams(-1, -2))
                else target.addView(status, 0, LayoutParams(-1, -2))
            }
            (status as? TaskShellStatusView)?.renderMode(if (railStatus) TaskNavigationMode.RAIL else TaskNavigationMode.BOTTOM)
        }
        val host = pageContent.findViewWithTag<View>("task-tools-host")
        // Settings retain their original scrolling content. Tools must not scroll away with its header.
        var ancestor = host?.parent
        var scrollingHeader = false
        while (ancestor is View && ancestor !== pageContent) {
            if (ancestor is ScrollView || ancestor is android.widget.HorizontalScrollView) scrollingHeader = true
            ancestor = ancestor.parent
        }
        if (host != null && !scrollingHeader) {
            host.visibility = if (compactTools) VISIBLE else GONE
            if (fallbackTools.parent === pageContent) pageContent.removeView(fallbackTools)
        } else if (compactTools) {
            host?.visibility = GONE
            if (fallbackTools.parent == null) pageContent.addView(fallbackTools, 0, LayoutParams(-1, -2))
            fallbackTools.visibility = VISIBLE
        } else {
            host?.visibility = GONE
            fallbackTools.visibility = GONE
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    companion object {
        const val TAG = "task-page-frame"
        private val NO_ACTION: (TaskDestination) -> Unit = {}
        fun host(activity: Activity, style: TaskProductVisualStyle, selected: () -> TaskDestination,
            onDestination: (TaskDestination) -> Unit): TaskPageFrame =
            TaskPageFrame(activity, style, selected, onDestination).apply { allDestinations = true }
        fun install(activity: Activity, parent: LinearLayout, productVisualStyle: TaskProductVisualStyle? = null): TaskPageFrame {
            (parent.findViewWithTag<View>(TAG) as? TaskPageFrame)?.let {
                check(productVisualStyle == null || it.productVisualStyle === productVisualStyle) { "Page style must be supplied on initial frame installation" }
                return it
            }
            val frame = TaskPageFrame(activity, productVisualStyle, onDestination = NO_ACTION).apply {
                secondaryPage = activity.javaClass.simpleName !in setOf("HomeActivity", "ExtractionActivity", "CurveActivity",
                    "HistoryActivity", "BeanInventoryActivity", "ScaleActivity", "AppSettingsActivity") ||
                    (activity is ScaleActivity && activity.intent.getBooleanExtra(ScaleActivity.EXTRA_CAPTURE_BEAN_DOSE, false))
            }
            while (parent.childCount > 0) {
                val child = parent.getChildAt(0)
                val params = child.layoutParams
                parent.removeViewAt(0)
                frame.pageContent.addView(child, params)
            }
            parent.addView(frame, LayoutParams(-1, -1))
            return frame
        }
    }
}
