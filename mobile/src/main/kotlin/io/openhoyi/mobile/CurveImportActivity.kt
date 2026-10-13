package io.openhoyi.mobile

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class CurveImportActivity : ThemedActivity() {
    companion object { private const val OPEN = 62 }
    private lateinit var text: EditText
    private lateinit var error: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = beanPage(getString(R.string.profiles_import), getString(R.string.profiles_import_hint))
        profileStore(body) ?: return
        text = beanInput(HoyiUi.card(this, body), R.string.profiles_json).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(false); minLines = 6; maxLines = 14
            setText(savedInstanceState?.getString("text").orEmpty())
        }
        error = beanError(body)
        HoyiUi.button(this, body, getString(R.string.profiles_paste)) {
            // Clipboard is read only after this explicit user action, never on resume/start.
            val value = runCatching {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this)?.toString()
            }.getOrNull()
            if (value == null || value.toByteArray(Charsets.UTF_8).size > CustomCurveDocument.MAX_BYTES) error.setText(R.string.profiles_import_invalid)
            else { text.setText(value); error.text = "" }
        }
        HoyiUi.button(this, body, getString(R.string.profiles_open_file)) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain")), OPEN)
        }
        HoyiUi.button(this, body, getString(R.string.profiles_import_preview), primary = true) { preview(text.text.toString()) }
    }
    private fun preview(value: String) {
        if (runCatching { CustomCurveDocument.decode(value) }.isFailure) { error.setText(R.string.profiles_import_invalid); return }
        startActivity(Intent(this, CurveImportPreviewActivity::class.java).putExtra(CurveImportPreviewActivity.DOCUMENT, value))
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != OPEN || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Thread({
            val result = runCatching {
                val input = contentResolver.openInputStream(uri) ?: error("No document input")
                val bytes = ByteArrayOutputStream()
                input.use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(bytes.size() + count <= CustomCurveDocument.MAX_BYTES)
                        bytes.write(buffer, 0, count)
                    }
                }
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { text.setText(it); preview(it) }.onFailure { error.setText(R.string.profiles_import_invalid) }
            }
        }, "curve-document-import").start()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::text.isInitialized) {
            val value = text.text.toString()
            if (value.toByteArray(Charsets.UTF_8).size <= CustomCurveDocument.MAX_BYTES) outState.putString("text", value)
        }
    }
}
