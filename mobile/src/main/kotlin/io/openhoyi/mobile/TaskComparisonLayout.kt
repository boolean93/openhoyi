package io.openhoyi.mobile

import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.View

/** A full-width comparison plot between independently scrollable controls and cup details. */
internal class TaskComparisonLayout(private val activity: ThemedActivity) : LinearLayout(activity) {
    val toolbar = LinearLayout(activity).apply { orientation = VERTICAL }
    val details = LinearLayout(activity).apply { orientation = HORIZONTAL; isBaselineAligned = false }
    private val toolbarScroll = ScrollView(activity).apply { tag = "comparison-toolbar"; addView(toolbar) }
    private val detailsScroll = ScrollView(activity).apply { addView(details) }
    private var chart: TwoCupChartView? = null
    init {
        orientation = VERTICAL
        addView(toolbarScroll, LayoutParams(-1, -2))
        addView(detailsScroll, LayoutParams(-1, -2))
    }
    fun setChart(value: TwoCupChartView) {
        check(chart == null)
        chart = value
        addView(value, 1, LayoutParams(-1, 0, 1f))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val height = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val freeHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        toolbar.measure(exactWidth, freeHeight)
        details.measure(exactWidth, freeHeight)
        val touch = HoyiUi.dp(activity, 48)
        val chartMinimum = chart?.minimumChartHeight() ?: 0
        val detailBudget = minOf(details.measuredHeight, height / 3,
            (height - chartMinimum - touch).coerceAtLeast(touch))
        val toolbarBudget = minOf(toolbar.measuredHeight, (height - chartMinimum - detailBudget).coerceAtLeast(touch))
        toolbarScroll.layoutParams.height = toolbarBudget
        detailsScroll.layoutParams.height = detailBudget
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
