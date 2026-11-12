package io.openhoyi.mobile

import android.app.Activity
import android.graphics.Rect
import android.view.Gravity
import android.view.View.MeasureSpec
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity

/** Bounds inside the visible app area, also used by the portrait/landscape JVM checks. */
internal data class ReleaseUpdatePromptBounds(val width: Int, val maxHeight: Int, val right: Int, val top: Int)
internal fun releaseUpdatePromptBounds(visibleWidth: Int, visibleHeight: Int, density: Float): ReleaseUpdatePromptBounds {
    val margin = (16 * density).toInt().coerceAtLeast(1)
    return ReleaseUpdatePromptBounds(minOf((360 * density).toInt(), visibleWidth - 2 * margin).coerceAtLeast(1),
        (visibleHeight - 2 * margin).coerceAtLeast(1), margin, margin)
}

/** A nonmodal, app-owned upper-right card: no backdrop, artwork or automatic navigation. */
internal class ReleaseUpdatePrompt(
    private val activity: Activity,
    update: ReleaseUpdate,
    private val later: () -> Unit,
    private val open: () -> Unit,
) {
    private val style = TaskProductVisualStyle(activity)
    private val visible = Rect().apply { activity.window.decorView.getWindowVisibleDisplayFrame(this) }
    private val bounds = releaseUpdatePromptBounds(visible.width(), visible.height(), activity.resources.displayMetrics.density)
    private var programmatic = false
    private val shell = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(bounds.maxHeight, MeasureSpec.AT_MOST))
        }
    }.apply {
        orientation = LinearLayout.VERTICAL
        tag = "product/update/prompt"
        background = style.shape(style.surface, outline = true)
        setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(12))
    }
    private val popup = PopupWindow(shell, bounds.width, -2, false).apply {
        isOutsideTouchable = true
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        setBackgroundDrawable(style.shape(style.surface, outline = true))
        elevation = style.dp(8).toFloat()
        animationStyle = 0
    }
    private val back = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { popup.dismiss() }
    }
    init {
        shell.addView(style.text(activity.getString(R.string.update_prompt_title), TaskProductVisualStyle.action,
            "product/update/prompt/title").apply { androidx.core.view.ViewCompat.setAccessibilityHeading(this, true) }, LinearLayout.LayoutParams(-1, -2))
        val body = style.text(activity.getString(R.string.update_prompt_body, update.versionName, update.versionCode),
            TaskProductVisualStyle.auxiliary, "product/update/prompt/body", secondary = true)
        shell.addView(ScrollView(activity).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = style.dp(8); bottomMargin = style.dp(8)
        })
        val actions = style.row("product/update/prompt/actions")
        fun action(label: Int, primary: Boolean, click: () -> Unit) {
            actions.addView(style.button(Button(activity), primary = primary).apply {
                text = activity.getString(label)
                tag = if (primary) "product/update/prompt/open" else "product/update/prompt/later"
                minHeight = style.dp(48)
                setOnClickListener { dismiss(); click() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (!primary) marginEnd = style.dp(8) })
        }
        action(R.string.update_later, false, later)
        action(R.string.update_download, true, open)
        shell.addView(actions, LinearLayout.LayoutParams(-1, -2))
        popup.setOnDismissListener { back.remove(); if (!programmatic) later() }
    }
    fun show() {
        (activity as? FragmentActivity)?.onBackPressedDispatcher?.addCallback(back)
        try {
            // RIGHT is intentional: this placement remains upper-right in either UI language.
            popup.showAtLocation(activity.window.decorView, Gravity.TOP or Gravity.RIGHT,
                bounds.right + (activity.resources.displayMetrics.widthPixels - visible.right).coerceAtLeast(0),
                visible.top + bounds.top)
        } catch (error: RuntimeException) { back.remove(); throw error }
    }
    /** Lifecycle/busy retractions never mean the user chose Later. */
    fun dismiss() { programmatic = true; popup.dismiss(); back.remove() }
}
