package io.openhoyi.mobile

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

/** Readout and real ±48dp touches. No text draft or separate Apply action. */
internal class SettingsBoilerTargetRow(context: Context, visual: SettingsVisualStyle,
    val boiler: BoilerTargetSteps.Boiler, label: String, private val step: (Int) -> Unit
) : LinearLayout(context) {
    private val style = visual.palette
    private val value = style.text("—", TaskProductVisualStyle.action).apply { gravity = Gravity.CENTER }
    private val caption = style.text("", TaskProductVisualStyle.auxiliary, secondary = true).apply {
        visibility = GONE
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val minus = button(-1, label)
    private val plus = button(1, label)
    init {
        orientation = VERTICAL
        tag = "product/settings/machine/${if (boiler == BoilerTargetSteps.Boiler.BREW) "brew" else "steam"}/row"
        setPadding(0, style.dp(4), 0, style.dp(4))
        val quantity = style.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.InsetDrawable(style.shape(style.selected, radius = 10),
                0, style.dp(5), 0, style.dp(5))
            setPadding(0,0,0,0)
            addView(minus, LayoutParams(style.dp(48), -2))
            addView(value, LayoutParams(-2, -2).apply { marginStart = style.dp(8) })
            addView(style.text("°C", TaskProductVisualStyle.auxiliary, secondary = true),
                LayoutParams(-2, -2).apply { marginStart = style.dp(4); marginEnd = style.dp(8) })
            addView(plus, LayoutParams(style.dp(48), -2))
        }
        style.add(this, visual.pair(style.text(label, TaskProductVisualStyle.Type(16, 550, 24)),
            quantity, "settings/machine/target-pair", gapDp = 8))
        style.add(this, caption)
    }
    private fun button(delta: Int, label: String) = style.button(Button(context)).apply {
        text = if (delta < 0) "−" else "+"
        contentDescription = resources.getString(if (delta < 0) R.string.settings_hig_decrease
            else R.string.settings_hig_increase, label)
        tag = "settings/machine/target-step/$delta"
        minHeight = style.dp(48); minimumHeight = minHeight
        minWidth = style.dp(48); minimumWidth = minWidth
        background = android.graphics.drawable.InsetDrawable(style.shape(style.surface, radius = 8),
            style.dp(3), style.dp(7), style.dp(3), style.dp(7))
        setOnClickListener { step(delta) }
    }
    fun show(target: Int?, enabled: Boolean, feedback: BoilerTargetSteps.Feedback?) {
        val rendered = target?.toString() ?: "—"
        if (value.text.toString() != rendered) value.text = rendered
        minus.isEnabled = enabled && target != null && target in boiler.limits && target > boiler.limits.first
        plus.isEnabled = enabled && target != null && target in boiler.limits && target < boiler.limits.last
        val label = feedback?.let { resources.getString(when (it) {
            BoilerTargetSteps.Feedback.QUEUED -> R.string.settings_target_queued
            BoilerTargetSteps.Feedback.CONFIRMING -> R.string.machine_settings_setting_waiting
            BoilerTargetSteps.Feedback.FAILED -> R.string.machine_settings_setting_failed
            BoilerTargetSteps.Feedback.UNKNOWN -> R.string.machine_settings_setting_unknown
            BoilerTargetSteps.Feedback.NOT_SENT -> R.string.settings_target_not_sent
        }) }.orEmpty()
        if (caption.text.toString() != label) caption.text = label
        caption.visibility = if (label.isEmpty()) View.GONE else View.VISIBLE
    }
}
