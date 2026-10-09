package io.openhoyi.mobile

import android.os.Bundle

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
        profileCard(body, doc)
        val store = profileStore(body) ?: return
        val existing = conflicting ?: store.find(doc.id)?.takeIf { it != doc }
        val error = beanError(body)
        if (existing != null) {
            HoyiUi.label(this, body, getString(R.string.profiles_conflict), 17, true)
            HoyiUi.label(this, body, getString(R.string.profiles_existing), 18, true)
            profileCard(body, existing)
            HoyiUi.button(this, body, getString(R.string.profiles_keep_existing)) { finish() }
            val copy = HoyiUi.button(this, body, getString(R.string.profiles_save_copy), primary = true) {}
            copy.setOnClickListener {
                if (finishedSave) return@setOnClickListener
                copy.isEnabled = false
                if (runCatching { store.saveCopy(doc) }.isSuccess) { finishedSave = true; finish() }
                else { error.setText(R.string.profiles_save_failed); copy.isEnabled = true }
            }
        } else {
            val save = HoyiUi.button(this, body, getString(R.string.profiles_import_save), primary = true) {}
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
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putBoolean("finishedSave", finishedSave) }
}
