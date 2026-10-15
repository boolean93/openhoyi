package io.openhoyi.mobile

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Moves views, never recreates page content or owns machine state. Insets remain on the parent. */
internal class TaskPageFrame private constructor(private val activity: Activity) : LinearLayout(activity) {
    val pageContent = LinearLayout(activity).apply { orientation = VERTICAL }
    val navigation = TaskNavigationView(activity)
    private val railScroll = ScrollView(activity).apply { isFillViewport = true }
    private val fallbackTools = TaskNavigationView.createToolsHost(activity).apply { tag = "task-tools-fallback" }
    private var fullRailHeight = -1
    private var measuredFontScale = -1f
    var mode = TaskNavigationMode.BOTTOM
        private set
    var toolsInTopBar = true
        private set
    private var configured: Pair<TaskNavigationMode, Boolean>? = null

    init { tag = TAG; railScroll.addView(navigation) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val policy = TaskLayoutPolicy.decide(resources.configuration.screenWidthDp.coerceAtLeast(1), (height / density).toInt())
        // Measure actual labels at the rail width; font scaling can require the compact tool menu.
        if (fullRailHeight < 0 || measuredFontScale != resources.configuration.fontScale) {
            navigation.render(TaskNavigationMode.RAIL, true)
            navigation.measure(MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, 72), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            fullRailHeight = navigation.measuredHeight
            measuredFontScale = resources.configuration.fontScale
        }
        val compactTools = policy.toolsInTopBar || policy.navigation == TaskNavigationMode.RAIL && fullRailHeight > height
        mode = policy.navigation
        toolsInTopBar = compactTools
        navigation.render(mode, mode == TaskNavigationMode.RAIL && !compactTools)
        val next = mode to compactTools
        if (configured != next) {
            configured = next
            removeAllViews()
            if (navigation.parent === this) removeView(navigation)
            if (navigation.parent === railScroll) railScroll.removeView(navigation)
            orientation = if (mode == TaskNavigationMode.RAIL) HORIZONTAL else VERTICAL
            if (mode == TaskNavigationMode.RAIL) {
                railScroll.addView(navigation, android.widget.FrameLayout.LayoutParams(-1, -2))
                addView(railScroll, LayoutParams(HoyiUi.dp(activity, TaskLayoutPolicy.RAIL_WIDTH_DP), -1))
                addView(pageContent, LayoutParams(0, -1, 1f))
            } else {
                addView(pageContent, LayoutParams(-1, 0, 1f))
                addView(navigation, LayoutParams(-1, -2))
            }
        }
        val host = pageContent.findViewWithTag<View>("task-tools-host")
        if (host != null) {
            host.visibility = if (compactTools) VISIBLE else GONE
            if (fallbackTools.parent === pageContent) pageContent.removeView(fallbackTools)
        } else if (compactTools) {
            if (fallbackTools.parent == null) pageContent.addView(fallbackTools, 0, LayoutParams(-1, -2))
            fallbackTools.visibility = VISIBLE
        } else fallbackTools.visibility = GONE
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    companion object {
        const val TAG = "task-page-frame"
        fun install(activity: Activity, parent: LinearLayout): TaskPageFrame {
            (parent.findViewWithTag<View>(TAG) as? TaskPageFrame)?.let { return it }
            val frame = TaskPageFrame(activity)
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
