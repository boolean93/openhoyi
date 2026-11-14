package io.openhoyi.mobile

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Both choices remain readable inside one track; changing state never triggers another callback. */
internal class CurveEditorSegments(context: Context, private val style: TaskProductVisualStyle,
    first: String, second: String) : FrameLayout(context) {
    private var selectedColors = listOf(style.primary, style.primary)
    private val thumb = View(context).apply { background = style.shape(style.primary, false, 7) }
    private val row = LinearLayout(context).apply { isBaselineAligned = false }
    private val choices = List(2) { index -> style.text(if (index == 0) first else second, TaskProductVisualStyle.action).apply {
        gravity = Gravity.CENTER; minimumHeight = style.dp(48); minimumWidth = style.dp(48)
        setPadding(style.dp(8), style.dp(4), style.dp(8), style.dp(4))
        isClickable = true; isFocusable = true
        setOnClickListener { if (selected != (index == 1)) { select(index == 1, true); onSelected(index == 1) } }
    } }
    private var selected = false
    private var laidOutDirection = -1
    var onSelected: (Boolean) -> Unit = {}
    init {
        minimumWidth = style.dp(96)
        background = android.graphics.drawable.InsetDrawable(style.shape(style.disabled, true, 8), 0, style.dp(7), 0, style.dp(7))
        // InsetDrawable padding is decorative; it must not add another 14dp to the 48dp touch row.
        setPadding(0, 0, 0, 0)
        addView(thumb, LayoutParams(0, 0))
        choices.forEach { row.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        addView(row, LayoutParams(-1, -2))
        select(false)
    }
    val checked get() = selected
    val text get() = choices[if (selected) 1 else 0].text
    fun selectFromUser(value: Boolean) { choices[if (value) 1 else 0].performClick() }
    fun colors(first: Int, second: Int) {
        selectedColors = listOf(first, second)
        thumb.background = style.shape(selectedColors[if (selected) 1 else 0], false, 7)
    }
    /** Compact visual type, not compact touch targets. */
    fun compact() {
        choices.forEach { label ->
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            if (android.os.Build.VERSION.SDK_INT >= 28) label.setLineHeight(AppTextUnits.pixels(resources, 17f).toInt())
            label.setSingleLine(true)
            label.setPadding(style.dp(5), style.dp(2), style.dp(5), style.dp(2))
        }
    }
    fun requiredWidth(): Int = choices.maxOf { label ->
        maxOf(style.dp(48), kotlin.math.ceil(android.text.Layout.getDesiredWidth(label.text, label.paint).toDouble()).toInt() + label.compoundPaddingLeft + label.compoundPaddingRight)
    } * 2 + paddingLeft + paddingRight
    fun labels(first: String, second: String) { choices[0].text = first; choices[1].text = second }
    fun select(value: Boolean, animate: Boolean = false) {
        if (selected == value && !animate && thumb.isLaidOut) return
        selected = value
        thumb.background = style.shape(selectedColors[if (value) 1 else 0], false, 7)
        choices.forEachIndexed { index, label ->
            label.isSelected = value == (index == 1)
            label.setTextColor(if (label.isSelected) Color.WHITE else style.ink)
            label.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.RadioButton"; info.isCheckable = true; info.isChecked = host.isSelected
                }
            }
        }
        val x = if (value xor (layoutDirection == LAYOUT_DIRECTION_RTL)) width / 2f else 0f
        thumb.animate().cancel()
        if (animate && isLaidOut) thumb.animate().translationX(x).setDuration(170).setInterpolator(DecelerateInterpolator(2f)).start()
        else thumb.translationX = x
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        thumb.layout(style.dp(2), style.dp(8), width / 2 - style.dp(2), height - style.dp(8))
        if (changed || laidOutDirection != layoutDirection) {
            thumb.animate().cancel()
            thumb.translationX = if (selected xor (layoutDirection == LAYOUT_DIRECTION_RTL)) width / 2f else 0f
        }
        laidOutDirection = layoutDirection
    }
}
