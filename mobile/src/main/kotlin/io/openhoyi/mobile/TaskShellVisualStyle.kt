package io.openhoyi.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button

/** Local navigation/device chrome. Page typography and shared icon defaults stay independent. */
internal class TaskShellVisualStyle(private val context: Context) {
    val surface = context.getColor(R.color.task_shell_surface)
    val ink = context.getColor(R.color.task_shell_ink)
    val accent = context.getColor(R.color.mobile_accent)
    val selected = context.getColor(R.color.mobile_accent_soft)
    val card = context.getColor(R.color.task_shell_card)
    val muted = context.getColor(R.color.task_shell_muted)
    private val border = context.getColor(R.color.task_shell_border)
    private val disabled = context.getColor(R.color.task_shell_disabled)
    private val disabledInk = context.getColor(R.color.task_shell_disabled_ink)
    val solidIcons = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val selectedInk = if (solidIcons) ink else accent
    val iconStrokeDp = if (solidIcons) 2.4f else 2f

    private fun shape(fill: Int, outlined: Boolean = false) = GradientDrawable().apply {
        setColor(fill); cornerRadius = 10 * context.resources.displayMetrics.density
        if (outlined) setStroke(context.resources.displayMetrics.density.toInt().coerceAtLeast(1), border)
    }
    fun navigationBackground(active: Boolean): RippleDrawable {
        fun content(focused: Boolean): android.graphics.drawable.Drawable {
            val fill = shape(if (active) selected else Color.TRANSPARENT).apply {
                if (focused) setStroke((2 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1), ink)
            }
            return if (active && solidIcons) LayerDrawable(arrayOf(fill, shape(accent))).apply {
                setLayerWidth(1, (3 * context.resources.displayMetrics.density).toInt())
                setLayerHeight(1, -1)
                setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
            } else fill
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), content(true))
            addState(intArrayOf(), content(false))
        }
        return RippleDrawable(ColorStateList.valueOf(selected), states, shape(Color.WHITE))
    }
    fun cardBackground() = shape(card)
    fun action(button: Button) {
        button.typeface = TaskTypography.face(context, 500)
        button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(disabledInk, ink)))
        button.background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(disabled, true))
            addState(intArrayOf(android.R.attr.state_focused), RippleDrawable(ColorStateList.valueOf(selected),
                shape(selected, true), shape(Color.WHITE)))
            addState(intArrayOf(), RippleDrawable(ColorStateList.valueOf(selected),
                shape(surface, true), shape(Color.WHITE)))
        }
    }
}
