package io.openhoyi.mobile

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.LinearLayout
import android.view.View
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
        val workspace = beanWorkspace(body).apply { tag = "task-inline-form" }
        val sources = BeanActionRows(this).also { workspace.addView(it) }
        val editor = beanWorkspace(workspace).apply { tag = "bean-full-field" }
        text = beanInput(editor, R.string.profiles_json).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(false); minLines = 1
            gravity = android.view.Gravity.TOP
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, 0, 1f)
            setText(savedInstanceState?.getString("text").orEmpty())
        }
        error = beanError(body)
        HoyiUi.button(this, sources, getString(R.string.profiles_paste)) {
            // Clipboard is read only after this explicit user action, never on resume/start.
            val value = runCatching {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this)?.toString()
            }.getOrNull()
            if (value == null || value.toByteArray(Charsets.UTF_8).size > CustomCurveDocument.MAX_BYTES) error.setText(R.string.profiles_import_invalid)
            else { text.setText(value); error.text = "" }
        }
        HoyiUi.button(this, sources, getString(R.string.profiles_open_file)) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain")), OPEN)
        }
        val footerRow = object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val wide = View.MeasureSpec.getSize(widthMeasureSpec) >= HoyiUi.dp(this@CurveImportActivity, 624)
                orientation = if (wide) HORIZONTAL else VERTICAL
                (getChildAt(0).layoutParams as LayoutParams).apply {
                    width = if (wide) 0 else -1
                    weight = if (wide) 1f else 0f
                    marginEnd = if (wide) HoyiUi.dp(this@CurveImportActivity, TaskVisualTokens.GROUP_GAP_DP) else 0
                }
                (getChildAt(1).layoutParams as LayoutParams).width =
                    if (wide) HoyiUi.dp(this@CurveImportActivity, 320) else -1
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply { gravity = android.view.Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        beanActions(body).addView(footerRow, LinearLayout.LayoutParams(-1, -2))
        HoyiUi.label(this, footerRow, getString(R.string.profiles_import_hint), TaskVisualTokens.SUPPORTING_SP, muted = true).apply {
            setPadding(0, HoyiUi.dp(this@CurveImportActivity, TaskVisualTokens.RELATED_GAP_DP), 0,
                HoyiUi.dp(this@CurveImportActivity, TaskVisualTokens.RELATED_GAP_DP))
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
        footerRow.addView(actions, LinearLayout.LayoutParams(-1, -2))
        HoyiUi.button(this, actions, getString(R.string.beans_cancel)) { finish() }.apply {
            asTaskTextAction()
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = HoyiUi.dp(this@CurveImportActivity, TaskVisualTokens.RELATED_GAP_DP)
            }
        }
        HoyiUi.button(this, actions, getString(R.string.profiles_import_preview), primary = true) {
            preview(text.text.toString())
        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 2f) }
        if (savedInstanceState?.containsKey("text") == true) text.post {
            if (isFinishing || isDestroyed) return@post
            if (savedInstanceState.getBoolean("textFocused")) text.requestFocus()
            text.setSelection(savedInstanceState.getInt("textStart").coerceIn(0, text.length()),
                savedInstanceState.getInt("textEnd").coerceIn(0, text.length()))
            text.scrollTo(savedInstanceState.getInt("textScrollX"), savedInstanceState.getInt("textScrollY"))
        }
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
            if (value.toByteArray(Charsets.UTF_8).size <= CustomCurveDocument.MAX_BYTES) {
                outState.putString("text", value)
                outState.putInt("textStart", text.selectionStart.coerceAtLeast(0))
                outState.putInt("textEnd", text.selectionEnd.coerceAtLeast(0))
                outState.putBoolean("textFocused", text.hasFocus())
                outState.putInt("textScrollX", text.scrollX)
                outState.putInt("textScrollY", text.scrollY)
            }
        }
    }
}
