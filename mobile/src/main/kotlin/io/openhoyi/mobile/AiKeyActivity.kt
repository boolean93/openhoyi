package io.openhoyi.mobile

import android.content.ClipboardManager
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView

/** A write-only credential form. Saved values never enter this Activity. */
class AiKeyActivity : ThemedActivity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private val store by lazy { AiKeyStore(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        val visual = SettingsVisualStyle(this)
        val style = visual.palette
        val body = visual.page(this, R.string.ai_key_title, R.string.ai_key_help, 640, "ai-key")
        status = style.text("", TaskProductVisualStyle.body).also { style.add(body, it, 24) }
        input = EditText(this).apply {
            hint = getString(R.string.ai_key_input)
            contentDescription = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.LengthFilter(512))
            isSaveEnabled = false; isSaveFromParentEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isLongClickable = false; setTextIsSelectable(false)
            val noSelection = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
                override fun onDestroyActionMode(mode: ActionMode?) = Unit
            }
            customSelectionActionModeCallback = noSelection
            customInsertionActionModeCallback = noSelection
            visual.input(this)
        }
        style.add(body, input, 16)
        HoyiUi.button(this, body, getString(R.string.ai_key_paste)) {
            // Read text only after this explicit gesture; never coerce a clipboard URI or write it back.
            val value = runCatching {
                getSystemService(ClipboardManager::class.java).primaryClip
                    ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            }.getOrNull()
            if (value == null || !AiKeyCodec.valid(value)) {
                status.setText(R.string.ai_key_invalid)
            } else {
                input.setText(value)
                input.setSelection(input.length())
                input.requestFocus()
                refresh()
            }
        }.also { visual.action(it, outline = true); it.setSingleLine(false); it.maxLines = Int.MAX_VALUE; it.ellipsize = null }
        HoyiUi.button(this, body, getString(R.string.ai_key_save)) {
            val value = input.text.toString()
            if (!AiKeyCodec.valid(value)) { status.setText(R.string.ai_key_invalid); return@button }
            val saved = store.save(value)
            input.text.clear()
            status.setText(if (saved) R.string.ai_key_saved else R.string.ai_key_failed)
        }.also { visual.action(it); it.setSingleLine(false); it.maxLines = Int.MAX_VALUE; it.ellipsize = null }
        HoyiUi.button(this, body, getString(R.string.ai_key_delete)) {
            input.text.clear()
            status.setText(if (store.delete()) R.string.ai_key_missing else R.string.ai_key_failed)
        }.also { visual.action(it, outline = true); it.setSingleLine(false); it.maxLines = Int.MAX_VALUE; it.ellipsize = null }
        refresh()
    }
    private fun refresh() { status.setText(when (store.status()) {
        AiKeyStatus.MISSING -> R.string.ai_key_missing
        AiKeyStatus.SAVED -> R.string.ai_key_saved
        AiKeyStatus.UNAVAILABLE -> R.string.ai_key_unavailable
    }) }
    override fun onPause() {
        if (::input.isInitialized) input.text.clear()
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::input.isInitialized) input.text.clear()
        super.onSaveInstanceState(outState)
    }
}
