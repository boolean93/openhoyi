package io.openhoyi.mobile

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner

/** Stable inline parameter/notes regions; resize never reparents a field or changes a draft. */
internal class TaskReviewSections(private val activity: ThemedActivity, body: LinearLayout,
    labels: List<String>, selected: Int, changed: (Int) -> Unit) {
    private var selectedIndex = selected.coerceIn(labels.indices)
    private val parameters = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val notes = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var multilineFields = emptyList<EditText>()
    private val parameterScroll = object : ScrollView(activity) {
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            post { fitEditors() }
        }
    }.apply { addView(parameters) }
    private val noteScroll = object : ScrollView(activity) {
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            post { fitEditors() }
        }
    }.apply { addView(notes) }
    private val regions = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val density = resources.displayMetrics.density
            // These are independently scrolling editor viewports, not a chart/tool workspace.
            // The IME may reduce height without removing the width needed for two usable panes.
            val split = TaskLayoutPolicy.decide(resources.configuration.screenWidthDp.coerceAtLeast(1),
                (height / density).toInt(), twoPaneMinimumHeightDp = 0).twoPaneAllowed && width >= dp(624)
            val nextOrientation = if (split) HORIZONTAL else VERTICAL
            val parameterWidth = if (split) dp(280) else width
            // A short native input is at least 52dp, before panel padding. Do not allocate
            // a 48dp viewport merely because that is the minimum touch target elsewhere.
            var parameterMinimum = dp(48)
            panels.take(2).forEach { panel ->
                for (index in 0 until panel.childCount) {
                    val field = panel.getChildAt(index)
                    if (field is EditText && field.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0) {
                        field.measure(MeasureSpec.makeMeasureSpec((parameterWidth - panel.paddingLeft - panel.paddingRight).coerceAtLeast(0), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                        parameterMinimum = maxOf(parameterMinimum, field.measuredHeight + panel.paddingTop + panel.paddingBottom)
                    }
                }
            }
            val parameterHeight = if (split) -1 else minOf(dp(180), height / 3).coerceAtLeast(minOf(parameterMinimum, height))
            if (orientation != nextOrientation || parameterScroll.layoutParams.width != parameterWidth ||
                parameterScroll.layoutParams.height != parameterHeight) {
                orientation = nextOrientation
                parameterScroll.layoutParams = LayoutParams(parameterWidth, parameterHeight)
                noteScroll.layoutParams = if (split) LayoutParams(0, -1, 1f).apply { marginStart = dp(24) }
                    else LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        orientation = LinearLayout.HORIZONTAL
        addView(parameterScroll)
        addView(noteScroll)
    }
    private val host = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        tag = "task-inline-form"
        body.addView(this, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    private val parameterHeading = sectionHeading(parameters, R.string.journal_parameters)
    private val notesHeader = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        isBaselineAligned = false
        notes.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private val notesHeading = sectionHeading(notesHeader, R.string.journal_notes).apply {
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }
    val panels = labels.indices.map { index ->
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            tag = "bean-full-field"
            setPadding(dp(8), dp(4), dp(8), dp(8))
            (if (index < 2) parameters else notes).addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }
    val selector = Spinner(activity).apply {
        tag = "bean-sections"
        contentDescription = activity.getString(R.string.task_form_jump)
        adapter = HoyiUi.spinnerAdapter(activity, labels)
        minimumHeight = dp(48)
        notesHeader.addView(this, LinearLayout.LayoutParams(dp(200), -2))
        setSelection(selectedIndex)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val different = selectedIndex != position
                selectedIndex = position
                changed(position)
                if (different) reveal(position)
            }
        }
    }
    init { host.addView(regions, LinearLayout.LayoutParams(-1, 0, 1f)) }

    private fun sectionHeading(parent: LinearLayout, label: Int) =
        HoyiUi.label(activity, parent, activity.getString(label), 20, true).apply {
            setPadding(dp(8), dp(16), dp(8), dp(16))
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        }

    private fun dp(value: Int) = HoyiUi.dp(activity, value)
    private fun reveal(index: Int) {
        val scroll = if (index < 2) parameterScroll else noteScroll
        panels[index].post { scroll.scrollTo(0, panels[index].top) }
    }
    fun jumpTo(index: Int) { selector.setSelection(index); reveal(index) }
    fun fitMultiline(fields: List<EditText>) { multilineFields = fields; noteScroll.post { fitEditors() } }
    private fun fitEditors() {
        // Parameter and notes viewports resize independently under the IME.
        // A collapsed notes viewport must not leave the grind editor at its old 160dp.
        multilineFields.filter { it.parent === panels[1] }.forEach { field ->
            val panel = panels[1]
            val budget = parameterScroll.height - panel.paddingTop - panel.paddingBottom
            if (budget > 0) {
                val height = minOf(dp(52), budget).coerceAtLeast(dp(48))
                if (field.layoutParams.height != height) field.layoutParams =
                    (field.layoutParams as LinearLayout.LayoutParams).apply { this.height = height; weight = 0f }
            }
        }
        if (noteScroll.width <= 0 || noteScroll.height <= 0) return
        notesHeader.measure(View.MeasureSpec.makeMeasureSpec(noteScroll.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        var noteChrome = notesHeader.measuredHeight
        panels.drop(2).forEach { panel ->
            noteChrome += panel.paddingTop + panel.paddingBottom
            for (index in 0 until panel.childCount) {
                val child = panel.getChildAt(index)
                if (child !is EditText) {
                    child.measure(View.MeasureSpec.makeMeasureSpec((noteScroll.width - panel.paddingLeft - panel.paddingRight).coerceAtLeast(0), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    noteChrome += child.measuredHeight
                }
            }
        }
        val noteHeight = ((noteScroll.height - noteChrome) / 3).coerceIn(dp(48), dp(160))
        multilineFields.filter { it.parent !== panels[1] }.forEach { field ->
            val height = noteHeight
            if (field.layoutParams.height != height) field.layoutParams = (field.layoutParams as LinearLayout.LayoutParams).apply { this.height = height; weight = 0f }
        }
    }
    fun restore(state: Bundle?, fields: Map<String, EditText>) {
        if (state == null) return
        host.post {
            fields[state.getString("review-focus")]?.let { field ->
                field.requestFocus()
                field.setSelection(state.getInt("review-start").coerceIn(0, field.length()), state.getInt("review-end").coerceIn(0, field.length()))
            }
            parameterScroll.scrollTo(0, state.getInt("review-parameters-scroll"))
            noteScroll.scrollTo(0, state.getInt("review-notes-scroll"))
        }
    }
    fun save(state: Bundle, fields: Map<String, EditText>) {
        state.putInt("review-parameters-scroll", parameterScroll.scrollY)
        state.putInt("review-notes-scroll", noteScroll.scrollY)
        fields.entries.firstOrNull { it.value.hasFocus() }?.let { (key, field) ->
            state.putString("review-focus", key)
            state.putInt("review-start", field.selectionStart)
            state.putInt("review-end", field.selectionEnd)
        }
    }
}
