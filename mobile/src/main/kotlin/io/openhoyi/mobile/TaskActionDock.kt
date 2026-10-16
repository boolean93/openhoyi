package io.openhoyi.mobile

import android.content.Context
import android.widget.LinearLayout

/** Fixed task action, aligned with the supporting pane without joining its scroll area. */
internal class TaskActionDock(context: Context, edgeInsetDp: Int = 12) : LinearLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        gravity = android.view.Gravity.END
        setPadding(dp(edgeInsetDp), 0, dp(edgeInsetDp), dp(4))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val wide = resources.configuration.screenWidthDp >= 840 && available >= dp(624)
        val actionWidth = if (wide) (available * .30f).toInt()
            .coerceIn(dp(280), dp(320)) else available
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as LayoutParams
            // Mutate the measurement input, not request another parent pass from onMeasure.
            params.width = actionWidth
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
