package io.openhoyi.mobile

import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout

/** Same live buffer in the inline plot or an explicit short-height detail window. */
internal class ScaleTrendPanel(private val activity: ThemedActivity, primary: LinearLayout, secondary: LinearLayout,
    private val visual: ScaleVisualStyle? = null) {
    val productLayoutEvidence = org.json.JSONObject()
    private val block = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        tag = "scale-trend"
        primary.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private val title = HoyiUi.label(activity, block, activity.getString(R.string.scale_trend_title), 16, true)
    private val plot = ScaleTrendView(activity, visual)
    private val status = HoyiUi.label(activity, block, activity.getString(R.string.scale_trend_empty), 14, muted = true)
    private var dialog: AlertDialog? = null
    private var detailPlot: ScaleTrendView? = null
    private var detailStatus: android.widget.TextView? = null
    private var values = emptyList<ScaleTrendWindow.Point>()
    private var now = 0L
    private var sourceDescription = ""
    private fun detailSummary() = listOf(sourceDescription, plot.contentDescription.toString())
        .filter { it.isNotBlank() }.joinToString("\n")
    private val fallback = HoyiUi.button(activity, secondary, activity.getString(R.string.scale_trend_title)) { open() }.apply {
        tag = "scale-trend-fallback"
    }
    init {
        title.setPadding(0, dp(8), 0, dp(8))
        block.addView(plot, 1, LinearLayout.LayoutParams(-1, dp((180 * activity.resources.configuration.fontScale).toInt())))
        visual?.palette?.let { style ->
            style.apply(title,TaskProductVisualStyle.section);title.setPadding(0,0,0,0)
            style.apply(status,TaskProductVisualStyle.auxiliary,true)
            block.removeView(title);block.removeView(status)
            val heading=HistoryToolbarRow(activity,dp(16),dp(8)) {
                kotlin.math.ceil(title.paint.measureText(title.text.toString())).toInt()+title.compoundPaddingLeft+title.compoundPaddingRight
            }
            heading.addView(title);heading.addView(status)
            block.addView(heading,0,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(8)})
            (block.layoutParams as LinearLayout.LayoutParams).topMargin=dp(16)
            plot.layoutParams.height=kotlin.math.ceil(180f*activity.resources.displayMetrics.density*activity.resources.configuration.fontScale).toInt()
            style.button(fallback,outline=true);HomeVisualIcon.buttonIcon(fallback,"info")
        }
    }
    private fun dp(value: Int) = HoyiUi.dp(activity, value)
    fun fit(width: Int, available: Int): Int {
        block.measure(View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(0), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val margin=if(visual==null)0 else (block.layoutParams as LinearLayout.LayoutParams).topMargin
        val required=block.measuredHeight+margin
        val inline = required <= available
        block.visibility = if (inline) View.VISIBLE else View.GONE
        fallback.visibility = if (inline) View.GONE else View.VISIBLE
        if(visual!=null)productLayoutEvidence.put("availableHeightPx",available).put("naturalBlockHeightPx",block.measuredHeight)
            .put("groupMarginPx",margin).put("requiredHeightPx",required).put("inline",inline).put("plotMinimumHeightPx",plot.layoutParams.height)
        return if (inline) required else 0
    }
    fun present(points: List<ScaleTrendWindow.Point>, at: Long, sourceDescription: String) {
        values = points; now = at; this.sourceDescription = sourceDescription
        plot.present(points, at)
        if (status.text.toString() != plot.contentDescription.toString()) status.text = plot.contentDescription
        detailPlot?.present(points, at)
        detailStatus?.let { if (it.text.toString() != detailSummary()) it.text = detailSummary() }
    }
    private fun open() {
        dismiss()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val chart = ScaleTrendView(activity,visual).also { detailPlot = it; it.present(values, now) }
        content.addView(chart, LinearLayout.LayoutParams(-1, if(visual==null)dp(240) else maxOf(dp(240),kotlin.math.ceil(180f*activity.resources.displayMetrics.density*activity.resources.configuration.fontScale).toInt())))
        detailStatus = HoyiUi.label(activity, content, detailSummary(), 14, muted = true)
        visual?.palette?.apply {apply(requireNotNull(detailStatus),TaskProductVisualStyle.auxiliary,true);content.setBackgroundColor(surface)}
        dialog = AlertDialog.Builder(activity).setTitle(R.string.scale_trend_title).setView(android.widget.ScrollView(activity).apply { addView(content) })
            .setNegativeButton(android.R.string.cancel, null).create().also {
                it.setOnDismissListener { detailPlot = null; detailStatus = null; dialog = null }
                AppDialog.show(it)
                visual?.palette?.let { style->it.window?.setBackgroundDrawable(style.shape(style.surface));style.button(it.getButton(AlertDialog.BUTTON_NEGATIVE),outline=true) }
                AppDialog.styleShown(it)
            }
    }
    fun dismiss() { dialog?.dismiss(); dialog = null; detailPlot = null; detailStatus = null }
}
