package io.openhoyi.mobile

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.AdapterView

/** Fixed page regions: only collections and dialog text own a scroll range. */
internal data class BeanPageRegions(val actions: LinearLayout)

internal class BeanActionRows(private val activity: ThemedActivity) : LinearLayout(activity) {
    private var row: LinearLayout? = null
    init { orientation = VERTICAL }
    private fun attachRow(view: LinearLayout) { super.addView(view, -1, LayoutParams(-1, -2)) }
    override fun removeAllViews() { row = null; super.removeAllViews() }
    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        if (child !is Button) { super.addView(child, index, params); return }
        val current = row?.takeIf { it.childCount < 2 } ?: LinearLayout(context).apply {
            orientation = HORIZONTAL
            isBaselineAligned = false
            this@BeanActionRows.attachRow(this)
            row = this
        }
        current.addView(child, LayoutParams(0, -2, 1f).apply {
            topMargin = HoyiUi.dp(activity, 6)
            if (current.childCount > 0) marginStart = HoyiUi.dp(activity, 8)
        })
    }
}

internal fun ThemedActivity.beanActions(body: LinearLayout): LinearLayout =
    (body.tag as? BeanPageRegions)?.actions ?: body

internal fun ThemedActivity.beanWorkspace(body: LinearLayout): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    body.addView(this, LinearLayout.LayoutParams(-1, 0, 1f))
}

internal fun ThemedActivity.beanList(body: LinearLayout): LinearLayout {
    val scroll = ScrollView(this).apply { isFillViewport = true }
    body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; scroll.addView(this) }
}

/** Short sections follow their content height; only explicit multiline editors fill the workspace.
 * Each section stays alive while another section is selected, preserving unsaved text. */
internal class BeanSections(private val activity: ThemedActivity, body: LinearLayout,
    labels: List<String>, selected: Int = 0, elasticPanels: Set<Int> = emptySet(), changed: (Int) -> Unit = {}) {
    val panels: List<LinearLayout>
    val selector: Spinner
    private val frame: FrameLayout
    init {
        val host = activity.beanWorkspace(body)
        selector = Spinner(activity).apply {
            tag = "bean-sections"
            adapter = HoyiUi.spinnerAdapter(activity, labels)
            contentDescription = labels.joinToString(" / ")
            minimumHeight = HoyiUi.dp(activity, 48)
            host.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        frame = FrameLayout(activity)
        host.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        panels = labels.mapIndexed { index, _ ->
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(HoyiUi.dp(activity, 12), 0, HoyiUi.dp(activity, 12), HoyiUi.dp(activity, 8))
                background = HoyiUi.shape(activity, R.color.mobile_surface, 12, R.color.mobile_border)
                tag = if (index in elasticPanels) "bean-elastic-section" else "bean-short-section"
                frame.addView(this, FrameLayout.LayoutParams(-1,
                    if (index in elasticPanels) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT))
                visibility = View.GONE
            }
        }
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                panels.forEachIndexed { index, panel -> panel.visibility = if (index == position) View.VISIBLE else View.GONE }
                changed(position)
            }
        }
        val initial = selected.coerceIn(0, panels.lastIndex)
        panels[initial].visibility = View.VISIBLE
        selector.setSelection(initial)
    }
}

/** Paired native field cells on tablets; compact screens keep a single readable column. */
internal fun ThemedActivity.beanFieldCell(parent: LinearLayout): LinearLayout {
    if (!HoyiUi.wide(this) || parent.tag == "bean-full-field") return parent
    val row = (if (parent.childCount > 0) parent.getChildAt(parent.childCount - 1) as? LinearLayout else null)
        ?.takeIf { it.tag == "bean-field-row" && it.childCount < 2 }
        ?: LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = "bean-field-row"
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        row.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply {
            if (row.childCount > 0) marginStart = HoyiUi.dp(this@beanFieldCell, 12)
        })
    }
}

internal fun ThemedActivity.beanDetails(title: String, value: String) {
    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
    HoyiUi.label(this, content, value, 16).apply { setTextIsSelectable(true) }
    beanShowDialog(AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(content) })
        .setPositiveButton(android.R.string.ok, null).create())
}

/** Focused fields keep usable space above the IME; low-height tablets also focus multiline editors.
 * Every keystroke also updates the durable page draft used by onSaveInstanceState. */
