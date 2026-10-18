package io.openhoyi.mobile

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView

/** Stable task regions, with a local supporting scroll area. Insets belong to the page root. */
internal class TaskWorkspaceLayout(context: Context,
    private val supportingWidthFraction: Float = .30f,
    private val supportingMaxWidthDp: Int = 320,
    private val boundedSecondaryViewport: Boolean = false) : LinearLayout(context) {
    val primaryBody = LinearLayout(context).apply { orientation = VERTICAL }
    val secondaryBody = LinearLayout(context).apply { orientation = VERTICAL }
    private val supportingScroll = object : ScrollView(context) {
        var heightLimit = Int.MAX_VALUE
        var boundToViewport = false
        override fun measureChildWithMargins(child: View, parentWidthMeasureSpec: Int, widthUsed: Int,
            parentHeightMeasureSpec: Int, heightUsed: Int) {
            if (!boundToViewport) {
                super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed)
                return
            }
            // Same host/children for its entire lifetime. In a real split pane,
            // remove ScrollView's UNSPEC child measurement: only the page's own
            // weighted local scroll region may move, never its fixed action dock.
            val params = child.layoutParams as ViewGroup.MarginLayoutParams
            val width = getChildMeasureSpec(parentWidthMeasureSpec,
                paddingLeft + paddingRight + params.leftMargin + params.rightMargin + widthUsed, params.width)
            val height = (MeasureSpec.getSize(parentHeightMeasureSpec) - paddingTop - paddingBottom -
                params.topMargin - params.bottomMargin - heightUsed).coerceAtLeast(0)
            child.measure(width, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (heightLimit == Int.MAX_VALUE) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            else super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                minOf(heightLimit, MeasureSpec.getSize(heightMeasureSpec)), MeasureSpec.AT_MOST))
        }
    }.apply { isFillViewport = false; addView(secondaryBody) }
    var fillSecondaryViewport: Boolean
        get() = supportingScroll.isFillViewport
        set(value) { supportingScroll.isFillViewport = value }
    val secondaryViewportBounded: Boolean get() = supportingScroll.boundToViewport
    private var paneMode: Boolean? = null
    /** Optional compact readout budget; charts retain their default expanding primary region. */
    var compactPrimaryHeightDp: Int? = null
        set(value) {
            require(value == null || value > 0)
            if (field == value) return
            field = value
            requestLayout()
        }
    // Returns primary height needed within the budget after a reachable supporting viewport.
    // The callback may choose a local text fallback when that primary budget is insufficient.
    var onPrimarySpaceAvailable: ((width: Int, height: Int) -> Int)? = null

    init {
        require(supportingWidthFraction > 0f && supportingWidthFraction <= .5f)
        require(supportingMaxWidthDp >= 280)
        tag = "task-workspace"; addView(primaryBody); addView(supportingScroll)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val height = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val twoPane = TaskLayoutPolicy.decide(resources.configuration.screenWidthDp.coerceAtLeast(1),
            (height / density).toInt()).twoPaneAllowed && width >= dp(624)
        val secondaryWidth = (width * supportingWidthFraction).toInt().coerceIn(dp(280), dp(supportingMaxWidthDp))
        val primaryWidth = if (twoPane) width - secondaryWidth - dp(24) else width
        // Keep every secondary action reachable even when the stage graph needs a text fallback.
        // Below 48dp plus the actual title height, both task regions cannot remain fully usable.
        var reachableActionHeight = dp(48)
        if (boundedSecondaryViewport && !twoPane) {
            // Extremely short stacked windows keep the original whole-support
            // scroll fallback. Its viewport must still fit each actual action,
            // including a wrapped 56dp primary, rather than clip it to 48dp.
            secondaryBody.measure(MeasureSpec.makeMeasureSpec(width.coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            fun actionHeight(view: View): Int = maxOf(if (view is Button && view.visibility != GONE) view.measuredHeight else 0,
                if (view is ViewGroup) (0 until view.childCount).maxOfOrNull { actionHeight(view.getChildAt(it)) } ?: 0 else 0)
            reachableActionHeight = maxOf(dp(56), actionHeight(secondaryBody))
        }
        val supportingMinimum = if (twoPane) 0 else minOf(reachableActionHeight, height)
        val primaryBudget = height - supportingMinimum
        val primaryRequired = onPrimarySpaceAvailable?.invoke(primaryWidth.coerceAtLeast(0), primaryBudget) ?: 0
        val compactPrimaryHeight = compactPrimaryHeightDp?.let { minOf(dp(it), primaryBudget).coerceAtLeast(0) }
        val secondaryHeight = if (compactPrimaryHeight != null) height - compactPrimaryHeight else minOf(dp(160), height / 3, (height - primaryRequired).coerceAtLeast(0))
            .coerceAtLeast(supportingMinimum)
        // Keep the same children, listeners and focus when the available area changes.
        if (paneMode != twoPane || (twoPane && supportingScroll.layoutParams.width != secondaryWidth)) {
            paneMode = twoPane
            orientation = if (twoPane) HORIZONTAL else VERTICAL
            primaryBody.layoutParams = if (twoPane) LayoutParams(0, -1, 1f) else LayoutParams(-1, 0, 1f)
            supportingScroll.layoutParams = if (twoPane) LayoutParams(secondaryWidth, -1).apply { marginStart = dp(24) }
                else LayoutParams(-1, -2)
        }
        if (!twoPane) {
            val params = primaryBody.layoutParams as LayoutParams
            val desiredHeight = compactPrimaryHeight ?: 0
            val desiredWeight = if (compactPrimaryHeight == null) 1f else 0f
            if (params.height != desiredHeight || params.weight != desiredWeight) {
                primaryBody.layoutParams = LayoutParams(-1, desiredHeight, desiredWeight)
            }
        }
        supportingScroll.boundToViewport = boundedSecondaryViewport && twoPane
        supportingScroll.heightLimit = if (twoPane) Int.MAX_VALUE else secondaryHeight
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
