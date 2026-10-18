package io.openhoyi.mobile

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONObject

/** Permanent field hierarchy; only viewport measurement switches for a genuinely tight budget. */
internal class TaskCurveEditorForm(private val activity: ThemedActivity, body: LinearLayout,
    private val style: TaskProductFormStyle) {
    private fun dp(value: Int) = style.dp(value)
    private fun cell() = style.visual.column("bean-full-field")
    val metadata = List(3) { cell() }
    val stageRows = List(4) { cell() }
    val stageHeader = style.visual.row()
    val supportingBody = cell()
    private val stageContent = cell()
    private val metadataContent = style.visual.row()
    private val stagePane = cell()
    private var compact = false
    private val stages = ScrollView(activity).apply { tag = "product/form/curve-editor/stage-scroll"; addView(stageContent) }
    private val metadataScroll = ScrollView(activity).apply { tag = "product/form/curve-editor/metadata-scroll"; addView(metadataContent) }
    private val previewScroll = object : ScrollView(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            supportingBody.findViewWithTag<View>("task-form-stage-preview")?.let { chart ->
                val width = (View.MeasureSpec.getSize(widthMeasureSpec) - supportingBody.paddingLeft - supportingBody.paddingRight).coerceAtLeast(0)
                val fixed = fixedHeight(supportingBody, chart, width)
                chart.layoutParams.height = if (compact || View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) dp(160)
                    else (View.MeasureSpec.getSize(heightMeasureSpec) - fixed).coerceIn(dp(160), dp(280))
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply { tag = "product/form/curve-editor/preview-scroll"; isFillViewport = true; addView(supportingBody) }
    private val lower = style.visual.row()
    private val column = style.visual.column("product/form/curve-editor/content")
    private val contentScroll = object : ScrollView(activity) {
        override fun measureChildWithMargins(child: View, parentWidthMeasureSpec: Int, widthUsed: Int,
            parentHeightMeasureSpec: Int, heightUsed: Int) {
            if (compact) { super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed); return }
            val params = child.layoutParams as ViewGroup.MarginLayoutParams
            child.measure(getChildMeasureSpec(parentWidthMeasureSpec, paddingLeft + paddingRight + params.leftMargin + params.rightMargin + widthUsed, params.width),
                View.MeasureSpec.makeMeasureSpec((View.MeasureSpec.getSize(parentHeightMeasureSpec) - paddingTop - paddingBottom - params.topMargin - params.bottomMargin - heightUsed).coerceAtLeast(0), View.MeasureSpec.EXACTLY))
        }
    }.apply { tag = "product/form/curve-editor/content-scroll"; addView(column) }
    var productLayoutEvidence = JSONObject()
        private set
    private val host = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            metadataContent.measure(widthMeasureSpec, free)
            val metadataNatural = metadataContent.measuredHeight
            val metadataMinimum = metadata.maxOf { shortMinimum(it) + it.paddingTop + it.paddingBottom }
            val stageWidth = ((width - dp(24)).coerceAtLeast(0) * 1.1f / 2.1f).toInt()
            val previewWidth = (width - dp(24) - stageWidth).coerceAtLeast(0)
            val stageInner = (stageWidth - stagePane.paddingLeft - stagePane.paddingRight).coerceAtLeast(0)
            stageHeader.measure(MeasureSpec.makeMeasureSpec(stageInner, MeasureSpec.EXACTLY), free)
            columns.measure(MeasureSpec.makeMeasureSpec(stageInner, MeasureSpec.EXACTLY), free)
            val stageMinimum = stageHeader.measuredHeight + columns.measuredHeight +
                stageRows.filter { it.visibility != View.GONE }.maxOfOrNull { shortMinimum(it) }.orZero() + stagePane.paddingTop + stagePane.paddingBottom
            stageContent.measure(MeasureSpec.makeMeasureSpec(stageInner, MeasureSpec.EXACTLY), free)
            val stageNatural = stageContent.measuredHeight + stagePane.paddingTop + stagePane.paddingBottom
            val chart = supportingBody.findViewWithTag<View>("task-form-stage-preview")
            val previewMinimum = fixedHeight(supportingBody, chart, (previewWidth - supportingBody.paddingLeft - supportingBody.paddingRight).coerceAtLeast(0)) + dp(160)
            val lowerMinimum = maxOf(stageMinimum, previewMinimum)
            compact = height < metadataMinimum + dp(16) + lowerMinimum
            (metadataScroll.layoutParams as LayoutParams).height = if (compact) -2 else minOf(metadataNatural, height / 3).coerceAtLeast(minOf(metadataMinimum, height))
            (lower.layoutParams as LayoutParams).apply { this.height = if (compact) -2 else 0; weight = if (compact) 0f else 1f }
            (stagePane.layoutParams as LayoutParams).height = if (compact) -2 else -1
            (previewScroll.layoutParams as LayoutParams).height = if (compact) -2 else -1
            (stages.layoutParams as LayoutParams).apply { this.height = if (compact) -2 else 0; weight = if (compact) 0f else 1f }
            productLayoutEvidence = JSONObject().put("availablePx", height).put("metadataNaturalPx", metadataNatural)
                .put("metadataMinimumPx", metadataMinimum).put("stageMinimumPx", stageMinimum).put("previewMinimumPx", previewMinimum)
                .put("compactSingleContentScroll", compact).put("stageNaturalPx", stageNatural)
                .put("fullNaturalPx", metadataNatural + dp(16) + maxOf(stageNatural, previewMinimum))
            column.forceLayout(); metadataScroll.forceLayout(); lower.forceLayout(); stagePane.forceLayout(); previewScroll.forceLayout(); contentScroll.forceLayout()
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply { orientation = LinearLayout.VERTICAL; tag = "task-inline-form" }
    private val columns = style.visual.row()
    init {
        (metadata + listOf(stagePane, supportingBody)).forEach { panel ->
            panel.background = style.visual.shape(style.visual.surface, true)
            panel.setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        body.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        host.addView(contentScroll, LinearLayout.LayoutParams(-1, -1))
        column.addView(metadataScroll, LinearLayout.LayoutParams(-1, -2))
        metadata.forEachIndexed { index, panel -> metadataContent.addView(panel, LinearLayout.LayoutParams(0, -2, if (index == 0) 1.3f else 1f).apply { if (index > 0) marginStart = dp(16) }) }
        column.addView(lower, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        lower.addView(stagePane, LinearLayout.LayoutParams(0, -1, 1.1f))
        lower.addView(previewScroll, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(24) })
        stageContent.addView(stageHeader, LinearLayout.LayoutParams(-1, -2))
        stageContent.addView(columns, LinearLayout.LayoutParams(-1, -2))
        val firstWidth = maxOf(dp(80), dp(20) + kotlin.math.ceil(android.text.Layout.getDesiredWidth(activity.getString(R.string.profiles_stage, 4),
            style.visual.text("", TaskProductVisualStyle.body).paint).toDouble()).toInt())
        listOf(R.string.import_table_stage, R.string.import_table_pressure, R.string.import_table_water).forEachIndexed { index, label ->
            val heading = style.visual.text(activity.getString(label), TaskProductVisualStyle.auxiliary, secondary = true).apply { setPadding(0, 0, 0, dp(8)) }
            columns.addView(heading, if (index == 0) LinearLayout.LayoutParams(firstWidth, -2)
                else LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        }
        stagePane.addView(stages, LinearLayout.LayoutParams(-1, 0, 1f))
        stageContent.foreground = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = this@TaskCurveEditorForm.style.visual.border; strokeWidth = activity.resources.displayMetrics.density }
            override fun draw(canvas: android.graphics.Canvas) {
                fun rule(y: Float) { canvas.drawLine(0f, y, bounds.right.toFloat(), y, paint) }
                rule(stageHeader.bottom.toFloat())
                rule((columns.bottom - dp(4)).toFloat())
                stageRows.filter { it.visibility != View.GONE }.zipWithNext().forEach { (row, next) ->
                    rule((row.bottom + next.top) / 2f)
                }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        stageRows.forEachIndexed { index, row ->
            row.tag = "curve-editor-stage-$index"; row.orientation = LinearLayout.HORIZONTAL; row.isBaselineAligned = false; row.gravity = android.view.Gravity.CENTER_VERTICAL
            stageContent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(8) })
            val identity = style.visual.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            identity.addView(View(activity).apply { background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(style.visual.stageColors[index]) } }, LinearLayout.LayoutParams(dp(12), dp(12)))
            identity.addView(style.visual.text(activity.getString(R.string.profiles_stage, index + 1), TaskProductVisualStyle.body), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            row.addView(identity, LinearLayout.LayoutParams(firstWidth, -2))
        }
    }
    private fun Int?.orZero() = this ?: 0
    private fun shortMinimum(view: View): Int {
        if (view is EditText) {
            view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val naturalHeight = view.measuredHeight
            view.forceLayout()
            return naturalHeight
        }
        if (view !is ViewGroup) return 0
        val minimum = (0 until view.childCount).filter { view.getChildAt(it).visibility != View.GONE }.maxOfOrNull { shortMinimum(view.getChildAt(it)) } ?: 0
        view.forceLayout()
        return minimum
    }
    private fun fixedHeight(parent: LinearLayout, except: View?, width: Int): Int {
        var total = parent.paddingTop + parent.paddingBottom
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child !== except && child.visibility != View.GONE) {
                child.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val params = child.layoutParams as LinearLayout.LayoutParams
                total += child.measuredHeight + params.topMargin + params.bottomMargin
            }
        }
        return total
    }
    fun setStageCount(count: Int) { stageRows.forEachIndexed { index, row -> row.visibility = if (index < count) View.VISIBLE else View.GONE } }
    fun save(state: Bundle, fields: Map<String, EditText>) {
        state.putInt("form-scroll", stages.scrollY); state.putInt("metadata-scroll", metadataScroll.scrollY); state.putInt("editor-content-scroll", contentScroll.scrollY)
        fields.entries.firstOrNull { it.value.hasFocus() }?.let { (key, field) ->
            state.putString("form-focus", key); state.putInt("form-start", field.selectionStart); state.putInt("form-end", field.selectionEnd)
        }
    }
    fun restore(state: Bundle?, fields: Map<String, EditText>) {
        if (state == null) return
        host.post {
            fields[state.getString("form-focus")]?.takeIf { it.isEnabled && it.isShown }?.let { field ->
                field.requestFocus(); field.setSelection(state.getInt("form-start").coerceIn(0, field.length()), state.getInt("form-end").coerceIn(0, field.length()))
            }
            stages.scrollTo(0, state.getInt("form-scroll")); metadataScroll.scrollTo(0, state.getInt("metadata-scroll")); contentScroll.scrollTo(0, state.getInt("editor-content-scroll"))
        }
    }
}
