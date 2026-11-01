package io.openhoyi.mobile

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** Stable inline parameter/notes regions; resize never reparents a field or changes a draft. */
internal class TaskReviewSections(private val activity: ThemedActivity, body: LinearLayout,
    labels: List<String>, selected: Int, private val style: TaskProductFormStyle, private val changed: (Int) -> Unit) {
    private var selectedIndex = selected.coerceIn(labels.indices)
    private val parameters = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val notes = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var multilineFields = emptyList<EditText>()
    private val parameterScroll = object : ScrollView(activity) {
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            post { fitEditors() }
        }
    }.apply { tag = "product/form/review/parameter-scroll"; addView(parameters) }
    private val noteScroll = object : ScrollView(activity) {
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            post { fitEditors() }
        }
    }.apply { tag = "product/form/review/notes-scroll"; addView(notes) }
    private val regions = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val density = resources.displayMetrics.density
            // These are independently scrolling editor viewports, not a chart/tool workspace.
            // The IME may reduce height without removing the width needed for two usable panes.
            val split = TaskLayoutPolicy.decide(resources.configuration.screenWidthDp.coerceAtLeast(1),
                (height / density).toInt(), twoPaneMinimumHeightDp = 0).twoPaneAllowed && width >= dp(624)
            val nextOrientation = if (split) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            val parameterWidth = if (split) dp(280) else width
            // A short native input is at least 52dp, before panel padding. Do not allocate
            // a 48dp viewport merely because that is the minimum touch target elsewhere.
            var parameterMinimum = dp(52)
            fun measureShort(view: View, ancestors: Int = 0) {
                if (view is EditText && view.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0) {
                    view.measure(MeasureSpec.makeMeasureSpec(parameterWidth.coerceAtLeast(0), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                    parameterMinimum = maxOf(parameterMinimum, view.measuredHeight + ancestors)
                } else if (view is ViewGroup) for (index in 0 until view.childCount)
                    measureShort(view.getChildAt(index), ancestors + view.paddingTop + view.paddingBottom)
            }
            panels.take(2).forEach { measureShort(it) }
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
        foreground = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = this@TaskReviewSections.style.visual.border; strokeWidth = activity.resources.displayMetrics.density }
            override fun draw(canvas: android.graphics.Canvas) {
                if (orientation == LinearLayout.HORIZONTAL) {
                    val x = (parameterScroll.right + noteScroll.left) / 2f
                    canvas.drawLine(x, 0f, x, bounds.bottom.toFloat(), paint)
                }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        addView(parameterScroll)
        addView(noteScroll)
    }
    private val host = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        tag = "task-inline-form"
        body.addView(this, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    private val parameterHeading = sectionHeading(parameters, R.string.journal_parameters)
    private val parameterDivider = sectionDivider(parameters)
    private val notesHeader = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        isBaselineAligned = false
        notes.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private val notesHeading = sectionHeading(notesHeader, R.string.journal_notes).apply {
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }
    private val notesDivider = sectionDivider(notes)
    val panels = labels.indices.map { index ->
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            tag = "bean-full-field"
            setPadding(0, 0, 0, 0)
            (if (index < 2) parameters else notes).addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                if (index == 1 || index > 2) topMargin = dp(16)
            })
        }
    }
    init { host.addView(regions, LinearLayout.LayoutParams(-1, 0, 1f)) }

    private fun sectionHeading(parent: LinearLayout, label: Int) =
        HoyiUi.label(activity, parent, activity.getString(label), 20, true).apply {
            style.visual.apply(this, TaskProductVisualStyle.section)
            setPadding(0, 0, 0, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        }

    private fun sectionDivider(parent: LinearLayout) = View(activity).apply {
        setBackgroundColor(style.visual.border)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(8); bottomMargin = dp(8)
        })
    }

    private fun dp(value: Int) = HoyiUi.dp(activity, value)
    private fun reveal(index: Int) {
        val scroll = if (index < 2) parameterScroll else noteScroll
        panels[index].post { scroll.scrollTo(0, panels[index].top) }
    }
    fun jumpTo(index: Int) {
        selectedIndex = index.coerceIn(panels.indices)
        changed(selectedIndex)
        reveal(selectedIndex)
    }
    fun fitMultiline(fields: List<EditText>) { multilineFields = fields; noteScroll.post { fitEditors() } }
    private fun fitEditors() {
        // Every multiline box keeps its actual two-line font/padding minimum,
        // even when the surrounding region must scroll under the IME.
        fun minimum(field: EditText) = style.twoLineMinimum(field)
        multilineFields.filter { it.parent === panels[1] }.forEach { field ->
            val height = minimum(field)
            if (field.layoutParams.height != height) field.layoutParams = (field.layoutParams as LinearLayout.LayoutParams).apply { this.height = height; weight = 0f }
        }
        if (noteScroll.width <= 0 || noteScroll.height <= 0) return
        val exact = View.MeasureSpec.makeMeasureSpec(noteScroll.width, View.MeasureSpec.EXACTLY)
        val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        notesHeader.measure(exact, free)
        val dividerParams = notesDivider.layoutParams as LinearLayout.LayoutParams
        var noteChrome = notesHeader.measuredHeight + dividerParams.height + dividerParams.topMargin + dividerParams.bottomMargin
        panels.drop(2).forEach { panel ->
            val params = panel.layoutParams as LinearLayout.LayoutParams
            noteChrome += panel.paddingTop + panel.paddingBottom + params.topMargin + params.bottomMargin
            for (index in 0 until panel.childCount) {
                val child = panel.getChildAt(index)
                if (child !is EditText && child.visibility != View.GONE) {
                    child.measure(exact, free)
                    val childParams = child.layoutParams as LinearLayout.LayoutParams
                    noteChrome += child.measuredHeight + childParams.topMargin + childParams.bottomMargin
                }
            }
        }
        val eachBudget = (noteScroll.height - noteChrome) / 3
        multilineFields.filter { it.parent !== panels[1] }.forEach { field ->
            val height = eachBudget.coerceIn(minimum(field), maxOf(dp(160), minimum(field)))
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
