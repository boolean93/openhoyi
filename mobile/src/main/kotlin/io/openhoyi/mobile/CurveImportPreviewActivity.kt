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
        val workspace = TaskWorkspaceLayout(this)
        body.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = HoyiUi.dp(this@CurveImportPreviewActivity, 16) })
        val preview = workspace.primaryBody
        val decisions = workspace.secondaryBody
        HoyiUi.label(this, preview, doc.name, 24, true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            tag = "full-text-details"
        }
        HoyiUi.label(this, preview, getString(if (CustomPressureCurveAdapter.profile(doc, false) != null)
            R.string.profiles_pressure_compatible else R.string.profiles_read_only), 14, true)
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
        if (doc.controlMode == CustomCurveDocument.ControlMode.PRESSURE) {
            preview.addView(CurveStageView(this).apply { targets = doc.stages.map { it.target } }, LinearLayout.LayoutParams(-1, 0, 1f))
            HoyiUi.label(this, preview, getString(R.string.profiles_stage_caption), 14, muted = true)
        } else HoyiUi.label(this, preview, getString(R.string.profiles_flow_raw), 15, muted = true)
        HoyiUi.button(this, preview, getString(R.string.ui_full_details)) {
            beanDetails(doc.name, profileSummary(doc) + "\n\n" + doc.encode())
        }
        val dock = TaskActionDock(this, edgeInsetDp = 0)
        body.addView(dock, LinearLayout.LayoutParams(-1, -2))
        val error = beanError(body)
        if (existing != null) {
            HoyiUi.label(this, decisions, getString(R.string.profiles_conflict), 20, true).apply {
                setPadding(0, 0, 0, HoyiUi.dp(this@CurveImportPreviewActivity, 16))
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
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putBoolean("finishedSave", finishedSave) }
}
