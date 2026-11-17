package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import org.json.JSONObject
import java.io.File

/** Separate branch of the Mock runner; only views with synthetic content are read. */
internal class AiChatPreviewChecks(private val test: Instrumentation) {
    fun run(arguments: Bundle) {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        check(arguments.getString("aiPreviewOnly") == "true" && arguments.getString("aiSafetyChecks") != "true")
        if (arguments.getString("aiCardConstructionOnly") == "true") {
            AiCurveCardConstructionChecks.run(test)
            return
        }
        val language = arguments.getString("previewLanguage") ?: "zh-Hans"
        val font = arguments.getString("previewFont") ?: "medium"
        val dark = arguments.getString("previewDark") ?: "false"
        check(language in setOf("zh-Hans", "en") && font in setOf("small", "medium", "large") && dark in setOf("true", "false"))
        val id = requireNotNull(arguments.getString("previewId"))
        check(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        val expected = AppLanguageContext.wrap(test.targetContext,
            if (language == "en") AppLanguage.ENGLISH else AppLanguage.CHINESE)
        val expectedSend = expected.getString(R.string.ai_send)
        val expectedOptions = expected.getString(R.string.ai_options)
        val directory = File(test.targetContext.filesDir, "ai-preview-fixture-$id")
        check(!directory.exists() && directory.mkdir())
        val activity = test.startActivitySync(Intent(test.targetContext, AiChatPreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("previewLanguage", language)
            .putExtra("previewFont", font)
            .putExtra("previewDark", dark == "true")) as AiChatPreviewActivity
        try {
            test.waitForIdleSync()
            lateinit var menu: Button
            test.runOnMainSync {
                val root = activity.window.decorView
                val all = children(root)
                val input = all.filterIsInstance<EditText>().single()
                val editor = EditorInfo()
                input.onCreateInputConnection(editor)
                check(editor.imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEND)
                check(editor.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0)
                val send = all.filterIsInstance<Button>().single { it.contentDescription == expectedSend }
                menu = all.filterIsInstance<Button>().single { it.contentDescription == expectedOptions }
                check(input.text.isEmpty())
                fullyVisible(input); fullyVisible(send); fullyVisible(menu)
                val bubbles = all.filterIsInstance<ViewGroup>()
                    .filter { it.tag == "ai/chat/user" || it.tag == "ai/chat/assistant" }
                check(bubbles.size == 2)
                bubbles.forEach { row ->
                    val message = row.getChildAt(0) as TextView
                    uncropped(message)
                    check(message.width <= (row.width * .78f).toInt() + 1)
                }
                val assistant = bubbles.last().getChildAt(0) as TextView
                val styled = assistant.text as Spanned
                check(!styled.contains('|'))
                check(styled.contains(if (language == "en") "Parameter: Time" else "参数: 时间"))
                check(styled.getSpans(0, styled.length, StyleSpan::class.java).isNotEmpty())
                check(styled.getSpans(0, styled.length, TypefaceSpan::class.java).isNotEmpty())
                saveView(root, File(directory, "layout.png"))
                val geometry = JSONObject().put("language", language).put("font", font).put("dark", dark == "true")
                    .put("rootWidth", root.width).put("rootHeight", root.height)
                    .put("input", rect(input)).put("send", rect(send)).put("options", rect(menu))
                File(directory, "layout.json").writeText(geometry.toString())
                menu.performClick()
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                val popup = requireNotNull(activity.optionsForCheck())
                val buttons = children(popup.contentView).filterIsInstance<Button>()
                check(buttons.size == 3) // New chat, local usage sample, disabled real Key.
                buttons.forEach { fullyVisible(it); uncropped(it) }
                check(buttons.last().isEnabled.not()) // Preview cannot navigate to the real Key screen.
                saveView(popup.contentView, File(directory, "options.png"))
                File(directory, "options.json").writeText(JSONObject()
                    .put("newChat", rect(buttons.first())).put("keyDisabled", true).toString())
                popup.dismiss()
                val input = children(activity.window.decorView).filterIsInstance<EditText>().single()
                input.setText("offline fixture")
                input.onEditorAction(EditorInfo.IME_ACTION_SEND)
                check(input.text.isEmpty())
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                fun bubbleCount() = children(activity.window.decorView).count {
                    it is ViewGroup && (it.tag == "ai/chat/user" || it.tag == "ai/chat/assistant")
                }
                check(bubbleCount() == 4)
                val input = children(activity.window.decorView).filterIsInstance<EditText>().single()
                input.requestFocus()
                input.setText("hardware fixture")
                val time = SystemClock.uptimeMillis()
                input.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
                input.setText("duplicate fixture")
                input.dispatchKeyEvent(KeyEvent(time, time + 1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
                input.dispatchKeyEvent(KeyEvent(time, time + 1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 1))
                input.dispatchKeyEvent(KeyEvent(time, time + 2, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
                check(bubbleCount() == 6 && input.text.toString() == "duplicate fixture")
                val shiftTime = time + 10
                input.dispatchKeyEvent(KeyEvent(shiftTime, shiftTime, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_ENTER, 0, KeyEvent.META_SHIFT_ON))
                input.dispatchKeyEvent(KeyEvent(shiftTime, shiftTime + 1, KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_ENTER, 0, KeyEvent.META_SHIFT_ON))
                check(bubbleCount() == 6 && input.text.contains('\n'))
                input.setText("composing")
                BaseInputConnection.setComposingSpans(input.text)
                input.dispatchKeyEvent(KeyEvent(time + 20, time + 20, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
                check(bubbleCount() == 6)
                BaseInputConnection.removeComposingSpans(input.text)
                activity.busyForCheck(true)
                input.onEditorAction(EditorInfo.IME_ACTION_SEND)
                check(bubbleCount() == 7) // Only the pending display bubble, never a new request or Stop.
                activity.busyForCheck(false)
            }
        } finally {
            activity.runOnUiThread { activity.finish() }
        }
    }
    private fun children(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
    private fun fullyVisible(view: View) {
        val bounds = Rect()
        check(view.getGlobalVisibleRect(bounds) && bounds.width() == view.width && bounds.height() == view.height)
        val minimum = (48 * view.resources.displayMetrics.density).toInt()
        check(bounds.width() >= minimum && bounds.height() >= minimum)
    }
    private fun uncropped(view: TextView) {
        val layout = requireNotNull(view.layout)
        val width = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        check(width > 0 && (0 until layout.lineCount).all { layout.getLineMax(it) <= width + 1 && layout.getEllipsisCount(it) == 0 })
        check(view.height - view.compoundPaddingTop - view.compoundPaddingBottom >= layout.height)
    }
    private fun rect(view: View) = Rect().also { check(view.getGlobalVisibleRect(it)) }.let {
        org.json.JSONArray(listOf(it.left, it.top, it.right, it.bottom))
    }
    private fun saveView(view: View, file: File) {
        check(view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
}
