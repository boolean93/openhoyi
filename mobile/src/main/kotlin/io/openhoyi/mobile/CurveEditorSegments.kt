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
    private val thumb = View(context).apply { background = style.shape(style.primary, false, 8) }
    private val row = LinearLayout(context).apply { isBaselineAligned = false }
    private val choices = List(2) { index -> style.text(if (index == 0) first else second, TaskProductVisualStyle.action).apply {
        gravity = Gravity.CENTER; minimumHeight = style.dp(48)
        setPadding(style.dp(8), style.dp(4), style.dp(8), style.dp(4))
        isClickable = true; isFocusable = true
        setOnClickListener { if (selected != (index == 1)) { select(index == 1, true); onSelected(index == 1) } }
    } }
    private var selected = false
    var onSelected: (Boolean) -> Unit = {}
    init {
        background = style.shape(style.disabled, true, 10)
        addView(thumb, LayoutParams(0, 0))
        choices.forEach { row.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        addView(row, LayoutParams(-1, -2))
        select(false)
    }
    val checked get() = selected
    val text get() = choices[if (selected) 1 else 0].text
    fun selectFromUser(value: Boolean) { choices[if (value) 1 else 0].performClick() }
    fun labels(first: String, second: String) { choices[0].text = first; choices[1].text = second }
    fun select(value: Boolean, animate: Boolean = false) {
        if (selected == value && !animate && thumb.isLaidOut) return
        selected = value
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
        val x = if (value) width / 2f else 0f
        thumb.animate().cancel()
        if (animate && isLaidOut) thumb.animate().translationX(x).setDuration(170).setInterpolator(DecelerateInterpolator(2f)).start()
        else thumb.translationX = x
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        thumb.layout(style.dp(2), style.dp(2), width / 2 - style.dp(2), height - style.dp(2))
        if (changed) { thumb.animate().cancel(); thumb.translationX = if (selected) width / 2f else 0f }
    }
}
