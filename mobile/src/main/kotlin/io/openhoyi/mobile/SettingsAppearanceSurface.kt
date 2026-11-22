package io.openhoyi.mobile

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Give only roomy Appearance panels the reference's horizontal label-and-control surface. */
internal class SettingsAppearanceSurface(context: Context, private val style: TaskProductVisualStyle,
    title: CharSequence, private val content: View, private val minWideDp: Int = 620,
    private val narrowLabel: Boolean = true, private val maxContentDp: Int? = null, private val onWidthMode: (Boolean) -> Unit = {}) : LinearLayout(context) {
    private var roomy: Boolean? = null
    private val label: TextView = style.text(title, TaskProductVisualStyle.action)
    init {
        addView(label)
        addView(content)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wide = MeasureSpec.getSize(widthMeasureSpec) >= style.dp(minWideDp)
        if (roomy != wide) {
            roomy = wide
            orientation = if (wide) HORIZONTAL else VERTICAL
            gravity = if (wide) Gravity.CENTER_VERTICAL else Gravity.TOP
            val pad = style.dp(if (wide) 14 else 0)
            setPadding(pad, pad, pad, pad)
            background = if (wide) settingsRaisedGroup(context, style) else null
            label.visibility = if (wide || narrowLabel) View.VISIBLE else View.GONE
            label.layoutParams = if (wide) LayoutParams(style.dp(110), -2) else LayoutParams(-1, -2)
            label.setPaddingRelative(0, 0, if (wide) style.dp(8) else 0, if (wide) 0 else style.dp(6))
            content.layoutParams = if (wide) maxContentDp?.let { LayoutParams(style.dp(it), -2) }
                ?: LayoutParams(0, -2, 1f) else LayoutParams(-1, -2)
            onWidthMode(wide)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** Existing language and text-size controls can share the same material without changing their dialogs. */
internal fun settingsAppearanceSurface(style: TaskProductVisualStyle): LinearLayout =
    style.column().apply {
        val pad = style.dp(10)
        setPadding(pad, pad, pad, pad)
        background = style.shape(style.surface, outline = true, radius = 14)
        markSettingsGroupMaterial(style)
    }
