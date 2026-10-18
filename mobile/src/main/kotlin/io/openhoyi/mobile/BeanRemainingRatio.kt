package io.openhoyi.mobile

import android.view.Gravity
import android.widget.LinearLayout

/** Display-only ratio: adjustments may legitimately produce more than 100 percent. */
internal fun ThemedActivity.beanRemainingRatio(parent: LinearLayout, balanceMg: Long, initialMg: Long,
    textSize: Int = TaskVisualTokens.TITLE_SP) {
    val ratio = if (initialMg > 0) balanceMg.toDouble() / initialMg else null
    val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    parent.addView(line, LinearLayout.LayoutParams(-1, -2))
    if (ratio != null) line.addView(android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        fun track(color: Int) = android.graphics.drawable.GradientDrawable().apply {
            setColor(getColor(color)); cornerRadius = HoyiUi.dp(this@beanRemainingRatio, 4).toFloat()
        }
        progressDrawable = android.graphics.drawable.LayerDrawable(arrayOf(
            track(R.color.mobile_border),
            android.graphics.drawable.ScaleDrawable(track(R.color.mobile_accent), Gravity.START, 1f, -1f)
        )).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
        progress = (ratio.coerceIn(0.0, 1.0) * max).toInt()
        setPadding(0, 0, 0, 0)
        minimumHeight = HoyiUi.dp(this@beanRemainingRatio, 8)
        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }, LinearLayout.LayoutParams(0, HoyiUi.dp(this, 8), 1f).apply {
        marginEnd = HoyiUi.dp(this@beanRemainingRatio, TaskVisualTokens.GROUP_GAP_DP)
    })
    HoyiUi.label(this, line, ratio?.let {
        java.text.NumberFormat.getPercentInstance(resources.configuration.locales[0]).format(it)
    } ?: "—", textSize).apply {
        contentDescription = getString(R.string.batch_remaining_ratio) + " " + text
    }
}
