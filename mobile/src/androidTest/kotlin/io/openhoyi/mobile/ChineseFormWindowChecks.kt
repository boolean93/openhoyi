package io.openhoyi.mobile

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.MotionEvent
import android.view.InputDevice
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import java.io.File

/** Chinese Mock-window evidence only. Never saves a draft or invokes a machine/stock action. */
internal object ChineseFormWindowChecks {
    fun run(test: Instrumentation, activity: Activity, directory: File, name: String,
        waitForDraw: () -> Unit): Int {
        check(BuildConfig.MOCK_MODE && activity.packageName == "io.openhoyi.mobile.mock")
        check((activity.application as MobileApplication).languagePreferences.current == AppLanguage.CHINESE)
        if (activity !is BeanEntryActivity && activity !is CurveEditorActivity && activity !is BrewReviewActivity) return 0
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
            failure?.let { throw it }
        }
        fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        lateinit var sections: Spinner
        var originalSection = 0
        var stageSelector: Spinner? = null
        var originalStages = 0
        main {
            sections = descendants(content).filterIsInstance<Spinner>().single { it.tag == "bean-sections" }
            originalSection = sections.selectedItemPosition
        }
        val evidence = File(directory, "chinese-form-states").apply { check(mkdirs() || isDirectory) }
        var allStatesChecked = false
        var completed = false
        var screenshots = 0
        var sectionCount = 0
        var longStates = 0
        var editorObserved = false
        var imeObserved = false
        val gaps = mutableListOf<String>()
        fun capture(suffix: String) {
            val bitmap = requireNotNull(test.uiAutomation.takeScreenshot()) { "$name/$suffix screenshot missing" }
            try {
                File(evidence, "$name-$suffix.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "$name/$suffix screenshot write failed" }
                }
                screenshots++
            } finally { bitmap.recycle() }
        }
        fun fullyVisible(view: View) {
            val visible = Rect()
            check(view.isShown && view.width > 0 && view.height > 0 && view.getGlobalVisibleRect(visible) &&
                visible.width() == view.width && visible.height() == view.height) {
                "$name native editor/control clipped: ${view.javaClass.simpleName} ${view.width}x${view.height} visible=$visible"
            }
        }
        fun tap(field: EditText) {
            val bounds = Rect()
            main { check(field.isEnabled); fullyVisible(field); field.getGlobalVisibleRect(bounds) }
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            val up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try {
                down.source = InputDevice.SOURCE_TOUCHSCREEN; up.source = InputDevice.SOURCE_TOUCHSCREEN
                test.sendPointerSync(down); test.sendPointerSync(up)
            } finally { down.recycle(); up.recycle() }
            test.waitForIdleSync()
        }
        fun longEditor(field: EditText, value: String) {
            check(field.isEnabled && field.text.toString() == value) { "$name long draft lost characters" }
            fullyVisible(field)
            check(requireNotNull(field.layout).height > field.height - field.compoundPaddingTop - field.compoundPaddingBottom)
            check(field.canScrollVertically(-1) || field.canScrollVertically(1)) { "$name long draft cannot scroll locally" }
            descendants(content).filterIsInstance<Button>().filter { it.isShown }.forEach {
                fullyVisible(it)
                check(it.height >= HoyiUi.dp(activity, 48)) { "$name fixed action too small" }
            }
        }
        fun keyboardBounds(): Rect? {
            val windows = test.uiAutomation.windows
            try {
                return windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                    ?.let { window -> Rect().also { window.getBoundsInScreen(it) } }?.takeUnless { it.isEmpty }
            } finally { windows.forEach { it.recycle() } }
        }
        fun awaitKeyboard(): Rect? {
            val deadline = SystemClock.elapsedRealtime() + 3000
            do {
                keyboardBounds()?.let { return it }
                Thread.sleep(50)
            } while (SystemClock.elapsedRealtime() < deadline)
            return null
        }
        val automation = test.uiAutomation
        val accessibility = requireNotNull(automation.serviceInfo)
        val originalFlags = accessibility.flags
        try {
            accessibility.flags = originalFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = accessibility
            test.waitForIdleSync()
            if (activity is CurveEditorActivity) {
                // Expose all four stages by an observed local draft selector; no save action.
                main { sections.setSelection(1) }
                waitForDraw()
                main {
                    stageSelector = descendants(content).filterIsInstance<Spinner>().single {
                        it.isShown && it !== sections && it.contentDescription?.toString() == activity.getString(R.string.profiles_stage_count)
                    }
                    originalStages = requireNotNull(stageSelector).selectedItemPosition
                    requireNotNull(stageSelector).setSelection(3)
                }
                waitForDraw()
            }
            main { sectionCount = sections.count }
            for (position in 0 until sectionCount) {
                main { sections.setSelection(position) }
                waitForDraw()
                main { check(sections.selectedItemPosition == position); SingleScreenPageChecks.verify(activity) }
                capture("section-$position")
                if (activity is BrewReviewActivity && position > 0) {
                    lateinit var field: EditText
                    var original = ""
                    var selection = 0
                    var originalScroll = 0
                    val longText = (1..64).joinToString("\n") { index ->
                        "第${index}段：这杯咖啡入口有柑橘与蜂蜜的香气，后段口感柔和。下次保持相同粉量，比较研磨、温度与压力阶段的差异，并记录完整的观察。"
                    }
                    main {
                        field = descendants(content).filterIsInstance<EditText>().single { it.isShown }
                        check(field.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
                        original = field.text.toString(); selection = field.selectionStart; originalScroll = field.scrollY
                        field.setText(longText)
                    }
                    try {
                        waitForDraw()
                        main { longEditor(field, longText) }
                        capture("section-$position-long-top")
                        main { field.setSelection(longText.length); field.bringPointIntoView(longText.length) }
                        waitForDraw()
                        main { longEditor(field, longText); check(field.scrollY > 0) }
                        capture("section-$position-long-end")
                        longStates += 2
                        if (position == 1) {
                            tap(field)
                            val keyboard = awaitKeyboard()
                            waitForDraw()
                            if (keyboard == null) gaps += "long-editor IME not observed; no IME screenshot claimed"
                            else {
                                main {
                                    longEditor(field, longText)
                                    val bounds = Rect(); field.getGlobalVisibleRect(bounds)
                                    check(!Rect.intersects(bounds, keyboard)) { "$name long editor hidden by IME" }
                                }
                                capture("section-$position-long-ime")
                                imeObserved = true
                            }
                        }
                    } finally {
                        main {
                            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
                            field.clearFocus(); content.requestFocus()
                            field.setText(original); field.setSelection(selection.coerceIn(0, original.length)); field.scrollTo(0, originalScroll)
                        }
                        waitForDraw()
                        main { SingleScreenPageChecks.verify(activity) }
                    }
                }
            }
            // Only click the Mock page's observed short EditText; the editor's OK cannot save the page.
            // A wide but short tablet still loses its form space to the real IME.
            if (!HoyiUi.wide(activity) || activity.resources.configuration.screenHeightDp <= 720) {
                main { sections.setSelection(0) }
                waitForDraw()
                lateinit var field: EditText
                var label = ""
                var original = ""
                main {
                    field = descendants(content).filterIsInstance<EditText>().first { it.isShown && it.isEnabled }
                    check(field.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)
                    fullyVisible(field)
                    label = descendants(content).filterIsInstance<TextView>().single { it.labelFor == field.id }.text.toString()
                    original = field.text.toString()
                }
                tap(field)
                val deadline = SystemClock.elapsedRealtime() + 5000
                var observed: EditorObservation? = null
                while (observed == null && SystemClock.elapsedRealtime() < deadline) {
                    observed = observeEditor(test, activity.packageName, label, activity.getString(android.R.string.ok))
                    if (observed == null) Thread.sleep(50)
                }
                val editor = requireNotNull(observed) { "$name clicked local field but its named editor/confirmation was not observed" }
                try {
                    val keyboard = awaitKeyboard()
                    waitForDraw()
                    check(editor.input.refresh() && editor.confirm.refresh())
                    val inputBounds = Rect(); val confirmBounds = Rect()
                    editor.input.getBoundsInScreen(inputBounds); editor.confirm.getBoundsInScreen(confirmBounds)
                    check(editor.input.isVisibleToUser && editor.input.isEnabled && editor.input.isEditable && !inputBounds.isEmpty)
                    check(editor.confirm.isVisibleToUser && editor.confirm.isEnabled && editor.confirm.isClickable &&
                        confirmBounds.height() >= HoyiUi.dp(activity, 48))
                    check(editor.input.text?.toString().orEmpty() == original) { "$name focused editor changed the draft" }
                    if (keyboard == null) gaps += "short native editor observed, IME not observed; editor-only screenshot"
                    else check(!Rect.intersects(inputBounds, keyboard) && !Rect.intersects(confirmBounds, keyboard)) {
                        "$name native input/confirmation hidden by IME"
                    }
                    capture(if (keyboard == null) "short-editor" else "short-editor-ime")
                    editorObserved = true
                    imeObserved = imeObserved || keyboard != null
                } finally {
                    try {
                        check(editor.confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "$name observed editor OK was not reachable" }
                    } finally { editor.input.recycle(); editor.confirm.recycle() }
                }
                main {
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
                    check(field.text.toString() == original)
                }
                waitForDraw()
                main { SingleScreenPageChecks.verify(activity) }
            } else gaps += "wide inline input: compact native editor/IME evidence not attempted"
            allStatesChecked = true
        } finally {
            try {
                main { stageSelector?.setSelection(originalStages) }
                waitForDraw()
                main { sections.setSelection(originalSection) }
                waitForDraw()
                main { SingleScreenPageChecks.verify(activity) }
                completed = allStatesChecked
            } finally {
                accessibility.flags = originalFlags
                automation.serviceInfo = accessibility
                File(evidence, "$name-result.txt").writeText("completed=$completed screenshots=$screenshots sections=$sectionCount longStates=$longStates " +
                    "editorObserved=$editorObserved imeObserved=$imeObserved noSave=true\n" + gaps.joinToString("\n"))
                test.sendStatus(0, Bundle().apply { putString("stream", "CHINESE_FORM_STATES $name completed=$completed screenshots=$screenshots sections=$sectionCount " +
                    "longStates=$longStates editorObserved=$editorObserved imeObserved=$imeObserved noSave=true\n") })
            }
        }

        return screenshots
    }

    private data class EditorObservation(val input: AccessibilityNodeInfo, val confirm: AccessibilityNodeInfo)
    private fun observeEditor(test: Instrumentation, packageName: String, label: String, ok: String): EditorObservation? {
        val windows = test.uiAutomation.windows
        try {
            for (window in windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }) {
                val root = window.root ?: continue
                val nodes = mutableListOf<AccessibilityNodeInfo>()
                fun visit(node: AccessibilityNodeInfo) {
                    nodes += node
                    for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
                }
                try {
                    visit(root)
                    if (root.packageName?.toString() != packageName || nodes.none { it.text?.toString() == label }) continue
                    val input = nodes.singleOrNull { it.isEditable && it.isVisibleToUser } ?: continue
                    val confirm = nodes.singleOrNull { it.viewIdResourceName == "android:id/button1" && it.text?.toString()?.equals(ok, true) == true } ?: continue
                    return EditorObservation(AccessibilityNodeInfo.obtain(input), AccessibilityNodeInfo.obtain(confirm))
                } finally { nodes.forEach { it.recycle() } }
            }
        } finally { windows.forEach { it.recycle() } }
        return null
    }
}
