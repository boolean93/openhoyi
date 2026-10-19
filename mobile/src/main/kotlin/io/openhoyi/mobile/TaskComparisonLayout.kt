package io.openhoyi.mobile

import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.View

/** A full-width comparison plot between independently scrollable controls and cup details. */
internal class TaskComparisonLayout(private val activity: ThemedActivity, private val visual: ExtractionHistoryVisualStyle? = null) : LinearLayout(activity) {
    val toolbar = LinearLayout(activity).apply { orientation = VERTICAL }
    val details = LinearLayout(activity).apply { orientation = VERTICAL }
    private val toolbarScroll = ScrollView(activity).apply { tag = "comparison-toolbar"; addView(toolbar) }
    private val detailsScroll = ScrollView(activity).apply { if (visual != null) tag = "comparison-details-scroll"; addView(details) }
    private var chart: TwoCupChartView? = null
    internal val productLayoutEvidence = org.json.JSONObject()
    init {
        orientation = VERTICAL
        addView(toolbarScroll, LayoutParams(-1, -2))
        addView(detailsScroll, LayoutParams(-1, -2))
        if (visual != null) { tag = "product/comparison/workspace"; toolbar.tag = "product/comparison/toolbar-body"; details.tag = "product/comparison/details" }
    }
    fun setChart(value: TwoCupChartView) {
        check(chart == null)
        chart = value
        addView(value, 1, LayoutParams(-1, 0, 1f))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (visual != null) {
            measureProduct(widthMeasureSpec, heightMeasureSpec, visual)
            return
        }
        val width = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val height = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val freeHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        toolbar.measure(exactWidth, freeHeight)
        details.measure(exactWidth, freeHeight)
        val touch = HoyiUi.dp(activity, 48)
        val chartMinimum = chart?.minimumChartHeight() ?: 0
        // Cup selection and metric controls must not lose space to a scrollable summary.
        // Reserve the plot minimum and a reachable details viewport before sizing controls.
        val toolbarBudget = minOf(toolbar.measuredHeight, (height - chartMinimum - touch).coerceAtLeast(touch))
        val remainingDetails = (height - chartMinimum - toolbarBudget).coerceAtLeast(touch)
        val summaryRequired = (0 until minOf(2, details.childCount)).sumOf { index ->
            val child = details.getChildAt(index)
            val params = child.layoutParams as? LayoutParams
            child.measuredHeight + (params?.topMargin ?: 0) + (params?.bottomMargin ?: 0)
        }
        val detailBudget = if (visual == null) minOf(details.measuredHeight, height / 3, remainingDetails)
            else minOf(details.measuredHeight, maxOf(height / 3, summaryRequired), remainingDetails)
        if (visual != null) {
            productLayoutEvidence.put("availableHeightPx", height).put("toolbarNaturalPx", toolbar.measuredHeight)
                .put("toolbarBudgetPx", toolbarBudget).put("summaryAndActionsNaturalPx", summaryRequired)
                .put("detailsNaturalPx", details.measuredHeight).put("detailsViewportPx", detailBudget).put("chartMinimumPx", chartMinimum)
            // Budget probes must not leave descendants measured against an obsolete spec.
            fun invalidateMeasured(view: View) {
                view.forceLayout()
                if (view is android.view.ViewGroup) (0 until view.childCount).forEach { invalidateMeasured(view.getChildAt(it)) }
            }
            invalidateMeasured(toolbar); invalidateMeasured(details)
        }
        toolbarScroll.layoutParams.height = toolbarBudget
        detailsScroll.layoutParams.height = detailBudget
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    private fun measureProduct(widthMeasureSpec: Int, heightMeasureSpec: Int, style: ExtractionHistoryVisualStyle) {
        val width = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val height = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val exact = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        toolbar.measure(exact, natural); details.measure(exact, natural)
        val touch = HoyiUi.dp(activity, 48)
        val renderer = ProductHistoryChartRenderer(activity, style)
        val preferred = renderer.minimumComparisonHeight(160f)
        val fallback = renderer.minimumComparisonHeight(96f)
        val standard = height >= preferred + touch * 2
        val minimum = if (standard) preferred else fallback
        val minimumDetails = minOf(details.measuredHeight, touch, (height - minimum).coerceAtLeast(0))
        val toolbarBudget = minOf(toolbar.measuredHeight, (height - minimum - minimumDetails).coerceAtLeast(0))
        val detailBudget = minOf(details.measuredHeight, (height - toolbarBudget - minimum).coerceAtLeast(0))
        productLayoutEvidence.put("availableHeightPx", height).put("toolbarNaturalPx", toolbar.measuredHeight)
            .put("toolbarBudgetPx", toolbarBudget).put("toolbarLocallyScrollable", toolbar.measuredHeight > toolbarBudget)
            .put("detailsNaturalPx", details.measuredHeight).put("detailsViewportPx", detailBudget)
            .put("detailsMinimumPx", touch).put("chartMinimumPx", minimum)
            .put("preferredChartMinimumPx", preferred).put("fallbackChartMinimumPx", fallback)
            .put("minimumPlotDp", if (standard) 160 else 96).put("shortHeightFallback", !standard)
            .put("minimumViewportsSatisfiable", height >= minimum + touch * 2)
            .put("chartBudgetPx", (height - toolbarBudget - detailBudget).coerceAtLeast(0))
        fun invalidateMeasured(view: View) {
            view.forceLayout()
            if (view is android.view.ViewGroup) (0 until view.childCount).forEach { invalidateMeasured(view.getChildAt(it)) }
        }
        invalidateMeasured(toolbarScroll); invalidateMeasured(detailsScroll)
        toolbarScroll.layoutParams.height = toolbarBudget
        detailsScroll.layoutParams.height = detailBudget
        // First protect the plot and real axes, then expose as much of the real details as fits.
        // Only space beyond the complete controls and details is added to the plot.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (visual != null) productLayoutEvidence.put("chartActualHeightPx", chart?.height ?: 0)
            .put("toolbarActualHeightPx", toolbarScroll.height).put("detailsActualHeightPx", detailsScroll.height)
    }
}
