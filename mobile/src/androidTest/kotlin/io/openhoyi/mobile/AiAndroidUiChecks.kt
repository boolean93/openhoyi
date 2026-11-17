package io.openhoyi.mobile

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Disposable Mock UI only. Runner owns emulator authorization and process-wide HTTP sandbox. */
internal class AiAndroidUiChecks(private val test: Instrumentation, private val sandbox: AiAndroidHttpSandbox) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private val activities = CopyOnWriteArrayList<Activity>()
    private val resumed = AtomicReference<Activity?>()
    private var assertions = 0
    private var externalCapture = "not_attempted"
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) { if (activity is AiChatActivity || activity is AiKeyActivity) activities.add(activity) }
        override fun onActivityResumed(activity: Activity) { if (activity is AiChatActivity || activity is AiKeyActivity) resumed.set(activity) }
        override fun onActivityDestroyed(activity: Activity) { activities.remove(activity) }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    }

    fun run(id: String, languageTag: String, fontKey: String, dark: Boolean, exerciseFlow: Boolean): Int {
        check(BuildConfig.MOCK_MODE && id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) { "Invalid UI fixture" }
        sandbox.requireInstalled()
        val language = AppLanguage.entries.firstOrNull { it.tag == languageTag } ?: error("Invalid fixture language")
        val font = AppFontSize.entries.firstOrNull { it.key == fontKey } ?: error("Invalid fixture font")
        val keyStore = AiKeyStore(context)
        val alias = "io.openhoyi.mobile.deepseek_api_key.v1"
        val androidKeys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        // Never delete or replace an existing user's key, including an orphan alias.
        check(keyStore.status() == AiKeyStatus.MISSING && !androidKeys.containsAlias(alias)) { "UI fixture requires empty credentials" }
        val oldLanguage = app.languagePreferences.current
        val oldFont = app.fontSizePreferences.current
        val preferences = listOf("app_language" to "tag", "app_typography" to "size", "appearance" to "dark", "ai_consent" to "deepseek_v4_compact")
        val before = preferences.associateWith { (file, key) -> context.getSharedPreferences(file, Context.MODE_PRIVATE).all[key] }
        var ownsAlias = false
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            onMain {
                expect(app.languagePreferences.select(language) != AppLanguagePreference.Selection.SAVE_FAILED)
                expect(app.fontSizePreferences.select(font) != AppFontSizePreference.Selection.SAVE_FAILED)
                expect(context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putBoolean("dark", dark).commit())
                expect(context.getSharedPreferences("ai_consent", Context.MODE_PRIVATE).edit().remove("deepseek_v4_compact").commit())
            }
            ownsAlias = true // Alias was absent above; even a failed save may have generated this fixture's alias.
            expect(keyStore.save("fake-sentinel-ui-$id"))
            val keyActivity = launch(AiKeyActivity::class.java)
            onMain {
                val input = field<EditText>(keyActivity, "input")
                expect(input.text.isEmpty())
                expect(!input.isSaveEnabled && !input.isSaveFromParentEnabled)
                expect(input.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS)
                expect(!input.isLongClickable && !input.isTextSelectable)
                expect(keyActivity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                hideIme(keyActivity)
            }
            SystemClock.sleep(250)
            if (!dark) secureScreenshot(keyActivity)
            onMain {
                val input = field<EditText>(keyActivity, "input")
                input.setText("fake-sentinel-ui-$id")
                click(keyActivity, R.string.ai_key_save)
                expect(input.text.isEmpty())
                keyActivity.finish()
            }
            val chat = launch(AiChatActivity::class.java)
            onMain {
                expect(chat.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0)
                val memory = ViewModelProvider(chat)[AiChatViewState::class.java]
                repeat(8) {
                    memory.lines += AiChatLine(true, "Fixture coffee question $it")
                    memory.lines += AiChatLine(false, if (language == AppLanguage.CHINESE) "这是离线测试回复，验证长会话中的字号换行与输入操作。".repeat(12) else "Offline fixture answer to verify wrapping and accessible chat controls. ".repeat(12))
                }
                method(chat, "render")
                hideIme(chat)
            }
            SystemClock.sleep(250)
            onMain {
                val input = field<EditText>(chat, "input")
                val send = field<Button>(chat, "send")
                fullyVisibleTarget(send)
                fullyVisibleTarget(input)
                descendants(chat.window.decorView).filter { it.isShown && it.isClickable && it !is EditText }.forEach(::fullyVisibleTarget)
                val transcript = field<View>(chat, "transcript")
                expect(transcript.height > chat.window.decorView.height)
                descendants(chat.window.decorView).filterIsInstance<TextView>().filter { it.isShown && it !is EditText }.forEach(::uncroppedText)
                // This intentional in-app draw contains only synthetic chat; never draw the Key Activity.
                val root = chat.findViewById<View>(android.R.id.content)
                val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(image))
                val directory = File(context.filesDir, "ai-fixture-$id").apply { check(mkdirs() || isDirectory) }
                File(directory, "layout.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                image.recycle()
                fun rectOf(view: View): JSONArray = JSONArray().also { array ->
                    val rect = Rect()
                    check(view.getGlobalVisibleRect(rect))
                    listOf(rect.left, rect.top, rect.right, rect.bottom).forEach { array.put(it) }
                }
                // Geometry only. Never serialize views, UI text, input, preferences or credentials.
                val geometry = JSONObject().put("language", languageTag).put("font", fontKey).put("dark", dark)
                    .put("rootWidth", root.width).put("rootHeight", root.height)
                    .put("inputGlobalRect", rectOf(input)).put("sendGlobalRect", rectOf(send))
                    .put("secureFlag", chat.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                geometry.put("externalCapture", externalCapture)
                File(directory, "layout.json").writeText(geometry.toString())
            }
            newChat(chat)
            onMain {
                val memory = ViewModelProvider(chat)[AiChatViewState::class.java]
                expect(memory.lines.isEmpty() && memory.protocol.messages.isEmpty() && memory.draft.isEmpty())
                field<EditText>(chat, "input").setText("Fixture draft cleared by key change")
            }
            // Re-save only the synthetic credential owned by this empty-key fixture.
            expect(keyStore.save("fake-sentinel-ui-$id"))
            await { onMain {
                val memory = ViewModelProvider(chat)[AiChatViewState::class.java]
                memory.draft.isEmpty() && memory.submission.pending == null &&
                    field<EditText>(chat, "input").text.isEmpty()
            } }
            if (onMain { chat.resources.configuration.screenHeightDp < 400 }) {
                // Host enables the emulator's software IME even with a hardware keyboard attached.
                // Visibility is an observed prerequisite, not inferred from showSoftInput's return value.
                check(Build.VERSION.SDK_INT >= 30) { "IME fixture requires API 30" }
                onMain {
                    val input = field<EditText>(chat, "input")
                    input.requestFocus()
                    chat.getSystemService(InputMethodManager::class.java)
                        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
                await { onMain { chat.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true } }
                SystemClock.sleep(200)
                onMain {
                    val input = field<EditText>(chat, "input")
                    val send = field<Button>(chat, "send")
                    fullyVisibleTarget(input)
                    fullyVisibleTarget(send)
                    val available = Rect().also { chat.window.decorView.getWindowVisibleDisplayFrame(it) }
                    listOf(input, send).forEach { target ->
                        val rect = Rect().also { target.getGlobalVisibleRect(it) }
                        expect(available.contains(rect))
                    }
                    hideIme(chat)
                }
                await { onMain { chat.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false } }
            }
            if (exerciseFlow) flow(chat)
            return assertions
        } catch (_: Throwable) {
            throw IllegalStateException("AI Android UI checks failed")
        } finally {
            var cleanupFailed = false
            runCatching { onMain { activities.toList().forEach { it.finish() } } }.onFailure { cleanupFailed = true }
            app.unregisterActivityLifecycleCallbacks(callbacks)
            if (ownsAlias) {
                if (!keyStore.delete()) cleanupFailed = true
                runCatching { androidKeys.deleteEntry(alias) }.onFailure { cleanupFailed = true }
            }
            runCatching { onMain {
                check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED)
                check(app.fontSizePreferences.select(oldFont) != AppFontSizePreference.Selection.SAVE_FAILED)
                before.forEach { (location, value) ->
                    val editor = context.getSharedPreferences(location.first, Context.MODE_PRIVATE).edit()
                    when (value) { null -> editor.remove(location.second); is Boolean -> editor.putBoolean(location.second, value); is String -> editor.putString(location.second, value); else -> error("Invalid fixture restore") }
                    check(editor.commit())
                }
            } }.onFailure { cleanupFailed = true }
            check(!cleanupFailed) { "AI UI fixture cleanup failed" }
        }
    }

    private fun flow(chat: AiChatActivity) {
        val baseline = sandbox.requestCount
        onMain { field<EditText>(chat, "input").setText("Offline fixture question"); field<Button>(chat, "send").performClick() }
        onMain { field<AlertDialog>(chat, "consentDialog").getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
        expect(sandbox.requestCount == baseline)
        sandbox.enqueue(body = AiAndroidHttpSandbox.answer("Offline fixture answer"))
        onMain {
            field<Button>(chat, "send").performClick()
            field<AlertDialog>(chat, "consentDialog").getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        await { onMain { ViewModelProvider(chat)[AiChatViewState::class.java].lines.size == 2 } }
        expect(sandbox.requestCount == baseline + 1)
        val failed = sandbox.enqueue(status = 401, body = "fixture failure")
        onMain {
            val input = field<EditText>(chat, "input")
            input.setText("Failed fixture")
            field<Button>(chat, "send").performClick()
            expect(input.text.isEmpty())
            val state = ViewModelProvider(chat)[AiChatViewState::class.java]
            expect(state.submission.pending == "Failed fixture" && state.lines.size == 2)
        }
        await { onMain { ViewModelProvider(chat)[AiChatViewState::class.java].submission.pending == null } }
        onMain {
            expect(field<EditText>(chat, "input").text.toString() == "Failed fixture")
            expect(ViewModelProvider(chat)[AiChatViewState::class.java].lines.size == 2)
        }
        expect(!failed.errorRead.get())
        val blocked = sandbox.enqueue(blockedRead = true)
        onMain {
            val input = field<EditText>(chat, "input")
            input.setText("Stop fixture")
            field<Button>(chat, "send").performClick()
            expect(input.text.isEmpty())
            expect(ViewModelProvider(chat)[AiChatViewState::class.java].submission.pending == "Stop fixture")
        }
        expect(blocked.readStarted.await(4, TimeUnit.SECONDS))
        onMain { field<Button>(chat, "send").performClick() }
        await { blocked.disconnected.get() }
        onMain { expect(field<EditText>(chat, "input").text.toString() == "Stop fixture") }
        expect(sandbox.requestCount == baseline + 3)
        val rotating = sandbox.enqueue(blockedRead = true)
        val draft = "Rotation fixture draft"
        onMain {
            field<EditText>(chat, "input").apply { setText(draft); requestFocus(); setSelection(3, 8) }
            field<Button>(chat, "send").performClick()
            expect(field<EditText>(chat, "input").text.isEmpty())
        }
        expect(rotating.readStarted.await(4, TimeUnit.SECONDS))
        val originalOrientation = onMain { chat.requestedOrientation }
        onMain { chat.requestedOrientation = if (chat.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        await { resumed.get() is AiChatActivity && resumed.get() !== chat }
        await { rotating.disconnected.get() }
        val restored = resumed.get() as AiChatActivity
        onMain {
            val input = field<EditText>(restored, "input")
            expect(input.text.toString() == draft)
            expect(input.selectionStart == 3 && input.selectionEnd == 8)
            expect(ViewModelProvider(restored)[AiChatViewState::class.java].lines.size == 2)
        }
        newChat(restored)
        onMain {
            val memory = ViewModelProvider(restored)[AiChatViewState::class.java]
            expect(memory.lines.isEmpty() && memory.protocol.messages.isEmpty() && memory.draft.isEmpty())
            restored.requestedOrientation = originalOrientation
        }
        SystemClock.sleep(200)
        expect(sandbox.requestCount == baseline + 4)
    }

    private fun secureScreenshot(activity: Activity) {
        val rect = onMain { Rect().also { activity.window.decorView.getGlobalVisibleRect(it) } }
        val screenshot = test.uiAutomation.takeScreenshot()
        if (screenshot == null) { externalCapture = "unavailable"; assertions++; return }
        try {
            var total = 0; var black = 0
            for (y in rect.top + rect.height() / 4 until rect.bottom - rect.height() / 4 step 8)
                for (x in rect.left + rect.width() / 4 until rect.right - rect.width() / 4 step 8) {
                    val pixel = screenshot.getPixel(x.coerceIn(0, screenshot.width - 1), y.coerceIn(0, screenshot.height - 1))
                    total++; if ((pixel and 0x00ffffff) == 0) black++
                }
            expect(total > 0 && black.toDouble() / total >= .98)
            externalCapture = "blackened"
        } finally { screenshot.recycle() }
    }
    private fun fullyVisibleTarget(view: View) {
        val rect = Rect()
        expect(view.getGlobalVisibleRect(rect) && rect.width() == view.width && rect.height() == view.height)
        expect(rect.width() >= (48 * view.resources.displayMetrics.density).toInt() && rect.height() >= (48 * view.resources.displayMetrics.density).toInt())
    }
    private fun uncroppedText(view: TextView) {
        if (view.text.isEmpty()) return
        val layout = requireNotNull(view.layout)
        val availableWidth = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        expect(availableWidth > 0)
        for (line in 0 until layout.lineCount) expect(layout.getLineMax(line) <= availableWidth + 1)
        expect(view.height - view.compoundPaddingTop - view.compoundPaddingBottom >= layout.height)
        for (line in 0 until layout.lineCount) expect(layout.getEllipsisCount(line) == 0)
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun click(activity: Activity, text: Int) { descendants(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() == activity.getString(text) }.performClick() }
    private fun newChat(chat: AiChatActivity) {
        onMain {
            descendants(chat.window.decorView).filterIsInstance<Button>()
                .first { it.contentDescription == chat.getString(R.string.ai_options) }.performClick()
        }
        await { onMain {
            val popup = field<AiChatPresentation>(chat, "presentation").options!!
            popup.contentView.width > 0 && popup.contentView.height > 0
        } }
        onMain {
            val popup = field<AiChatPresentation>(chat, "presentation").options!!
            val buttons = descendants(popup.contentView).filterIsInstance<Button>()
            buttons.forEach { fullyVisibleTarget(it); uncroppedText(it) }
            buttons.first { it.text.toString() == chat.getString(R.string.ai_new) }.performClick()
        }
    }
    private fun hideIme(activity: Activity) { activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(activity.window.decorView.windowToken, 0); activity.currentFocus?.clearFocus() }
    private fun <T : Activity> launch(type: Class<T>): T = type.cast(test.startActivitySync(Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))!!.also { SystemClock.sleep(200) }
    private fun expect(value: Boolean) { check(value); assertions++ }
    private fun <T> onMain(action: () -> T): T = BoundedInstrumentationMain.run("AI UI fixture", action = action)
    @Suppress("UNCHECKED_CAST") private fun <T> field(owner: Any, name: String): T = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T
    private fun method(owner: Any, name: String) { owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner) }
    private fun await(condition: () -> Boolean) { val end = SystemClock.elapsedRealtime() + 5_000; while (!condition()) { check(SystemClock.elapsedRealtime() < end); SystemClock.sleep(25) } }
}
