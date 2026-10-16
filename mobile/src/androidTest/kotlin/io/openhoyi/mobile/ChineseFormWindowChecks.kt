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
        var pageWindowId = -1
        var activeEditorLabel: String? = null
        var primaryFailure: Throwable? = null
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
        var inlineObserved = false
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
        // Public API only; called from main-thread blocks after attaching this page.
        fun pageWindowIdOnMain(): Int {
            val node = content.createAccessibilityNodeInfo()
            return try { node.windowId } finally { node.recycle() }
        }
        fun attemptCleanups(steps: List<() -> Unit>) {
            var first: Throwable? = null
            steps.forEach { step ->
                try { step() } catch (error: Throwable) {
                    val previous = first
                    if (previous == null) first = error else if (previous !== error) previous.addSuppressed(error)
                }
            }
            first?.let { throw it }
        }
        fun preserveCleanup(primary: Throwable?, cleanup: () -> Unit) {
            try { cleanup() } catch (error: Throwable) {
                if (primary == null) throw error
                if (error !== primary) primary.addSuppressed(error)
            }
        }
        fun awaitEditor(label: String): EditorObservation {
            val deadline = SystemClock.elapsedRealtime() + 5000
            do {
                observeEditor(test, activity.packageName, label, activity.getString(android.R.string.ok), pageWindowId)
                    ?.let { return it }
                Thread.sleep(50)
            } while (SystemClock.elapsedRealtime() < deadline)
            error("$name named native editor not observed: $label pageWindowId=$pageWindowId")
        }
        fun awaitPageReady(label: String? = null, closedWindowId: Int? = null) {
            val deadline = SystemClock.elapsedRealtime() + 5000
            var stable = 0
            do {
                var pageFocused = false
                main { pageFocused = activity.hasWindowFocus() && pageWindowIdOnMain() == pageWindowId }
                val windows = test.uiAutomation.windows
                val oldEditorGone = try { closedWindowId == null || windows.none { it.id == closedWindowId } }
                    finally { windows.forEach { it.recycle() } }
                val editor = label?.let { observeEditor(test, activity.packageName, it, activity.getString(android.R.string.ok), pageWindowId) }
                val noEditor = editor == null
                editor?.let { it.input.recycle(); it.confirm.recycle() }
                if (pageFocused && oldEditorGone && noEditor && keyboardBounds() == null) stable++ else stable = 0
                if (stable >= 2) { waitForDraw(); return }
                Thread.sleep(50)
            } while (SystemClock.elapsedRealtime() < deadline)
            error("$name editor-close/page-focus/IME-hidden boundary timed out: label=$label closedWindowId=$closedWindowId pageWindowId=$pageWindowId")
        }
        fun confirmEditor(editor: EditorObservation, label: String) {
            val windowId = editor.input.windowId
            check(windowId != pageWindowId && editor.confirm.windowId == windowId)
            check(editor.confirm.refresh() && editor.confirm.isVisibleToUser && editor.confirm.isEnabled && editor.confirm.isClickable) {
                "$name native confirmation became stale: $label windowId=$windowId"
            }
            check(editor.confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "$name native editor confirmation not reachable: $label" }
            awaitPageReady(label, windowId)
            activeEditorLabel = null
        }
        fun cleanupEditor() {
            val label = activeEditorLabel
            val editor = label?.let { observeEditor(test, activity.packageName, it, activity.getString(android.R.string.ok), pageWindowId) }
            if (editor != null) try { confirmEditor(editor, requireNotNull(label)) }
                finally { editor.input.recycle(); editor.confirm.recycle() }
            main {
                // android.R.id.content itself is not the focusable beanPage root.
                check(content.getChildAt(0).requestFocus()) { "$name actual page root refused focus" }
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            }
            awaitPageReady(label)
            activeEditorLabel = null
        }
        fun freshPageRoot(): AccessibilityNodeInfo {
            val windows = test.uiAutomation.windows
            val root = try {
                windows.singleOrNull { it.id == pageWindowId && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }?.root
            } finally { windows.forEach { it.recycle() } }
            val observed = requireNotNull(root) { "$name original page accessibility window disappeared: $pageWindowId" }
            if (!observed.refresh() || observed.windowId != pageWindowId || observed.packageName?.toString() != activity.packageName) {
                observed.recycle()
                error("$name original page accessibility root is stale or belongs to another window")
            }
            return observed
        }
        val automation = test.uiAutomation
        val accessibility = requireNotNull(automation.serviceInfo)
        val originalFlags = accessibility.flags
        try {
            accessibility.flags = originalFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = accessibility
            test.waitForIdleSync()
            main { pageWindowId = pageWindowIdOnMain() }
            check(pageWindowId >= 0) { "$name page has no accessibility window id" }
            awaitPageReady()
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
                if (activity is BeanEntryActivity || activity is CurveEditorActivity) {
                    lateinit var groupField: EditText
                    main {
                        val form = descendants(content).single { it.tag == "task-form-scroll" } as android.widget.ScrollView
                        val cards = form.getChildAt(0) as ViewGroup
                        val card = cards.getChildAt(position)
                        groupField = descendants(card).filterIsInstance<EditText>().first { it.isShown }
                        groupField.requestRectangleOnScreen(Rect(0, 0, groupField.width, groupField.height), true)
                    }
                    waitForDraw()
                    main { fullyVisible(groupField) }
                }
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
                    var longFailure: Throwable? = null
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
                            var fieldLabel = ""
                            main { fieldLabel = descendants(content).filterIsInstance<TextView>().single { it.labelFor == field.id }.text.toString() }
                            if (HoyiUi.wide(activity) && activity.resources.configuration.screenHeightDp <= 720) activeEditorLabel = fieldLabel
                            tap(field)
                            val keyboard = awaitKeyboard()
                            waitForDraw()
                            if (HoyiUi.wide(activity) && activity.resources.configuration.screenHeightDp <= 720) {
                                val label = fieldLabel
                                val focused = awaitEditor(label)
                                try {
                                    check(focused.input.refresh() && focused.confirm.refresh())
                                    val inputBounds = Rect(); val confirmBounds = Rect()
                                    focused.input.getBoundsInScreen(inputBounds); focused.confirm.getBoundsInScreen(confirmBounds)
                                    check(focused.input.isVisibleToUser && focused.input.isEditable && focused.input.isEnabled)
                                    check(focused.input.text?.toString() == longText) { "$name focused long editor lost the draft" }
                                    check(inputBounds.height() >= HoyiUi.dp(activity, 128)) { "$name focused long editor still has only a single-line viewport" }
                                    check(focused.confirm.isVisibleToUser && focused.confirm.isClickable && confirmBounds.height() >= HoyiUi.dp(activity, 48))
                                    if (keyboard == null) gaps += "focused long editor observed; IME not observed"
                                    else check(!Rect.intersects(inputBounds, keyboard) && !Rect.intersects(confirmBounds, keyboard)) { "$name focused long editor hidden by IME" }
                                    capture("section-$position-long-focused${if (keyboard == null) "" else "-ime"}")
                                    editorObserved = true
                                    imeObserved = imeObserved || keyboard != null
                                    // Exercise the real native filter and editing, not just the initial text.
                                    val oversized = "测".repeat(16032)
                                    check(focused.input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, oversized)
                                    })) { "$name ACTION_SET_TEXT oversized native draft was rejected" }
                                    waitForDraw()
                                    check(focused.input.refresh() && focused.input.text?.length == 16000) { "$name native editor lost its original length limit" }
                                    main { check(field.text.toString() == oversized.take(16000)) { "$name filtered native draft did not reach the page" } }
                                    val edited = longText + "\n末尾编辑检查"
                                    check(focused.input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, edited)
                                    })) { "$name ACTION_SET_TEXT edited native draft was rejected" }
                                    waitForDraw()
                                    check(focused.input.refresh() && focused.input.text?.toString() == edited) {
                                        "$name native edited text was not fully observed before caret movement"
                                    }
                                    // ACTION_SET_TEXT already leaves the native caret at the end.
                                    // Move it away first: setting an unchanged selection is a native no-op.
                                    check(focused.input.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
                                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 0)
                                    })) { "$name ACTION_SET_SELECTION start 0:0 was rejected" }
                                    waitForDraw()
                                    check(focused.input.refresh() && focused.input.text?.toString() == edited &&
                                        focused.input.textSelectionStart == 0 && focused.input.textSelectionEnd == 0) {
                                        "$name native start caret/text mismatch: selection=${focused.input.textSelectionStart}:${focused.input.textSelectionEnd} expected=0:0"
                                    }
                                    main {
                                        check(field.text.toString() == edited && field.selectionStart == 0 && field.selectionEnd == 0) {
                                            "$name page start caret/text mismatch: selection=${field.selectionStart}:${field.selectionEnd} expected=0:0"
                                        }
                                    }
                                    check(focused.input.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
                                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, edited.length)
                                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, edited.length)
                                    })) { "$name ACTION_SET_SELECTION end ${edited.length}:${edited.length} was rejected" }
                                    waitForDraw()
                                    check(focused.input.refresh() && focused.input.text?.toString() == edited &&
                                        focused.input.textSelectionStart == edited.length && focused.input.textSelectionEnd == edited.length) {
                                        "$name native end caret/text mismatch: selection=${focused.input.textSelectionStart}:${focused.input.textSelectionEnd} expected=${edited.length}"
                                    }
                                    main {
                                        check(field.text.toString() == edited && field.selectionStart == edited.length && field.selectionEnd == edited.length) {
                                            "$name native long edit lost draft/caret: chars=${field.text.length} selection=${field.selectionStart}:${field.selectionEnd} expected=${edited.length}"
                                        }
                                    }
                                    confirmEditor(focused, label)
                                } finally { focused.input.recycle(); focused.confirm.recycle() }
                                waitForDraw()
                                val edited = longText + "\n末尾编辑检查"
                                main {
                                    check(field.text.toString() == edited && field.selectionStart == edited.length && field.selectionEnd == edited.length) {
                                        "$name confirmed long editor changed draft/caret: chars=${field.text.length} selection=${field.selectionStart}:${field.selectionEnd} expected=${edited.length}"
                                    }
                                }
                                // A coordinate tap intentionally moves a text caret. Reopen using
                                // the observed accessibility click to test position preservation itself.
                                val activeRoot = freshPageRoot()
                                try {
                                    check(activeRoot.packageName?.toString() == activity.packageName)
                                    val nodes = activeRoot.findAccessibilityNodeInfosByText(edited)
                                    try {
                                        val input = nodes.single { it.isEditable && it.isVisibleToUser && it.text?.toString() == edited }
                                        check(input.windowId == pageWindowId && input.refresh()) { "$name observed long field belongs to a stale window" }
                                        activeEditorLabel = label
                                        check(input.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "$name observed long field cannot reopen" }
                                    } finally { nodes.forEach { it.recycle() } }
                                } finally { activeRoot.recycle() }
                                test.waitForIdleSync()
                                waitForDraw()
                                val reopened = awaitEditor(label)
                                try {
                                    check(reopened.input.refresh()) { "$name reopened native input is stale" }
                                    check(reopened.input.text?.toString() == edited && reopened.input.textSelectionStart == edited.length && reopened.input.textSelectionEnd == edited.length) {
                                        "$name reopened editor lost its edit position: selection=${reopened.input.textSelectionStart}:${reopened.input.textSelectionEnd} expected=${edited.length}"
                                    }
                                    capture("section-$position-long-reopened")
                                    check(reopened.input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, longText)
                                    })) { "$name ACTION_SET_TEXT reopened native draft restore was rejected" }
                                    confirmEditor(reopened, label)
                                } finally { reopened.input.recycle(); reopened.confirm.recycle() }
                                waitForDraw()
                                main { check(field.text.toString() == longText) }
                            }
                            else if (keyboard == null) gaps += "long-editor IME not observed; no IME screenshot claimed"
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
                    } catch (error: Throwable) {
                        longFailure = error
                        throw error
                    } finally {
                        preserveCleanup(longFailure) {
                            attemptCleanups(listOf(
                                { cleanupEditor() },
                                { main {
                                    field.clearFocus()
                                    field.setText(original); field.setSelection(selection.coerceIn(0, original.length)); field.scrollTo(0, originalScroll)
                                } },
                                { waitForDraw(); main { SingleScreenPageChecks.verify(activity) } }
                            ))
                        }
                    }
                }
            }
            // Only click the Mock page's observed short EditText; the editor's OK cannot save the page.
            // A wide but short tablet still loses its form space to the real IME.
            if (activity is BeanEntryActivity || activity is CurveEditorActivity) {
                main { sections.setSelection(0) }
                waitForDraw()
                lateinit var field: EditText
                var original = ""
                var originalStart = 0
                var originalEnd = 0
                main {
                    field = descendants(content).filterIsInstance<EditText>().first { it.isShown && it.isEnabled }
                    original = field.text.toString()
                    originalStart = field.selectionStart.coerceAtLeast(0)
                    originalEnd = field.selectionEnd.coerceAtLeast(0)
                    field.requestRectangleOnScreen(Rect(0, 0, field.width, field.height), true)
                }
                waitForDraw()
                tap(field)
                val keyboard = awaitKeyboard()
                waitForDraw()
                try {
                    main {
                        check(activity.hasWindowFocus() && pageWindowIdOnMain() == pageWindowId)
                        check(field.hasFocus()) { "$name inline field did not retain focus" }
                        field.requestRectangleOnScreen(Rect(0, 0, field.width, field.height), true)
                    }
                    waitForDraw()
                    main {
                        fullyVisible(field)
                        val bounds = Rect(); field.getGlobalVisibleRect(bounds)
                        if (keyboard != null) check(!Rect.intersects(bounds, keyboard)) { "$name inline input hidden by IME" }
                        check(field.text.toString() == original) { "$name inline focus changed draft" }
                        field.setText("中文草稿  未保存")
                        field.setSelection(2, 4)
                        SingleScreenPageChecks.verify(activity)
                    }
                    waitForDraw()
                    main {
                        check(field.text.toString() == "中文草稿  未保存" && field.selectionStart == 2 && field.selectionEnd == 4)
                        // Fixed page actions must remain reachable while the real inline input owns focus.
                        descendants(content).filterIsInstance<Button>().filter { button ->
                            button.isShown && generateSequence(button.parent) { it.parent }.none {
                                it is android.widget.ScrollView
                            }
                        }.forEach { button ->
                            fullyVisible(button)
                            val bounds = Rect(); button.getGlobalVisibleRect(bounds)
                            check(button.height >= HoyiUi.dp(activity, 48))
                            if (keyboard != null) check(!Rect.intersects(bounds, keyboard)) { "$name fixed action hidden by IME" }
                        }
                    }
                    capture(if (keyboard == null) "short-inline" else "short-inline-ime")
                    inlineObserved = true
                    imeObserved = imeObserved || keyboard != null
                    if (keyboard == null) gaps += "inline editing observed; IME not observed"
                } finally {
                    main {
                        field.setText(original)
                        field.setSelection(originalStart.coerceAtMost(original.length), originalEnd.coerceAtMost(original.length))
                    }
                    cleanupEditor()
                }
            } else if (!HoyiUi.wide(activity) || activity.resources.configuration.screenHeightDp <= 720) {
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
                activeEditorLabel = label
                tap(field)
                val editor = awaitEditor(label)
                var editorFailure: Throwable? = null
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
                } catch (error: Throwable) {
                    editorFailure = error
                    throw error
                } finally {
                    preserveCleanup(editorFailure) {
                        try { confirmEditor(editor, label) }
                        finally { editor.input.recycle(); editor.confirm.recycle() }
                    }
                }
                main {
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
                    check(field.text.toString() == original)
                }
                waitForDraw()
                main { SingleScreenPageChecks.verify(activity) }
            } else gaps += "wide inline input: compact native editor/IME evidence not attempted"
            allStatesChecked = true
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            fun cleanupStep(action: () -> Unit) {
                try { action() } catch (error: Throwable) {
                    completed = false
                    val first = cleanupFailure
                    if (first == null) cleanupFailure = error else if (first !== error) first.addSuppressed(error)
                }
            }
            cleanupStep { cleanupEditor() }
            cleanupStep { main { stageSelector?.setSelection(originalStages) } }
            cleanupStep { waitForDraw() }
            cleanupStep { main { sections.setSelection(originalSection) } }
            cleanupStep {
                waitForDraw()
                main { SingleScreenPageChecks.verify(activity) }
                completed = allStatesChecked && primaryFailure == null && cleanupFailure == null
            }
            cleanupStep { accessibility.flags = originalFlags; automation.serviceInfo = accessibility }
            cleanupStep {
                File(evidence, "$name-result.txt").writeText("completed=$completed screenshots=$screenshots sections=$sectionCount longStates=$longStates " +
                    "editorObserved=$editorObserved inlineObserved=$inlineObserved imeObserved=$imeObserved pageWindowId=$pageWindowId noSave=true\n" + gaps.joinToString("\n") +
                    (primaryFailure?.let { "\nPRIMARY\n" + it.stackTraceToString() } ?: "") +
                    (cleanupFailure?.let { "\nCLEANUP\n" + it.stackTraceToString() } ?: ""))
            }
            cleanupStep {
                test.sendStatus(0, Bundle().apply { putString("stream", "CHINESE_FORM_STATES $name completed=$completed screenshots=$screenshots sections=$sectionCount " +
                    "longStates=$longStates editorObserved=$editorObserved inlineObserved=$inlineObserved imeObserved=$imeObserved pageWindowId=$pageWindowId noSave=true\n") })
            }
            cleanupFailure?.let { error ->
                val primary = primaryFailure
                if (primary == null) throw error
                if (primary !== error) primary.addSuppressed(error)
            }
        }

        return screenshots
    }

    private data class EditorObservation(val input: AccessibilityNodeInfo, val confirm: AccessibilityNodeInfo)
    private fun observeEditor(test: Instrumentation, packageName: String, label: String, ok: String, pageWindowId: Int): EditorObservation? {
        val windows = test.uiAutomation.windows
        try {
            for (window in windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.id != pageWindowId }) {
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
