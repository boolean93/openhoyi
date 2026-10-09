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
        profileCard(body, doc)
        val json = doc.encode()
        HoyiUi.button(this, body, getString(R.string.profiles_copy)) {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(getString(R.string.profiles_json), json))
            Toast.makeText(this, R.string.profiles_copied, Toast.LENGTH_SHORT).show()
        }
        HoyiUi.button(this, body, getString(R.string.profiles_share_text), primary = true) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, json), getString(R.string.profiles_share_title)))
        }
        HoyiUi.button(this, body, getString(R.string.profiles_export_file)) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "openhoyi-${doc.id}.json"), EXPORT)
        }
        shareFile = HoyiUi.button(this, body, getString(R.string.profiles_share_file)) {
            exportedUri?.let { uri ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                    .apply { clipData = ClipData.newUri(contentResolver, getString(R.string.profiles_json), uri) }
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), getString(R.string.profiles_share_title)))
            }
        }.apply { isEnabled = exportedUri != null }
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
    val card = HoyiUi.card(this, body, doc.name)
    HoyiUi.label(this, card, getString(R.string.profiles_read_only), 15, true)
    if (doc.controlMode == CustomCurveDocument.ControlMode.PRESSURE) {
        card.addView(CurveStageView(this).apply { targets = doc.stages.map { it.target } }, LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 160)))
        HoyiUi.label(this, card, getString(R.string.profiles_stage_caption), 13, muted = true)
    } else HoyiUi.label(this, card, getString(R.string.profiles_flow_raw), 15, muted = true)
    HoyiUi.label(this, card, profileSummary(doc), 16).setPadding(0, HoyiUi.dp(this, 12), 0, 0)
}