internal fun ThemedActivity.beanCompactEditor(field: android.widget.EditText, label: String) {
    var ancestor = field.parent
    while (ancestor is View) {
        if (ancestor.tag == "task-inline-form") return
        ancestor = ancestor.parent
    }
    if (HoyiUi.wide(this) && resources.configuration.screenHeightDp > 720) return
    field.showSoftInputOnFocus = false
    // Callers finish configuring multiline input after creating this field.
    // Modal fields must activate on their first tap, not spend that tap taking
    // touch focus. Keyboard/accessibility activation remains available.
    field.post {
        val inline = field.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0 && !HoyiUi.wide(this)
        field.isFocusableInTouchMode = inline
        field.isFocusable = true
        field.showSoftInputOnFocus = inline
    }
    var dialog: AlertDialog? = null
    fun edit() {
        val multiline = field.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (multiline && !HoyiUi.wide(this)) {
            field.showSoftInputOnFocus = true
            (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            return
        }
        if (dialog != null || isFinishing || isDestroyed) return
        val initialSelectionStart = field.selectionStart.coerceIn(0, field.text.length)
        val initialSelectionEnd = field.selectionEnd.coerceIn(0, field.text.length)
        val editor = object : android.widget.EditText(this) {
            // EditText can call this override while its superclass is constructing.
            var trackSelection = false
            override fun onSelectionChanged(start: Int, end: Int) {
                super.onSelectionChanged(start, end)
                if (trackSelection && start >= 0 && end >= 0) field.setSelection(
                    start.coerceAtMost(field.text.length), end.coerceAtMost(field.text.length))
            }
        }.apply {
            inputType = field.inputType
            filters = field.filters
            textSize = 18f
            setText(field.text.toString())
            setSelectAllOnFocus(!multiline)
            minimumHeight = HoyiUi.dp(this@beanCompactEditor, if (multiline) 128 else 52)
            if (multiline) {
                setSingleLine(false)
                minLines = 4
                maxLines = 6
                gravity = android.view.Gravity.TOP
                isVerticalScrollBarEnabled = true
                setSelection(initialSelectionStart, initialSelectionEnd)
            }
            setTextColor(getColor(R.color.mobile_text))
            setPadding(HoyiUi.dp(this@beanCompactEditor, 12), HoyiUi.dp(this@beanCompactEditor, 8), HoyiUi.dp(this@beanCompactEditor, 12), HoyiUi.dp(this@beanCompactEditor, 8))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { field.setText(s) }
                override fun afterTextChanged(s: android.text.Editable?) {
                    field.setSelection(selectionStart.coerceIn(0, field.text.length), selectionEnd.coerceIn(0, field.text.length))
                }
            })
            trackSelection = true
        }
        dialog = AlertDialog.Builder(this).setTitle(label).setView(editor)
            .setPositiveButton(android.R.string.ok, null).create().also { focused ->
                focused.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

                focused.setOnShowListener {
                    editor.requestFocus()
                    focused.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
                beanShowDialog(focused) {
                    val start = editor.selectionStart.coerceIn(0, field.text.length)
                    val end = editor.selectionEnd.coerceIn(0, field.text.length)
                    editor.trackSelection = false
                    // Move focus to the actual page root, not an editable descendant.
                    // Closing an editor must not reactivate it through focus recovery.
                    findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)?.requestFocus()
                    field.clearFocus()
                    field.setSelection(start, end)
                    (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                        .hideSoftInputFromWindow(window.decorView.windowToken, 0)
                    dialog = null
                }
            }
    }
    field.setOnClickListener { edit() }
    // Only explicit activation opens a modal editor; transient layout/window focus does not.
}

/** A helper-created dialog cannot outlive its Activity or retain the old draft after rotation. */
internal fun ThemedActivity.beanShowDialog(dialog: AlertDialog, dismissed: () -> Unit = {}) {
    val lifecycle = object : android.app.Application.ActivityLifecycleCallbacks {
        override fun onActivityDestroyed(activity: android.app.Activity) { if (activity === this@beanShowDialog) dialog.dismiss() }
        override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
        override fun onActivityStarted(activity: android.app.Activity) = Unit
        override fun onActivityResumed(activity: android.app.Activity) = Unit
        override fun onActivityPaused(activity: android.app.Activity) = Unit
        override fun onActivityStopped(activity: android.app.Activity) = Unit
        override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
    }
    application.registerActivityLifecycleCallbacks(lifecycle)
    dialog.setOnDismissListener { application.unregisterActivityLifecycleCallbacks(lifecycle); dismissed() }
    dialog.show()
}

/** Selected names are bounded; opening the native dropdown exposes each complete label. */
internal fun ThemedActivity.beanChoiceAdapter(labels: List<String>, selectedLabels: List<String> = labels,
    selectedMaxLines: Int = 2): android.widget.ArrayAdapter<String> =
    object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
        init { require(selectedLabels.size == labels.size); require(selectedMaxLines > 0) }
        private fun readable(view: View, selected: Boolean): View = view.apply {
            if (this is android.widget.TextView) {
                setSingleLine(false)
                maxLines = if (selected) selectedMaxLines else Int.MAX_VALUE
                ellipsize = if (selected && selectedMaxLines != Int.MAX_VALUE) android.text.TextUtils.TruncateAt.END else null
                minHeight = HoyiUi.dp(this@beanChoiceAdapter, 48)
                setTextColor(getColor(R.color.mobile_text))
                setBackgroundColor(getColor(R.color.mobile_surface))
                contentDescription = text
                if (selected) tag = "full-text-details"
                layoutParams = layoutParams?.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            }
        }
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = readable(super.getView(position, convertView, parent), true).apply {
            if (this is android.widget.TextView) {
                text = selectedLabels[position]
                contentDescription = labels[position]
            }
        }
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = readable(super.getDropDownView(position, convertView, parent), false)
    }

internal fun ThemedActivity.beanCompactSummary(parent: LinearLayout, value: String): android.widget.TextView {
    val summary = HoyiUi.label(this, parent, value, 18, true).apply {
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        contentDescription = value
        tag = "full-text-details"
    }
    HoyiUi.button(this, parent, getString(R.string.ui_full_details)) { beanDetails(getString(R.string.ui_full_details), value) }
    return summary
}
