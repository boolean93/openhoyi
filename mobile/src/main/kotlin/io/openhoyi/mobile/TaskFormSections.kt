package io.openhoyi.mobile

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner

/** Stable inline cards. The bean-sections selector is a jump list, not a visibility filter. */
internal class TaskFormSections(private val activity: ThemedActivity, body: LinearLayout,
    private val labels: List<String>, selected: Int = 0, private val changed: (Int) -> Unit = {}) {
    val panels: List<LinearLayout>
    val selector: Spinner
    val supportingBody = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val supportingScroll = ScrollView(activity).apply { addView(supportingBody) }
    private val scroll = ScrollView(activity).apply { tag = "task-form-scroll"; isFillViewport = false }
    private val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var visibleCount = labels.size
    private var selectedIndex = selected.coerceIn(labels.indices)
    private var restoring = false
    private val primary = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val host = object : LinearLayout(activity) {
        private var paneMode: Boolean? = null
        private var formWidth = -1
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val density = resources.displayMetrics.density
            val twoPane = supportingBody.childCount > 0 && TaskLayoutPolicy.decide(
                resources.configuration.screenWidthDp.coerceAtLeast(1), (height / density).toInt()).twoPaneAllowed &&
                width >= HoyiUi.dp(activity, 624)
            val availableFormWidth = if (twoPane) width - HoyiUi.dp(activity, 304) else width
            val desiredWidth = minOf(availableFormWidth, HoyiUi.dp(activity, 680))
            if (paneMode != twoPane || formWidth != desiredWidth) {
                paneMode = twoPane
                formWidth = desiredWidth
                supportingScroll.visibility = if (twoPane) View.VISIBLE else View.GONE
                primary.layoutParams = LayoutParams(desiredWidth, -1)
                supportingScroll.layoutParams = LayoutParams(HoyiUi.dp(activity, 280), -1).apply { marginStart = HoyiUi.dp(activity, 24) }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL; tag = "task-inline-form" }

    init {
        body.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        host.addView(primary)
        host.addView(supportingScroll)
        selector = Spinner(activity).apply {
            tag = "bean-sections"
            adapter = HoyiUi.spinnerAdapter(activity, labels)
            contentDescription = activity.getString(R.string.task_form_jump)
            minimumHeight = HoyiUi.dp(activity, 48)
            primary.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        primary.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        panels = labels.map { label ->
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                // Keep simple fields full-width, even when the device itself qualifies as wide.
                tag = "bean-full-field"
                setPadding(HoyiUi.dp(activity, 12), HoyiUi.dp(activity, 8), HoyiUi.dp(activity, 12), HoyiUi.dp(activity, 12))
                background = HoyiUi.shape(activity, R.color.mobile_surface, 12, R.color.mobile_border)
                content.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = HoyiUi.dp(activity, 10) })
                HoyiUi.label(activity, this, label, 16, true)
            }
        }
        selector.setSelection(selectedIndex)
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val different = selectedIndex != position
                selectedIndex = position
                changed(position)
                if (different && !restoring) reveal(position)
            }
        }
    }

    fun jumpTo(index: Int) {
        val target = index.coerceIn(0, visibleCount - 1)
        selectedIndex = target
        selector.setSelection(target)
        changed(target)
        reveal(target)
    }

    private fun reveal(index: Int) {
        panels[index].post { scroll.scrollTo(0, panels[index].top) }
    }

    fun setVisiblePanelCount(count: Int) {
        val bounded = count.coerceIn(1, panels.size)
        if (visibleCount == bounded) return
        visibleCount = bounded
        panels.forEachIndexed { index, panel -> panel.visibility = if (index < bounded) View.VISIBLE else View.GONE }
        val next = selectedIndex.coerceAtMost(bounded - 1)
        selectedIndex = next
        selector.adapter = HoyiUi.spinnerAdapter(activity, labels.take(bounded))
        selector.setSelection(next)
        changed(next)
    }

    fun restore(state: Bundle?, fields: Map<String, EditText>) {
        if (state == null) return
        restoring = true
        scroll.post {
            val field = fields[state.getString("form-focus")]
            if (field != null && field.isEnabled && field.isShown) {
                field.requestFocus()
                field.setSelection(state.getInt("form-start").coerceIn(0, field.length()),
                    state.getInt("form-end").coerceIn(0, field.length()))
            }
            scroll.scrollTo(0, state.getInt("form-scroll"))
            restoring = false
        }
    }

    fun save(state: Bundle, fields: Map<String, EditText>) {
        state.putInt("form-scroll", scroll.scrollY)
        fields.entries.firstOrNull { it.value.hasFocus() }?.let { (key, field) ->
            state.putString("form-focus", key)
            state.putInt("form-start", field.selectionStart)
            state.putInt("form-end", field.selectionEnd)
        }
    }
}
