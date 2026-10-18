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
        val style = TaskProductFormStyle(this)
        val body = style.visual.page(this, getString(R.string.profiles_import), getString(R.string.profiles_import_hint))
        profileStore(body) ?: return
        val workspace = beanWorkspace(body).apply { tag = "task-inline-form" }
        style.visual.add(workspace, style.visual.text(getString(R.string.profiles_import_intro), TaskProductVisualStyle.body))
        val sources = style.visual.row("product/curve-import/sources").also {
            workspace.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(16) })
        }
        val editor = beanWorkspace(workspace).apply { tag = "bean-full-field" }
        text = beanInput(editor, R.string.profiles_json).apply {
            setHint(R.string.profiles_import_placeholder)
            style.input(this, "curve-import", "raw")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(false); minLines = 1
            gravity = android.view.Gravity.TOP
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, 0, 1f)
            setText(savedInstanceState?.getString("text").orEmpty())
        }
        val dataLabel = (0 until editor.childCount).map(editor::getChildAt).filterIsInstance<TextView>().single { it.labelFor == text.id }
        style.visual.apply(dataLabel, TaskProductVisualStyle.section); dataLabel.setPadding(0, 0, 0, 0)
        editor.removeView(dataLabel)
        val format = style.visual.text(getString(R.string.profiles_compatibility), TaskProductVisualStyle.auxiliary,
            "product/curve-import/format", true)
        val dataHeader = object : LinearLayout(this) {
            init { isBaselineAligned = false; gravity = android.view.Gravity.CENTER_VERTICAL; addView(dataLabel); addView(format) }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                dataLabel.measure(natural, natural); format.measure(natural, natural)
                val inline = dataLabel.measuredWidth + format.measuredWidth + style.dp(16) <= View.MeasureSpec.getSize(widthMeasureSpec)
                orientation = if (inline) HORIZONTAL else VERTICAL
                val direction = this.layoutDirection
                (dataLabel.layoutParams as LayoutParams).apply { width = if (inline) -2 else -1; weight = 0f }
                (format.layoutParams as LayoutParams).apply {
                    width = if (inline) 0 else -1; weight = if (inline) 1f else 0f
                    marginStart = if (inline) style.dp(16) else 0; marginEnd = 0
                    topMargin = if (inline) 0 else style.dp(8); resolveLayoutDirection(direction)
                }
                format.gravity = if (inline) android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.START
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        editor.addView(dataHeader, 0, LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(16); bottomMargin = style.dp(8) })
        error = style.error(beanError(body))
        HoyiUi.button(this, sources, getString(R.string.profiles_paste)) {
            // Clipboard is read only after this explicit user action, never on resume/start.
            val value = runCatching {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this)?.toString()
            }.getOrNull()
            if (value == null || value.toByteArray(Charsets.UTF_8).size > CustomCurveDocument.MAX_BYTES) error.setText(R.string.profiles_import_invalid)
            else { text.setText(value); error.text = "" }
        }.apply {
            style.visual.button(this); HomeVisualIcon.buttonIcon(this, "copy"); tag = "product/curve-import/paste"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = style.dp(8) }
        }
        HoyiUi.button(this, sources, getString(R.string.profiles_open_file)) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain")), OPEN)
        }.apply {
            style.visual.button(this); HomeVisualIcon.buttonIcon(this, "export"); tag = "product/curve-import/open-file"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = style.dp(8) }
        }
        val actions = style.visual.row("product/curve-import/actions")
        style.visual.action(actions, getString(R.string.beans_cancel), "product/curve-import/cancel", top = 0) { finish() }
            .layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = style.dp(8) }
        val previewAction = style.visual.action(actions, getString(R.string.profiles_import_preview), "product/curve-import/preview",
            primary = true, top = 0) { preview(text.text.toString()) }.apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2)
            isEnabled = this@CurveImportActivity.text.text.isNotEmpty()
        }
        text.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                // Only empty input is unavailable. Every nonempty value still uses the original decode on click.
                previewAction.isEnabled = !value.isNullOrEmpty()
            }
            override fun afterTextChanged(value: android.text.Editable?) = Unit
        })
        beanActions(body).addView(style.footer(style.visual.text(getString(R.string.curve_visual_import_scope),
            TaskProductVisualStyle.auxiliary, "product/curve-import/scope", true), actions, "curve-import", topRule = true),
            LinearLayout.LayoutParams(-1, -2))
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
