package io.openhoyi.mobile

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** M2 opt-in presentation only. Never changes inputType, filters, selection or listeners. */
internal class TaskProductFormStyle(val activity: ThemedActivity) {
    val visual = TaskProductVisualStyle(activity)
    fun dp(value: Int) = visual.dp(value)
    fun input(field: EditText, page: String, key: String): EditText = field.apply {
        tag = "product/form/$page/$key"
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = TaskTypography.face(context, 400); paint.isFakeBoldText = false
        includeFontPadding = false; letterSpacing = 0f
        if (android.os.Build.VERSION.SDK_INT >= 28) setLineHeight(visual.sp(24).toInt())
        else setLineSpacing((visual.sp(24) - paint.fontMetricsInt.descent + paint.fontMetricsInt.ascent).coerceAtLeast(0f), 1f)
        minimumHeight = dp(52); minHeight = dp(52)
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(visual.onDisabled, visual.ink)))
        setHintTextColor(visual.muted)
        setPaddingRelative(dp(12), dp(8), dp(12), dp(8))
        backgroundTintList = null
        background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), visual.shape(visual.disabled, true, 10))
            addState(intArrayOf(android.R.attr.state_focused), android.graphics.drawable.GradientDrawable().apply {
                setColor(visual.surface); cornerRadius = dp(10).toFloat(); setStroke(dp(1), visual.accent)
            })
            addState(intArrayOf(), visual.shape(visual.surface, true, 10))
        }
    }
    fun label(view: TextView, belowGap: Boolean = true) = visual.apply(view, TaskProductVisualStyle.auxiliary, true).apply {
        setPadding(0, 0, 0, if (belowGap) dp(8) else 0)
    }
    fun error(view: TextView) = visual.apply(view, TaskProductVisualStyle.auxiliary).apply {
        setTextColor(activity.getColor(R.color.mobile_danger)); setPadding(0, dp(8), 0, 0)
    }
    val multilineMeasurements = org.json.JSONObject()
    /** Two real shaped lines, including the last line's descent, never lineHeight * 2. */
    fun twoLineMinimum(field: EditText): Int {
        val existing = field.layout
        val first = if (existing != null && existing.lineCount > 0 && field.text.isNotEmpty())
            field.text.subSequence(existing.getLineStart(0).coerceAtMost(field.length()), existing.getLineEnd(0).coerceAtMost(field.length())).toString().trimEnd('\n')
            else field.text.toString().substringBefore('\n')
        val line = first.ifEmpty { field.hint?.toString()?.substringBefore('\n')?.takeIf { it.isNotEmpty() } ?: "国" }
        val sample = line + "\n" + line
        val width = kotlin.math.ceil(android.text.Layout.getDesiredWidth(line, field.paint).toDouble()).toInt().coerceAtLeast(1)
        val builder = android.text.StaticLayout.Builder.obtain(sample, 0, sample.length, android.text.TextPaint(field.paint), width)
            .setIncludePad(field.includeFontPadding).setLineSpacing(field.lineSpacingExtra, field.lineSpacingMultiplier)
            .setBreakStrategy(field.breakStrategy).setHyphenationFrequency(field.hyphenationFrequency)
        if (android.os.Build.VERSION.SDK_INT >= 28) builder.setUseLineSpacingFromFallbacks(field.isFallbackLineSpacing)
        val layout = builder.build()
        val lineBox = layout.getLineBottom(minOf(1, layout.lineCount - 1)) - layout.getLineTop(0)
        val result = maxOf(dp(52), lineBox + field.compoundPaddingTop + field.compoundPaddingBottom)
        multilineMeasurements.put(field.tag.toString(), org.json.JSONObject().put("source", "two-paragraph actual-Paint StaticLayout")
            .put("lineCount", layout.lineCount).put("firstTwoLinesPx", lineBox).put("paddingPx", field.compoundPaddingTop + field.compoundPaddingBottom)
            .put("minimumPx", result).put("emptyUsesCandidateGlyph", first.isEmpty() && field.hint.isNullOrEmpty()))
        return result
    }
    fun choiceAdapter(labels: List<String>) = object : android.widget.ArrayAdapter<String>(activity, android.R.layout.simple_spinner_dropdown_item, labels) {
        private fun present(view: View, selected: Boolean) = (view as TextView).apply {
            visual.apply(this, TaskProductVisualStyle.body)
            setSingleLine(false); maxLines = if (selected) 2 else Int.MAX_VALUE
            ellipsize = if (selected) android.text.TextUtils.TruncateAt.END else null
            minimumHeight = dp(52); setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(visual.surface); contentDescription = text
            if (selected) tag = "full-text-details"
        }
        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = present(super.getView(position, convertView, parent), true)
        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = present(super.getDropDownView(position, convertView, parent), false)
    }
    /** Construction-only wrapper. A measured EditText baseline, not box-centering, places its unit. */
    fun unit(field: EditText, value: String, page: String, key: String) {
        val parent = field.parent as LinearLayout
        val index = parent.indexOfChild(field)
        val params = field.layoutParams
        parent.removeView(field)
        val unit = visual.text(value, TaskProductVisualStyle.auxiliary, "product/form/$page/$key/unit", true)
        val wrapper = object : FrameLayout(activity) {
            init { tag = "product/form/$page/$key/with-unit"; addView(field); addView(unit) }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                unit.measure(natural, natural)
                field.setPaddingRelative(dp(12), dp(8), dp(12) + unit.measuredWidth + dp(8), dp(8))
                field.measure(widthMeasureSpec, heightMeasureSpec)
                setMeasuredDimension(field.measuredWidth, field.measuredHeight)
            }
            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                field.layout(0, 0, width, height)
                val x = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) dp(12) else width - dp(12) - unit.measuredWidth
                val y = field.baseline - unit.baseline
                unit.layout(x, y, x + unit.measuredWidth, y + unit.measuredHeight)
            }
        }
        parent.addView(wrapper, index, params)
    }
    fun selected(button: android.widget.Button, selected: Boolean) {
        visual.button(button)
        button.setTextColor(if (!button.isEnabled) visual.onDisabled else if (selected) visual.accent else visual.muted)
        val fill = if (selected) visual.selected else Color.TRANSPARENT
        button.background = RippleDrawable(ColorStateList.valueOf(visual.selected), StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), visual.shape(fill, true, 8))
            addState(intArrayOf(), visual.shape(fill, false, 8))
        }, null)
    }
    /** Notice + original action objects, inline iff their real natural widths fit. */
    fun footer(notice: TextView, actions: LinearLayout, page: String, topRule: Boolean = false) = object : LinearLayout(activity) {
        init {
            tag = "product/form/$page/footer"; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL
            if (topRule) {
                setPadding(0, dp(8), 0, 0)
                foreground = object : android.graphics.drawable.Drawable() {
                    private val paint = android.graphics.Paint().apply { color = visual.border; strokeWidth = activity.resources.displayMetrics.density }
                    override fun draw(canvas: android.graphics.Canvas) { canvas.drawLine(0f, dp(4).toFloat(), bounds.right.toFloat(), dp(4).toFloat(), paint) }
                    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
                    @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
                }
            }
            addView(notice, LayoutParams(0, -2, 1f)); addView(actions, LayoutParams(-2, -2))
        }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            notice.measure(natural, natural); actions.measure(natural, natural)
            val available = View.MeasureSpec.getSize(widthMeasureSpec)
            val inline = notice.measuredWidth + actions.measuredWidth + dp(16) <= available
            orientation = if (inline) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            (notice.layoutParams as LayoutParams).apply {
                width = if (inline) 0 else -1; weight = if (inline) 1f else 0f; marginEnd = if (inline) dp(16) else 0
            }
            (actions.layoutParams as LayoutParams).apply { width = if (inline) -2 else -1; topMargin = if (inline) 0 else dp(8) }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
