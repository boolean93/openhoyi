package io.openhoyi.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

/** A small exclusive choice. Only observed state is painted as selected. */
internal class SettingsBinaryChoice(
    context: Context, private val style: TaskProductVisualStyle,
    first: CharSequence, second: CharSequence, private val request: (Boolean) -> Unit,
) : LinearLayout(context) {
    private var observed: Boolean? = null
    init {
        orientation = HORIZONTAL
        isBaselineAligned = false
        background = style.shape(style.page, radius = 10)
        setPadding(style.dp(2), style.dp(2), style.dp(2), style.dp(2))
        listOf(first, second).forEachIndexed { index, caption ->
            val choice = style.button(Button(context)).apply {
                gravity = Gravity.CENTER
                text = caption
                style.apply(this, TaskProductVisualStyle.auxiliary)
                minHeight = style.dp(48); minimumHeight = minHeight
                minimumWidth = style.dp(48)
                setPaddingRelative(style.dp(6), style.dp(4), style.dp(6), style.dp(4))
                setOnClickListener {
                    if (this@SettingsBinaryChoice.isEnabled && observed != null && observed != (index == 1))
                        request(index == 1)
                }
            }
            addView(choice, LayoutParams(-2, -2))
        }
        render()
    }
    fun present(value: Boolean?) {
        if (observed == value) return
        observed = value
        render()
    }
    override fun setEnabled(enabled: Boolean) {
        if (enabled == isEnabled) return
        super.setEnabled(enabled)
        render()
    }
    private fun render() {
        for (index in 0 until childCount) {
            (getChildAt(index) as Button).apply {
                val active = observed == (index == 1)
                isSelected = active
                isEnabled = this@SettingsBinaryChoice.isEnabled && observed != null
                setTextColor(if (active) style.accent else if (isEnabled) style.ink else style.muted)
                background = RippleDrawable(ColorStateList.valueOf(style.selected),
                    style.shape(if (active) style.selected else Color.TRANSPARENT, radius = 8),
                    style.shape(Color.WHITE, radius = 8))
            }
        }
    }
}
