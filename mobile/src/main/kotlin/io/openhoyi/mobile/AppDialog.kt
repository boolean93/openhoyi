package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView

/** Shared shell for app-owned dialogs. Android permission and document panels stay system-owned. */
internal object AppDialog {
    internal data class Action(val label: CharSequence, val danger: Boolean = false, val onClick: () -> Unit)

    fun confirm(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        action: Action,
        cancelLabel: CharSequence = context.getString(android.R.string.cancel),
    ): AlertDialog = show(context, title, TextView(context).apply { text = message }, action,
        Action(cancelLabel, onClick = {}))

    fun choices(context: Context, title: CharSequence, labels: List<CharSequence>, onSelect: (Int) -> Unit): AlertDialog =
        show(AlertDialog.Builder(context).setTitle(title).setItems(labels.toTypedArray()) { _, index -> onSelect(index) }
            .setNegativeButton(android.R.string.cancel, null).create())

    /** The body may be taller than the window; actions remain fixed below its scroll area. */
    fun show(
        context: Context,
        title: CharSequence,
        body: View,
        positive: Action,
        negative: Action? = null,
    ): AlertDialog {
        val style = TaskProductVisualStyle(context)
        val shell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(style.surface)
                cornerRadius = style.dp(16).toFloat()
            }
        }
        val heading = style.text(title, TaskProductVisualStyle.section).apply {
            tag = "app-dialog-title"
            setSingleLine(false); maxLines = Int.MAX_VALUE; ellipsize = null
            setPadding(style.dp(24), style.dp(20), style.dp(24), style.dp(12))
        }
        shell.addView(heading, LinearLayout.LayoutParams(-1, -2))
        if (body.parent is ViewGroup) (body.parent as ViewGroup).removeView(body)
        if (body is TextView) style.apply(body, TaskProductVisualStyle.body)
        val content = ScrollView(context).apply {
            tag = "app-dialog-body"
            isFillViewport = false
            addView(body, ViewGroup.LayoutParams(-1, -2))
        }
        shell.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            marginStart = style.dp(24)
            marginEnd = style.dp(24)
        })
        val actions = LinearLayout(context).apply {
            tag = "app-dialog-actions"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(16))
        }
        shell.addView(actions, LinearLayout.LayoutParams(-1, -2))
        val dialog = AlertDialog.Builder(context).setView(shell).create()
        fun addAction(action: Action, primary: Boolean) {
            val button = Button(context).apply {
                text = action.label
                tag = if (primary) "app-dialog-positive" else "app-dialog-negative"
                style.button(this, primary = primary && !action.danger, outline = !primary || action.danger)
                if (action.danger) setTextColor(ColorStateList.valueOf(context.getColor(R.color.mobile_danger)))
                setOnClickListener { dialog.dismiss(); action.onClick() }
            }
            actions.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (!primary) marginEnd = style.dp(8)
            })
        }
        negative?.let { addAction(it, false) }
        addAction(positive, true)
        dialog.setOnShowListener { size(dialog, context, style) }
        dialog.show()
        return dialog
    }

    /** Style an existing Builder result while preserving its original callbacks and choice semantics. */
    fun show(dialog: AlertDialog): AlertDialog {
        dialog.show()
        return styleShown(dialog)
    }

    /** Apply the shared shell after a caller's existing onShow and evidence hooks have run. */
    fun styleShown(dialog: AlertDialog): AlertDialog {
        val context = dialog.context
        val style = TaskProductVisualStyle(context)
        size(dialog, context, style)
        val titleId = context.resources.getIdentifier("alertTitle", "id", "android")
        if (titleId != 0) dialog.findViewById<TextView>(titleId)?.let {
            style.apply(it, TaskProductVisualStyle.section)
            it.setSingleLine(false); it.maxLines = Int.MAX_VALUE; it.ellipsize = null
        }
        dialog.findViewById<TextView>(android.R.id.message)?.let {
            style.apply(it, TaskProductVisualStyle.body)
            it.setPadding(style.dp(24), style.dp(8), style.dp(24), style.dp(16))
        }
        dialog.listView?.let { choices -> styleChoices(choices, style) }
        if (dialog !is android.app.DatePickerDialog) dialog.findViewById<ViewGroup>(android.R.id.custom)?.let { custom ->
            val body = (0 until custom.childCount).map { custom.getChildAt(it) }
                .firstOrNull { it.visibility != View.GONE }
            if (body != null && body !is ScrollView && !containsEditor(body)) {
                custom.removeView(body)
                custom.addView(ScrollView(context).apply {
                    tag = "app-dialog-body"
                    addView(body, ViewGroup.LayoutParams(-1, -2))
                }, ViewGroup.LayoutParams(-1, -2))
            }
            styleBody(custom, style)
        }
        listOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE).forEach { which ->
            dialog.getButton(which)?.let { button ->
                style.button(button, primary = which == AlertDialog.BUTTON_POSITIVE)
            }
        }
        return dialog
    }

    private fun containsEditor(view: View): Boolean = view is android.widget.EditText ||
        view is ViewGroup && (0 until view.childCount).any { containsEditor(view.getChildAt(it)) }

    private fun styleBody(view: View, style: TaskProductVisualStyle) {
        if (view is TextView && view !is Button && view !is android.widget.EditText) {
            style.apply(view, TaskProductVisualStyle.body)
            view.setSingleLine(false); view.maxLines = Int.MAX_VALUE; view.ellipsize = null
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) styleBody(view.getChildAt(index), style)
    }

    private fun styleChoices(choices: ListView, style: TaskProductVisualStyle) {
        choices.setBackgroundColor(style.surface)
        val styled = java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())
        fun visibleRows() {
            for (index in 0 until choices.childCount) {
                val row = choices.getChildAt(index)
                if (!styled.add(row)) continue
                row.minimumHeight = style.dp(48)
                if (row is TextView) {
                    style.apply(row, TaskProductVisualStyle.body)
                    row.setSingleLine(false); row.maxLines = Int.MAX_VALUE; row.ellipsize = null
                    row.setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(8))
                } else styleBody(row, style)
            }
        }
        visibleRows()
        choices.viewTreeObserver.addOnGlobalLayoutListener { visibleRows() }
    }

    private fun size(dialog: AlertDialog, context: Context, style: TaskProductVisualStyle) {
        val metrics = context.resources.displayMetrics
        val width = minOf(style.dp(560), metrics.widthPixels - style.dp(32)).coerceAtLeast(1)
        dialog.window?.apply {
            setBackgroundDrawable(style.shape(style.surface, radius = 16))
            decorView.clipToOutline = true
            setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            var cappedHeight = 0
            decorView.viewTreeObserver.addOnGlobalLayoutListener {
                val visible = Rect()
                decorView.getWindowVisibleDisplayFrame(visible)
                val availableHeight = minOf(metrics.heightPixels, visible.height()) - style.dp(32)
                val cap = availableHeight.coerceAtLeast(style.dp(120))
                if (decorView.height > cap && cappedHeight != cap) {
                    cappedHeight = cap
                    setLayout(width, cap)
                }
            }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
}
