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
    private val parameterScroll = ScrollView(activity).apply { addView(parameters) }
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
            val split = TaskLayoutPolicy.decide(resources.configuration.screenWidthDp.coerceAtLeast(1),
                (height / density).toInt()).twoPaneAllowed && width >= dp(624)
            val nextOrientation = if (split) HORIZONTAL else VERTICAL
            val parameterWidth = if (split) dp(280) else width
            val parameterHeight = if (split) -1 else minOf(dp(180), height / 3).coerceAtLeast(minOf(dp(48), height))
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
        host.addView(this, LinearLayout.LayoutParams(-1, -2))
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

    private fun dp(value: Int) = HoyiUi.dp(activity, value)
    private fun reveal(index: Int) {
        val scroll = if (index < 2) parameterScroll else noteScroll
        panels[index].post { scroll.scrollTo(0, panels[index].top) }
    }
    fun jumpTo(index: Int) { selector.setSelection(index); reveal(index) }
    fun fitMultiline(fields: List<EditText>) { multilineFields = fields; noteScroll.post { fitEditors() } }
    private fun fitEditors() {
        if (noteScroll.width <= 0 || noteScroll.height <= 0) return
        var noteChrome = 0
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
        multilineFields.forEach { field ->
            val height = if (field.parent === panels[1]) minOf(dp(160), parameterScroll.height).coerceAtLeast(dp(48)) else noteHeight
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
