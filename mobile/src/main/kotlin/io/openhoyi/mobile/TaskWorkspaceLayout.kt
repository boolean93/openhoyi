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
    private var paneMode: Boolean? = null
    // Returns the primary height needed before allocating any supporting space.
    // The callback may choose a local text fallback when even the whole height is insufficient.
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
        val primaryRequired = onPrimarySpaceAvailable?.invoke(primaryWidth.coerceAtLeast(0), height) ?: 0
        val secondaryHeight = minOf(dp(160), height / 3, (height - primaryRequired).coerceAtLeast(0))
        // Keep the same children, listeners and focus when the available area changes.
        if (paneMode != twoPane || (twoPane && supportingScroll.layoutParams.width != secondaryWidth)) {
            paneMode = twoPane
            orientation = if (twoPane) HORIZONTAL else VERTICAL
            primaryBody.layoutParams = if (twoPane) LayoutParams(0, -1, 1f) else LayoutParams(-1, 0, 1f)
            supportingScroll.layoutParams = if (twoPane) LayoutParams(secondaryWidth, -1).apply { marginStart = dp(24) }
                else LayoutParams(-1, -2)
        }
        supportingScroll.heightLimit = if (twoPane) Int.MAX_VALUE else secondaryHeight
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
