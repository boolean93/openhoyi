package io.openhoyi.mobile

import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout

/** Same live buffer in the inline plot or an explicit short-height detail window. */
internal class ScaleTrendPanel(private val activity: ThemedActivity, primary: LinearLayout, secondary: LinearLayout) {
    private val block = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        tag = "scale-trend"
        primary.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private val title = HoyiUi.label(activity, block, activity.getString(R.string.scale_trend_title), 16, true)
    private val plot = ScaleTrendView(activity)
    private val status = HoyiUi.label(activity, block, activity.getString(R.string.scale_trend_empty), 14, muted = true)
    private var dialog: AlertDialog? = null
    private var detailPlot: ScaleTrendView? = null
    private var detailStatus: android.widget.TextView? = null
    private var values = emptyList<ScaleTrendWindow.Point>()
    private var now = 0L
    private val fallback = HoyiUi.button(activity, secondary, activity.getString(R.string.scale_trend_title)) { open() }.apply {
        tag = "scale-trend-fallback"
    }
    init {
        title.setPadding(0, dp(8), 0, dp(8))
        block.addView(plot, 1, LinearLayout.LayoutParams(-1, dp((180 * activity.resources.configuration.fontScale).toInt())))
    }
    private fun dp(value: Int) = HoyiUi.dp(activity, value)
    fun fit(width: Int, available: Int): Int {
        block.measure(View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(0), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val inline = block.measuredHeight <= available
        block.visibility = if (inline) View.VISIBLE else View.GONE
        fallback.visibility = if (inline) View.GONE else View.VISIBLE
        return if (inline) block.measuredHeight else 0
    }
    fun present(points: List<ScaleTrendWindow.Point>, at: Long) {
        values = points; now = at
        plot.present(points, at)
        if (status.text.toString() != plot.contentDescription.toString()) status.text = plot.contentDescription
        detailPlot?.present(points, at)
        detailStatus?.let { if (it.text.toString() != plot.contentDescription.toString()) it.text = plot.contentDescription }
    }
    private fun open() {
        dismiss()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val chart = ScaleTrendView(activity).also { detailPlot = it; it.present(values, now) }
        content.addView(chart, LinearLayout.LayoutParams(-1, dp(240)))
        detailStatus = HoyiUi.label(activity, content, plot.contentDescription.toString(), 14, muted = true)
        dialog = AlertDialog.Builder(activity).setTitle(R.string.scale_trend_title).setView(android.widget.ScrollView(activity).apply { addView(content) })
            .setNegativeButton(android.R.string.cancel, null).create().also {
                it.setOnDismissListener { detailPlot = null; detailStatus = null; dialog = null }
                it.show()
            }
    }
    fun dismiss() { dialog?.dismiss(); dialog = null; detailPlot = null; detailStatus = null }
}
