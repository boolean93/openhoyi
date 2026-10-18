package io.openhoyi.mobile

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONObject

/** One card; original fields never move between parents when its budget changes. */
internal class TaskFormSections(private val activity: ThemedActivity, body: LinearLayout,
    labels: List<String>, selected: Int = 0, private val style: TaskProductFormStyle,
    private val changed: (Int) -> Unit = {}) {
    private fun dp(value: Int) = style.dp(value)
    val actions = style.visual.column().apply { gravity = Gravity.END }
    val modeHost = style.visual.column("product/form/bean-entry/mode")
    val panels = labels.indices.map { style.visual.column("bean-full-field") }
    private val identity = style.visual.column("product/form/bean-entry/identity-and-stock")
    private val content = style.visual.column("product/form/bean-entry/content")
    private val scroll = ScrollView(activity).apply { tag = "task-form-scroll"; addView(content) }
    private val card = style.visual.column().apply {
        setPadding(dp(16), dp(16), dp(16), dp(16)); background = style.visual.shape(style.visual.surface, true)
    }
    var productLayoutEvidence = JSONObject()
        private set
    private val host = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cardWidth = minOf(MeasureSpec.getSize(widthMeasureSpec), dp(680))
            (card.layoutParams as LayoutParams).width = cardWidth
            val inner = (cardWidth - card.paddingLeft - card.paddingRight).coerceAtLeast(0)
            val exact = MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY)
            val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            fun arrangement(inline: Boolean) {
                identity.orientation = if (inline) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                panels.take(2).forEachIndexed { index, panel ->
                    (panel.layoutParams as LayoutParams).apply {
                        width = if (inline) 0 else -1; height = -2; weight = if (inline) (if (index == 0) 3f else 2f) else 0f
                        marginStart = if (inline && index > 0) dp(16) else 0
                        marginEnd = 0
                        topMargin = if (!inline && index > 0) dp(8) else 0
                        // In-place measure changes do not call View.setLayoutParams,
                        // so resolve the physical margins used by LinearLayout now.
                        resolveLayoutDirection(identity.layoutDirection)
                    }
                }
                identity.forceLayout(); content.forceLayout()
            }
            arrangement(false)
            content.measure(exact, free); actions.measure(exact, free)
            val fixed = card.paddingTop + card.paddingBottom + actions.measuredHeight + dp(8)
            val vertical = content.measuredHeight + fixed
            val available = MeasureSpec.getSize(heightMeasureSpec)
            val firstWidth = ((inner - dp(16)).coerceAtLeast(0) * 3 / 5)
            val secondWidth = (inner - dp(16) - firstWidth).coerceAtLeast(0)
            panels.take(2).forEach { it.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), free) }
            val fits = panels[0].measuredWidth <= firstWidth && panels[1].measuredWidth <= secondWidth
            val inline = vertical > available && fits
            arrangement(inline)
            productLayoutEvidence = JSONObject().put("verticalNaturalPx", vertical).put("availablePx", available)
                .put("inlineFits", fits).put("inlineSelected", inline).put("identityWidthPx", firstWidth).put("quantityWidthPx", secondWidth)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply { gravity = Gravity.CENTER_HORIZONTAL; orientation = LinearLayout.VERTICAL; tag = "task-inline-form" }
    init {
        body.addView(host, LinearLayout.LayoutParams(-1, 0, 1f)); host.addView(card, LinearLayout.LayoutParams(-1, -1))
        card.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(modeHost, LinearLayout.LayoutParams(-1, -2))
        content.addView(identity, LinearLayout.LayoutParams(-1, -2))
        panels.take(2).forEach { identity.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        content.addView(panels[2], LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        style.visual.add(panels[2], style.visual.text(labels[2], TaskProductVisualStyle.section))
        // The upper date rule is drawn inside the existing group gap, not another spacer.
        content.foreground = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = this@TaskFormSections.style.visual.border; strokeWidth = activity.resources.displayMetrics.density }
            override fun draw(canvas: android.graphics.Canvas) { val y = (panels[2].top - dp(4)).toFloat(); canvas.drawLine(0f, y, bounds.right.toFloat(), y, paint) }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        card.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        changed(selected.coerceIn(panels.indices))
    }
    fun jumpTo(index: Int) {
        val target = index.coerceIn(panels.indices); changed(target)
        panels[target].post { val rect = android.graphics.Rect(); panels[target].getDrawingRect(rect); panels[target].requestRectangleOnScreen(rect, true) }
    }
    fun restore(state: Bundle?, fields: Map<String, EditText>) {
        if (state == null) return
        scroll.post {
            fields[state.getString("form-focus")]?.takeIf { it.isEnabled && it.isShown }?.let { field ->
                field.requestFocus(); field.setSelection(state.getInt("form-start").coerceIn(0, field.length()), state.getInt("form-end").coerceIn(0, field.length()))
            }
            scroll.scrollTo(0, state.getInt("form-scroll"))
        }
    }
    fun save(state: Bundle, fields: Map<String, EditText>) {
        state.putInt("form-scroll", scroll.scrollY)
        fields.entries.firstOrNull { it.value.hasFocus() }?.let { (key, field) ->
            state.putString("form-focus", key); state.putInt("form-start", field.selectionStart); state.putInt("form-end", field.selectionEnd)
        }
    }
}
