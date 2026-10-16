package io.openhoyi.mobile

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** Recipe metadata stays above the stage table; only task regions scroll when height is tight. */
internal class TaskCurveEditorForm(private val activity: ThemedActivity, body: LinearLayout) {
    private fun dp(value: Int) = HoyiUi.dp(activity, value)
    val metadata = List(3) { cell() }
    val stageRows = List(4) { cell() }
    val stageHeader = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    val supportingBody = cell()
    private val stageContent = cell()
    private val stages = ScrollView(activity).apply { addView(stageContent) }
    private val metadataContent = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
    private val metadataScroll = ScrollView(activity).apply { addView(metadataContent) }
    private val previewScroll = ScrollView(activity).apply { isFillViewport = true; addView(supportingBody) }
    private val lower = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
    private val stagePane = cell()
    private val host = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val height = MeasureSpec.getSize(heightMeasureSpec)
            metadataContent.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            (metadataScroll.layoutParams as LayoutParams).height = minOf(metadataContent.measuredHeight, height / 3)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply { orientation = LinearLayout.VERTICAL; tag = "task-inline-form" }

    init {
        body.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        host.addView(metadataScroll, LinearLayout.LayoutParams(-1, -2))
        metadata.forEachIndexed { index, cell ->
            metadataContent.addView(cell, LinearLayout.LayoutParams(0, -2, if (index == 0) 1.3f else 1f).apply {
                if (index > 0) marginStart = dp(16)
            })
        }
        host.addView(lower, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        lower.addView(stagePane, LinearLayout.LayoutParams(0, -1, 1.1f))
        lower.addView(previewScroll, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(24) })
        stagePane.addView(stageHeader, LinearLayout.LayoutParams(-1, -2))
        stagePane.addView(stages, LinearLayout.LayoutParams(-1, 0, 1f))
        stageRows.forEachIndexed { index, row ->
            row.tag = "curve-editor-stage-$index"
            stageContent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
            HoyiUi.label(activity, row, activity.getString(R.string.profiles_stage, index + 1), 16, true).apply {
                setTextColor(CurveStagePalette.color(activity, index))
            }
        }
    }

    private fun cell() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        tag = "bean-full-field"
    }

    fun setStageCount(count: Int) {
        stageRows.forEachIndexed { index, row -> row.visibility = if (index < count) View.VISIBLE else View.GONE }
    }

    fun save(state: Bundle, fields: Map<String, EditText>) {
        state.putInt("form-scroll", stages.scrollY)
        state.putInt("metadata-scroll", metadataScroll.scrollY)
        fields.entries.firstOrNull { it.value.hasFocus() }?.let { (key, field) ->
            state.putString("form-focus", key)
            state.putInt("form-start", field.selectionStart)
            state.putInt("form-end", field.selectionEnd)
        }
    }

    fun restore(state: Bundle?, fields: Map<String, EditText>) {
        if (state == null) return
        host.post {
            fields[state.getString("form-focus")]?.takeIf { it.isEnabled && it.isShown }?.let { field ->
                field.requestFocus()
                field.setSelection(state.getInt("form-start").coerceIn(0, field.length()),
                    state.getInt("form-end").coerceIn(0, field.length()))
            }
            stages.scrollTo(0, state.getInt("form-scroll"))
            metadataScroll.scrollTo(0, state.getInt("metadata-scroll"))
        }
    }
}
