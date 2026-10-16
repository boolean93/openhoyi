package io.openhoyi.mobile

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView

/** Stable task regions, with a local supporting scroll area. Insets belong to the page root. */
internal class TaskWorkspaceLayout(context: Context) : LinearLayout(context) {
    val primaryBody = LinearLayout(context).apply { orientation = VERTICAL }
    val secondaryBody = LinearLayout(context).apply { orientation = VERTICAL }
    private val supportingScroll = object : ScrollView(context) {
        var heightLimit = Int.MAX_VALUE
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (heightLimit == Int.MAX_VALUE) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            else super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                minOf(heightLimit, MeasureSpec.getSize(heightMeasureSpec)), MeasureSpec.AT_MOST))
        }
    }.apply { isFillViewport = false; addView(secondaryBody) }
    var fillSecondaryViewport: Boolean
        get() = supportingScroll.isFillViewport
        set(value) { supportingScroll.isFillViewport = value }
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

    init { tag = "task-workspace"; addView(primaryBody); addView(supportingScroll) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val height = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val twoPane = TaskLayoutPolicy.decide(resources.configuration.screenWidthDp.coerceAtLeast(1),
            (height / density).toInt()).twoPaneAllowed && width >= dp(624)
        val secondaryWidth = (width * .30f).toInt().coerceIn(dp(280), dp(320))
        val primaryWidth = if (twoPane) width - secondaryWidth - dp(24) else width
        // Keep every secondary action reachable even when the stage graph needs a text fallback.
        // Below 48dp plus the actual title height, both task regions cannot remain fully usable.
        val supportingMinimum = if (twoPane) 0 else minOf(dp(48), height)
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
        supportingScroll.heightLimit = if (twoPane) Int.MAX_VALUE else secondaryHeight
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
