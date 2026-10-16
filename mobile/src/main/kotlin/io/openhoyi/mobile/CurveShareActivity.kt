package io.openhoyi.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class CurveShareActivity : ThemedActivity() {
    companion object { const val CURVE_ID = "curveId"; private const val EXPORT = 61 }
    private var document: CustomCurveDocument? = null
    private var exportedUri: Uri? = null
    private lateinit var shareFile: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = beanPage(getString(R.string.profiles_share_title), getString(R.string.profiles_text_hint))
        val doc = runCatching {
            savedInstanceState?.getString("document")?.let(CustomCurveDocument::decode)
                ?: profileDocument(intent.getStringExtra(CURVE_ID))
        }.getOrNull()
        if (doc == null) { HoyiUi.label(this, body, getString(R.string.profiles_missing), 17); return }
        document = doc
        exportedUri = savedInstanceState?.getString("exportedUri")?.let(Uri::parse)
        val workspace = TaskWorkspaceLayout(this, supportingWidthFraction = .38f, supportingMaxWidthDp = 440).apply {
            fillSecondaryViewport = true
            listOf(primaryBody, secondaryBody).forEach { pane ->
                val inset = HoyiUi.dp(this@CurveShareActivity, 16)
                pane.setPadding(inset, inset, inset, inset)
                pane.background = HoyiUi.shape(this@CurveShareActivity, R.color.mobile_surface, 12, R.color.mobile_border)
            }
        }
        body.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = HoyiUi.dp(this@CurveShareActivity, 16)
        })
        sharePreview(workspace, doc)
        val actions = workspace.secondaryBody
        HoyiUi.label(this, actions, getString(R.string.profiles_share_methods), 20, true).apply {
            setPadding(0, 0, 0, HoyiUi.dp(this@CurveShareActivity, 16))
        }
        val json = doc.encode()
        HoyiUi.button(this, actions, getString(R.string.profiles_share_text), primary = true) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, json), getString(R.string.profiles_share_title)))
        }
        HoyiUi.button(this, actions, getString(R.string.profiles_copy)) {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(getString(R.string.profiles_json), json))
            Toast.makeText(this, R.string.profiles_copied, Toast.LENGTH_SHORT).show()
        }
        android.view.View(this).also { divider ->
            divider.setBackgroundColor(getColor(R.color.mobile_border))
            actions.addView(divider, LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 1)).apply {
                topMargin = HoyiUi.dp(this@CurveShareActivity, 16)
                bottomMargin = HoyiUi.dp(this@CurveShareActivity, 16)
            })
        }
        HoyiUi.button(this, actions, getString(R.string.profiles_export_file)) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "openhoyi-${doc.id}.json"), EXPORT)
        }.asTaskTextAction()
        shareFile = HoyiUi.button(this, actions, getString(R.string.profiles_share_file)) {
            exportedUri?.let { uri ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                    .apply { clipData = ClipData.newUri(contentResolver, getString(R.string.profiles_json), uri) }
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), getString(R.string.profiles_share_title)))
            }
        }.apply { isEnabled = exportedUri != null }
        HoyiUi.label(this, actions, getString(R.string.profiles_export_first), 14, muted = true)
        actions.addView(android.view.View(this), LinearLayout.LayoutParams(1, 0, 1f))
        HoyiUi.label(this, actions, getString(R.string.profiles_text_hint), 14, muted = true).apply {
            setPadding(0, HoyiUi.dp(this@CurveShareActivity, 24), 0, 0)
        }
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != EXPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val json = document?.encode() ?: return
        Thread({
            val result = runCatching {
                val output = contentResolver.openOutputStream(uri) ?: error("No document output")
                output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.isSuccess) { exportedUri = uri; shareFile.isEnabled = true }
                Toast.makeText(this, if (result.isSuccess) R.string.profiles_export_success else R.string.profiles_export_failed, Toast.LENGTH_LONG).show()
            }
        }, "curve-document-export").start()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        document?.let { outState.putString("document", it.encode()) }
        outState.putString("exportedUri", exportedUri?.toString())
    }
}

internal fun ThemedActivity.profileStore(body: LinearLayout): CustomCurveStore? = (application as MobileApplication).customCurvesResult.getOrNull().also {
    if (it == null) HoyiUi.label(this, HoyiUi.card(this, body), getString(R.string.profiles_unavailable), 17, true)
}
internal fun ThemedActivity.profileDocument(id: String?): CustomCurveDocument? {
    if (id == null) return null
    val app = application as MobileApplication
    if (id.startsWith("draft-")) return app.customCurvesResult.getOrThrow().find(id)
    return app.curves.find(id)?.let { CustomCurveDocument.fromLibraryItem(it) }
}
internal fun ThemedActivity.profileSummary(doc: CustomCurveDocument): String = buildString {
    append(getString(R.string.profiles_summary, doc.temperatureC, CurveDraftNumber.format(doc.targetHundredthsGram, 2), doc.stages.size))
    doc.stages.forEachIndexed { index, stage ->
        append('\n')
        append(if (doc.controlMode == CustomCurveDocument.ControlMode.PRESSURE)
            getString(R.string.profiles_stage_pressure_summary, index + 1, CurveDraftNumber.format(stage.target, 1), CurveDraftNumber.format(stage.waterTenthsMl, 1))
        else getString(R.string.profiles_stage_raw_summary, index + 1, stage.target, CurveDraftNumber.format(stage.waterTenthsMl, 1)))
    }
}
internal fun ThemedActivity.profileCard(body: LinearLayout, doc: CustomCurveDocument) {
    val card = HoyiUi.card(this, body)
    card.setPadding(HoyiUi.dp(this, 10), HoyiUi.dp(this, 8), HoyiUi.dp(this, 10), HoyiUi.dp(this, 8))
    card.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = HoyiUi.dp(this@profileCard, 6) }
    val workspace = TaskWorkspaceLayout(this)
    card.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
    workspace.onPrimarySpaceAvailable = { _, budget -> minOf(budget, HoyiUi.dp(this, 240)) }
    val primary = workspace.primaryBody
    val support = workspace.secondaryBody
    HoyiUi.label(this, primary, doc.name, 20, true).apply {
        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        tag = "full-text-details"
    }
    HoyiUi.label(this, primary, getString(if (CustomPressureCurveAdapter.profile(doc, false) != null) R.string.profiles_pressure_compatible else R.string.profiles_read_only), 14, true)
    if (doc.controlMode == CustomCurveDocument.ControlMode.PRESSURE) {
        primary.addView(CurveStageView(this).apply { targets = doc.stages.map { it.target } }, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.label(this, primary, getString(R.string.profiles_stage_caption), 14, muted = true)
    } else {
        HoyiUi.label(this, primary, getString(R.string.profiles_flow_raw), 15, muted = true)
    }
    HoyiUi.label(this, support, profileSummary(doc), 15)
    HoyiUi.button(this, support, getString(R.string.ui_full_details)) { beanDetails(doc.name, profileSummary(doc) + "\n\n" + doc.encode()) }
}

internal fun ThemedActivity.profilePreviewDialog(doc: CustomCurveDocument) {
    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; minimumHeight = (resources.displayMetrics.heightPixels * 0.55f).toInt() }
    profileCard(content, doc)
    val dialog = android.app.AlertDialog.Builder(this).setTitle(R.string.profiles_preview).setView(content)
        .setPositiveButton(android.R.string.ok, null).create()
    dialog.setOnShowListener {
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), (resources.displayMetrics.heightPixels * 0.78f).toInt())
    }
    beanShowDialog(dialog)
}
