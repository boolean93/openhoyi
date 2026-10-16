package io.openhoyi.mobile

import android.view.View
import android.widget.LinearLayout

/** Share-only composition; dialogs keep their independent preview layout. */
internal fun ThemedActivity.sharePreview(workspace: TaskWorkspaceLayout, doc: CustomCurveDocument) {
    val preview = workspace.primaryBody
    HoyiUi.label(this, preview, doc.name, 24, true).apply {
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        tag = "full-text-details"
    }
    HoyiUi.label(this, preview, getString(if (CustomPressureCurveAdapter.profile(doc, false) != null)
        R.string.profiles_pressure_compatible else R.string.profiles_read_only), 14, muted = true)
    val metrics = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, HoyiUi.dp(this@sharePreview, 16), 0, HoyiUi.dp(this@sharePreview, 16))
    }
    preview.addView(metrics)
    listOf(
        R.string.curve_preview_temperature to "${doc.temperatureC} °C",
        R.string.curve_preview_weight to if (doc.targetHundredthsGram == 0) getString(R.string.curve_details_no_weight)
            else "${CurveDraftNumber.format(doc.targetHundredthsGram, 2)} g"
    ).forEach { (label, value) ->
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, HoyiUi.dp(this@sharePreview, 8), 0)
        }
        metrics.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        HoyiUi.label(this, column, getString(label), 14, muted = true)
        HoyiUi.label(this, column, value, 24).apply {
            typeface = TaskTypography.face(this@sharePreview, 700)
            setPadding(0, HoyiUi.dp(this@sharePreview, 4), 0, 0)
        }
    }
    if (doc.controlMode != CustomCurveDocument.ControlMode.PRESSURE) {
        HoyiUi.label(this, preview, getString(R.string.profiles_flow_raw), 16, muted = true)
        return
    }
    val chart = CurveStageView(this).apply { targets = doc.stages.map { it.target } }
    preview.addView(chart, LinearLayout.LayoutParams(-1, 0, 1f))
    HoyiUi.label(this, preview, getString(R.string.profiles_stage_caption), 14, muted = true)
    val table = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        tag = "share-stage-table"
        setPadding(0, HoyiUi.dp(this@sharePreview, 16), 0, 0)
    }
    preview.addView(table, LinearLayout.LayoutParams(-1, -2))
    fun row(values: List<String>, heading: Boolean, stageIndex: Int? = null) {
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        table.addView(line, LinearLayout.LayoutParams(-1, -2))
        values.forEachIndexed { column, value ->
            HoyiUi.label(this, line, value, 14, bold = heading, muted = heading).apply {
                setPadding(HoyiUi.dp(this@sharePreview, 8), HoyiUi.dp(this@sharePreview, 4),
                    HoyiUi.dp(this@sharePreview, 8), HoyiUi.dp(this@sharePreview, 4))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                if (column == 0 && stageIndex != null) setTextColor(CurveStagePalette.color(context, stageIndex))
            }
        }
    }
    row(listOf(R.string.import_table_stage, R.string.import_table_pressure, R.string.import_table_water).map(::getString), true)
    doc.stages.forEachIndexed { index, stage ->
        row(listOf(getString(R.string.profiles_stage, index + 1), CurveDraftNumber.format(stage.target, 1),
            CurveDraftNumber.format(stage.waterTenthsMl, 1)), false, index)
    }
    workspace.onPrimarySpaceAvailable = { width, height ->
        val contentWidth = (width - preview.paddingLeft - preview.paddingRight).coerceAtLeast(0)
        val exactWidth = View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        var fixedHeight = preview.paddingTop + preview.paddingBottom
        for (index in 0 until preview.childCount) {
            val child = preview.getChildAt(index)
            if (child !== chart && child !== table) {
                child.measure(exactWidth, unspecified)
                val params = child.layoutParams as LinearLayout.LayoutParams
                fixedHeight += child.measuredHeight + params.topMargin + params.bottomMargin
            }
        }
        table.measure(exactWidth, unspecified)
        val minimumChart = HoyiUi.dp(this, 160)
        val showTable = height >= fixedHeight + table.measuredHeight + minimumChart
        table.visibility = if (showTable) View.VISIBLE else View.GONE
        fixedHeight + minimumChart + if (showTable) table.measuredHeight else 0
    }
}
