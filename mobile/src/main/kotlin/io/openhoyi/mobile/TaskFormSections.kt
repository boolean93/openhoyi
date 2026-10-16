package io.openhoyi.mobile

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** One centered entry card; only fields scroll, while its save action remains reachable. */
internal class TaskFormSections(private val activity: ThemedActivity, body: LinearLayout,
    labels: List<String>, selected: Int = 0, private val changed: (Int) -> Unit = {}) {
    private fun dp(value: Int) = HoyiUi.dp(activity, value)
    val panels: List<LinearLayout>
    val actions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
    private val scroll = ScrollView(activity).apply { tag = "task-form-scroll" }
    private val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val card = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = HoyiUi.shape(activity, R.color.mobile_surface, 12, R.color.mobile_border)
    }
    private val host = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            (card.layoutParams as LayoutParams).width = minOf(MeasureSpec.getSize(widthMeasureSpec), dp(680))
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply { gravity = Gravity.CENTER_HORIZONTAL; tag = "task-inline-form" }

    init {
        body.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        host.addView(card, LinearLayout.LayoutParams(-1, -1))
        card.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.addView(content)
        panels = labels.mapIndexed { index, label ->
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                tag = "bean-full-field"
                content.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                    if (index > 0) topMargin = dp(8)
                })
                if (index == 2) HoyiUi.label(activity, this, label, 20, true)
            }
        }
        card.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        changed(selected.coerceIn(panels.indices))
    }

    fun jumpTo(index: Int) {
        val target = index.coerceIn(panels.indices)
        changed(target)
        panels[target].post { scroll.scrollTo(0, panels[target].top) }
    }

    fun restore(state: Bundle?, fields: Map<String, EditText>) {
        if (state == null) return
        scroll.post {
            fields[state.getString("form-focus")]?.takeIf { it.isEnabled && it.isShown }?.let { field ->
                field.requestFocus()
                field.setSelection(state.getInt("form-start").coerceIn(0, field.length()),
                    state.getInt("form-end").coerceIn(0, field.length()))
            }
            scroll.scrollTo(0, state.getInt("form-scroll"))
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
