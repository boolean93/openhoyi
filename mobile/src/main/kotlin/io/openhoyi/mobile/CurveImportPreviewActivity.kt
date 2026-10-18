package io.openhoyi.mobile

import android.os.Bundle
import android.widget.LinearLayout

class CurveImportPreviewActivity : ThemedActivity() {
    companion object { const val DOCUMENT = "curveDocument" }
    private var document: CustomCurveDocument? = null
    private var finishedSave = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishedSave = savedInstanceState?.getBoolean("finishedSave") ?: false
        if (finishedSave) { finish(); return }
        document = runCatching { CustomCurveDocument.decode(intent.getStringExtra(DOCUMENT).orEmpty()) }.getOrNull()
        render()
    }
    private fun render(conflicting: CustomCurveDocument? = null) {
        val body = beanPage(getString(R.string.profiles_import_preview), getString(R.string.profiles_compatibility))
        val doc = document
        if (doc == null) { HoyiUi.label(this, body, getString(R.string.profiles_import_invalid), 17); return }
        val store = profileStore(body) ?: return
        val existing = conflicting ?: store.find(doc.id)?.takeIf { it != doc }
        val workspace = TaskWorkspaceLayout(this, supportingWidthFraction = .35f, supportingMaxWidthDp = 400)
        body.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = HoyiUi.dp(this@CurveImportPreviewActivity, 16) })
        val preview = workspace.primaryBody
        val decisions = workspace.secondaryBody
        HoyiUi.label(this, preview, doc.name, 24, true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            tag = "full-text-details"
        }
        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, HoyiUi.dp(this@CurveImportPreviewActivity, 16), 0, HoyiUi.dp(this@CurveImportPreviewActivity, 16))
        }
        preview.addView(metrics)
        listOf(
            R.string.curve_preview_temperature to "${doc.temperatureC} °C",
            R.string.curve_preview_weight to if (doc.targetHundredthsGram == 0) getString(R.string.curve_details_no_weight)
                else "${CurveDraftNumber.format(doc.targetHundredthsGram, 2)} g",
            R.string.curve_preview_stages to doc.stages.size.toString()
        ).forEach { (label, value) ->
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, HoyiUi.dp(this@CurveImportPreviewActivity, 8), 0)
            }
            metrics.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
            HoyiUi.label(this, column, getString(label), 14, muted = true)
            HoyiUi.label(this, column, value, 24).apply {
                typeface = TaskTypography.face(this@CurveImportPreviewActivity, 700)
                setPadding(0, HoyiUi.dp(this@CurveImportPreviewActivity, 4), 0, 0)
            }
        }
        var stageChart: CurveStageView? = null
        if (doc.controlMode == CustomCurveDocument.ControlMode.PRESSURE) {
            HoyiUi.label(this, preview, getString(R.string.curve_library_stage_heading), TaskVisualTokens.SECTION_SP, true).apply {
                setPadding(0, 0, 0, HoyiUi.dp(this@CurveImportPreviewActivity, TaskVisualTokens.RELATED_GAP_DP))
            }
            stageChart = CurveStageView(this).apply { targets = doc.stages.map { it.target } }
            preview.addView(stageChart, LinearLayout.LayoutParams(-1, 0, 1f))
            HoyiUi.label(this, preview, getString(R.string.profiles_stage_caption), 14, muted = true)
        } else HoyiUi.label(this, preview, getString(R.string.profiles_flow_raw), 15, muted = true)
        val table = if (stageChart != null) stageTable(doc).also {
            preview.addView(it, LinearLayout.LayoutParams(-1, -2))
        } else null
        if (table != null) workspace.onPrimarySpaceAvailable = { width, height ->
            val exactWidth = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY)
            val unspecified = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            var fixedHeight = 0
            for (index in 0 until preview.childCount) {
                val child = preview.getChildAt(index)
                if (child !== stageChart && child !== table) {
                    child.measure(exactWidth, unspecified)
                    val params = child.layoutParams as LinearLayout.LayoutParams
                    fixedHeight += child.measuredHeight + params.topMargin + params.bottomMargin
                }
            }
            table.measure(exactWidth, unspecified)
            val minimumChart = HoyiUi.dp(this, 160)
            val showTable = height >= fixedHeight + table.measuredHeight + minimumChart
            table.visibility = if (showTable) android.view.View.VISIBLE else android.view.View.GONE
            fixedHeight + minimumChart + if (showTable) table.measuredHeight else 0
        }
        val dock = TaskActionDock(this, edgeInsetDp = 0)
        body.addView(dock, LinearLayout.LayoutParams(-1, -2))
        val error = beanError(body)
        if (existing != null) {
            HoyiUi.label(this, decisions, getString(R.string.profiles_conflict), TaskVisualTokens.SECTION_SP, true).apply {
                tag = "import-conflict-notice"
                val inset = HoyiUi.dp(this@CurveImportPreviewActivity, TaskVisualTokens.CARD_PADDING_DP)
                setPadding(inset, inset, inset, inset)
                background = HoyiUi.shape(this@CurveImportPreviewActivity, R.color.mobile_warning_surface,
                    TaskVisualTokens.CARD_RADIUS_DP, R.color.mobile_warning_border)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = HoyiUi.dp(this@CurveImportPreviewActivity, TaskVisualTokens.GROUP_GAP_DP)
                }
            }
            HoyiUi.button(this, decisions, getString(R.string.profiles_existing)) { profilePreviewDialog(existing) }
            HoyiUi.button(this, decisions, getString(R.string.profiles_keep_existing)) { finish() }
            val copy = HoyiUi.button(this, dock, getString(R.string.profiles_save_copy), primary = true) {}
            copy.setOnClickListener {
                if (finishedSave) return@setOnClickListener
                copy.isEnabled = false
                if (runCatching { store.saveCopy(doc) }.isSuccess) { finishedSave = true; finish() }
                else { error.setText(R.string.profiles_save_failed); copy.isEnabled = true }
            }
        } else {
            val save = HoyiUi.button(this, dock, getString(R.string.profiles_import_save), primary = true) {}
            save.setOnClickListener {
                if (finishedSave) return@setOnClickListener
                save.isEnabled = false
                val result = runCatching { store.importDocument(doc) }
                result.onSuccess {
                    if (it.status == CustomCurveStore.ImportStatus.CONFLICT) render(it.document)
                    else { finishedSave = true; finish() }
                }.onFailure { error.setText(R.string.profiles_save_failed); save.isEnabled = true }
            }
        }
        HoyiUi.label(this, decisions, getString(R.string.profiles_compatibility), 14, muted = true).apply {
            setPadding(0, HoyiUi.dp(this@CurveImportPreviewActivity, 16), 0, 0)
        }
        HoyiUi.label(this, decisions, getString(if (CustomPressureCurveAdapter.profile(doc, false) != null)
            R.string.profiles_pressure_compatible else R.string.profiles_read_only), 14, true).apply {
            setPadding(0, HoyiUi.dp(this@CurveImportPreviewActivity, 16), 0, 0)
        }
        HoyiUi.button(this, decisions, getString(R.string.ui_full_details)) {
            beanDetails(doc.name, profileSummary(doc) + "\n\n" + doc.encode())
        }.apply {
            setTextColor(getColor(R.color.mobile_accent))
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            val states = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), HoyiUi.shape(this@CurveImportPreviewActivity,
                    android.R.color.transparent, 8, R.color.mobile_accent))
                addState(intArrayOf(), HoyiUi.shape(this@CurveImportPreviewActivity, android.R.color.transparent, 8))
            }
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_accent_soft)), states,
                HoyiUi.shape(this@CurveImportPreviewActivity, android.R.color.white, 8))
        }
    }
    private fun stageTable(doc: CustomCurveDocument) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, HoyiUi.dp(this@CurveImportPreviewActivity, 16), 0, 0)
        tag = "import-stage-table"
        fun row(values: List<String>, heading: Boolean, stageIndex: Int? = null) {
            val line = LinearLayout(this@CurveImportPreviewActivity).apply { orientation = LinearLayout.HORIZONTAL }
            addView(line, LinearLayout.LayoutParams(-1, -2))
            values.forEachIndexed { column, value ->
                HoyiUi.label(this@CurveImportPreviewActivity, line, value, 14, bold = heading, muted = heading).apply {
                    setPadding(HoyiUi.dp(this@CurveImportPreviewActivity, 8), HoyiUi.dp(this@CurveImportPreviewActivity, 4),
                        HoyiUi.dp(this@CurveImportPreviewActivity, 8), HoyiUi.dp(this@CurveImportPreviewActivity, 4))
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
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putBoolean("finishedSave", finishedSave) }
}
