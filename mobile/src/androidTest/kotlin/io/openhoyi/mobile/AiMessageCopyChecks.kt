package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ClickableSpan
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

/** Uses real window input on an isolated Mock, not performClick/performLongClick. */
internal object AiMessageCopyChecks {
    private fun stage(name: String) { Log.i("OpenHoyiAiCopy", "stage=$name") }

    private class DownObserver(context: Context) : FrameLayout(context) {
        var pending: Pair<Long, () -> Unit>? = null
        var refreshedDownTime: Long? = null

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            val handled = super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val refresh = pending
                if (refresh != null && event.downTime == refresh.first) {
                    check(handled) { "Injected DOWN did not reach a touch target" }
                    pending = null
                    // The child already handled DOWN, but its delayed long-press callback
                    // cannot run until this main-thread dispatch returns to the looper.
                    refresh.second()
                    refreshedDownTime = event.downTime
                }
            }
            return handled
        }
    }

    fun run(test: Instrumentation) {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        stage("start-activity")
        val activity = test.startActivitySync(Intent(test.targetContext, AiChatPreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AiChatPreviewActivity
        val clipboard = requireNotNull(activity.getSystemService(ClipboardManager::class.java))
        val answer = "Read **this** [guide](https://example.test/local)"
        val rendered = "Read this guide"
        val userText = "A local question"
        val streamText = "Streaming response"
        val partialText = "Only the partial answer"
        val lines = listOf(AiChatLine(true, userText), AiChatLine(false, answer))
        val card = AiCurveCardConstructionChecks.cards().first()
        val image = AiAttachment("1635e178-f260-4999-8ce4-e10c13a53054", "fixture.jpg", "image/jpeg", 3, AiAttachmentKind.IMAGE)
        val seedClip = "A pre-existing clipboard value"
        var opened = 0
        var cardReferenceAvailable = false
        lateinit var chat: AiChatPresentation
        lateinit var windowContent: DownObserver
        val longPress = ViewConfiguration.getLongPressTimeout().toLong() + 200
        try {
            fun install(language: AppLanguage, font: AppFontSize, dark: Boolean) {
                test.runOnMainSync {
                    val context = android.view.ContextThemeWrapper(
                        AppAppearanceContext(AppLanguageContext.wrap(activity, language, dark, font),
                            AppAppearance(dark, AppThemePalette.COFFEE)), R.style.MobileTheme
                    ).apply { theme.applyStyle(AppThemePalette.COFFEE.nativeOverlay, true) }
                    check(context.themePalette() == AppThemePalette.COFFEE)
                    chat = AiChatPresentation(context, SettingsVisualStyle(context), "", 0, 0,
                        {}, {}, {}, { error("No request is allowed") },
                        onOpenLink = { check(it == "https://example.test/local"); opened++ },
                        onOpenCurve = { error("No curve action is allowed") },
                        curveAvailable = { cardReferenceAvailable })
                    windowContent = DownObserver(activity).apply {
                        addView(chat.root, FrameLayout.LayoutParams(-1, -1))
                    }
                    activity.setContentView(windowContent)
                    chat.render(lines, "", false, "", previewSession = "first")
                }
                test.waitForIdleSync()
            }
            stage("attach-english-large-dark")
            install(AppLanguage.ENGLISH, AppFontSize.LARGE, dark = true)
            val focusDeadline = SystemClock.uptimeMillis() + 3000
            while (!activity.hasWindowFocus() && SystemClock.uptimeMillis() < focusDeadline) SystemClock.sleep(50)
            check(activity.hasWindowFocus()) { "Clipboard requires a focused foreground Activity" }
            fun seedClipboard() = test.runOnMainSync {
                clipboard.setPrimaryClip(ClipData.newPlainText("Synthetic fixture", seedClip))
            }
            seedClipboard()

            fun assertClip(expected: String?, reason: String) {
                test.waitForIdleSync()
                test.runOnMainSync {
                    check(clipboard.primaryClip?.getItemAt(0)?.text?.toString() == expected) { reason }
                }
            }
            fun target(tag: String) = requireNotNull(chat.root.findViewWithTag<View>(tag))
            fun bubble(tag: String) = (target(tag) as ViewGroup).getChildAt(0) as ViewGroup
            fun body(tag: String) = bubble(tag).getChildAt(0) as TextView
            fun point(view: View): Pair<Float, Float> {
                var result = 0f to 0f
                test.runOnMainSync {
                    val bounds = Rect()
                    check(view.isShown && view.getGlobalVisibleRect(bounds)) { "Touch target not visible: ${view.tag}" }
                    val rootOrigin = IntArray(2).also { view.rootView.getLocationOnScreen(it) }
                    result = (rootOrigin[0] + bounds.exactCenterX()) to (rootOrigin[1] + bounds.exactCenterY())
                }
                return result
            }
            fun press(at: Pair<Float, Float>, duration: Long = 60, afterDown: (() -> Unit)? = null) {
                var time = 0L
                test.runOnMainSync {
                    check(windowContent.pending == null)
                    time = SystemClock.uptimeMillis()
                    if (afterDown != null) {
                        windowContent.refreshedDownTime = null
                        windowContent.pending = time to afterDown
                    }
                }
                touch(test, time, MotionEvent.ACTION_DOWN, at)
                if (afterDown != null) test.runOnMainSync {
                    check(windowContent.pending == null && windowContent.refreshedDownTime == time) {
                        "Refresh did not run during the injected DOWN dispatch"
                    }
                }
                SystemClock.sleep(duration)
                touch(test, time, MotionEvent.ACTION_UP, at)
                test.waitForIdleSync()
            }
            fun assertNoMenu(reason: String) = test.runOnMainSync {
                check(chat.options == null && chat.root.findViewWithTag<View>("ai/chat/message-copy") == null) { reason }
            }
            fun menuButton(label: String): Button {
                lateinit var button: Button
                test.runOnMainSync {
                    val menu = requireNotNull(chat.options?.takeIf { it.isShowing }) { "Long press did not expose a menu" }
                    check(menu.contentView.tag == "ai/chat/message-copy-menu")
                    button = requireNotNull(menu.contentView.findViewWithTag("ai/chat/message-copy"))
                    check(button.text.toString() == label && button.isEnabled)
                    val palette = SettingsVisualStyle(chat.root.context).palette
                    val expectedStyle = palette.button(Button(chat.root.context))
                    check(button.textSize == expectedStyle.textSize && button.currentTextColor == palette.accent) {
                        "The Copy action did not follow the app's font or palette"
                    }
                    val visible = Rect()
                    check(button.getGlobalVisibleRect(visible) && visible.width() == button.width &&
                        visible.height() == button.height && visible.width() >= palette.dp(48) &&
                        visible.height() >= palette.dp(48)) { "The Copy action is clipped or too small" }
                    check(button.layout.getEllipsisCount(0) == 0)
                }
                return button
            }
            fun clickCopy(expected: String, label: String = "Copy") {
                press(point(menuButton(label)))
                assertNoMenu("Copy click did not dismiss the popup")
                assertClip(expected, "Copy click did not write exactly the visible message body")
            }
            val message = body("ai/chat/assistant")
            var link = 0f to 0f
            var nonLink = 0f to 0f
            test.runOnMainSync {
                val layout = requireNotNull(message.layout)
                val text = message.text as Spanned
                val span = text.getSpans(0, text.length, ClickableSpan::class.java).single()
                val location = IntArray(2).also { message.getLocationOnScreen(it) }
                fun at(index: Int): Pair<Float, Float> {
                    val line = layout.getLineForOffset(index)
                    return (location[0] + message.totalPaddingLeft + layout.getPrimaryHorizontal(index)) to
                        (location[1] + message.totalPaddingTop +
                            (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f)
                }
                link = at(text.getSpanStart(span) + 1)
                nonLink = at(2)
            }
            stage("link-short-and-long")
            assertNoMenu("A copy control is visible before long press")
            press(link)
            check(opened == 1) { "Short tap did not open the link" }
            assertNoMenu("Short link tap opened a copy menu")
            assertClip(seedClip, "Short link tap copied")
            press(link, longPress)
            check(opened == 1) { "Long press also opened the link" }
            assertClip(seedClip, "Long press changed the clipboard before the menu action")
            stage("link-copy-action")
            clickCopy(rendered)

            stage("user-copy-and-back")
            press(point(body("ai/chat/user")), longPress)
            menuButton("Copy")
            assertClip(rendered, "Opening the user message menu changed the clipboard")
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            test.waitForIdleSync()
            assertNoMenu("Back did not dismiss the copy menu")
            assertClip(rendered, "Cancelling the user message menu changed the clipboard")
            press(point(body("ai/chat/user")), longPress)
            clickCopy(userText)

            stage("status-refresh-during-hold")
            press(nonLink, longPress) {
                // Before the system long-press timeout: a status-only render used to detach the pressed TextView.
                chat.render(lines, "", false, "Status refreshed", previewSession = "first")
            }
            assertClip(userText, "Holding non-link text copied before the menu action")
            check(opened == 1) { "Holding non-link text opened the link" }
            clickCopy(rendered)

            seedClipboard()
            stage("horizontal-drag-inside-body")
            val horizontalDistance = 3 * ViewConfiguration.get(activity).scaledTouchSlop
            val horizontalStart = (link.first - horizontalDistance) to link.second
            test.runOnMainSync {
                val left = IntArray(2).also { message.getLocationOnScreen(it) }[0]
                check(horizontalStart.first > left && link.first < left + message.width) {
                    "Horizontal regression must stay inside the body"
                }
            }
            val horizontalDown = SystemClock.uptimeMillis()
            touch(test, horizontalDown, MotionEvent.ACTION_DOWN, horizontalStart)
            SystemClock.sleep(32)
            touch(test, horizontalDown, MotionEvent.ACTION_MOVE, link)
            SystemClock.sleep(longPress)
            touch(test, horizontalDown, MotionEvent.ACTION_UP, link)
            test.waitForIdleSync()
            assertNoMenu("Horizontal drag inside the message opened a copy menu")
            assertClip(seedClip, "Horizontal drag inside the message copied")
            check(opened == 1) { "Dragging onto a link opened it" }

            stage("padding-menu-outside-cancel")
            var padding = 0f to 0f
            test.runOnMainSync {
                val container = bubble("ai/chat/assistant")
                val location = IntArray(2).also { container.getLocationOnScreen(it) }
                check(container.paddingTop > 0 && message.top >= container.paddingTop)
                padding = (location[0] + container.width / 2f) to (location[1] + container.paddingTop / 2f)
            }
            press(padding, longPress)
            menuButton("Copy")
            press(point(chat.send))
            assertNoMenu("Outside tap did not dismiss the message menu")
            assertClip(seedClip, "Outside dismissal changed the clipboard")

            stage("session-change-cancel")
            press(nonLink, longPress)
            menuButton("Copy")
            test.runOnMainSync { chat.render(lines, "", false, "", previewSession = "next") }
            test.waitForIdleSync()
            assertNoMenu("A menu from the previous session remained actionable")
            assertClip(seedClip, "Replacing the session copied its old text")

            stage("streaming-copy")
            test.runOnMainSync {
                chat.render(emptyList(), "Pending question", true, "")
                chat.updateStreamPreview(AiStreamPreview.Snapshot(content = streamText,
                    phase = AiStreamPreview.Phase.CONTENT))
            }
            test.waitForIdleSync()
            press(point(target("ai/chat/partial-answer")), longPress) {
                chat.render(emptyList(), "Pending question", true, "Retry status refreshed")
                chat.updateStreamPreview(AiStreamPreview.Snapshot(content = "$streamText updated",
                    phase = AiStreamPreview.Phase.CONTENT))
            }
            menuButton("Copy")
            assertClip(seedClip, "Streaming long press copied without confirmation")
            test.runOnMainSync {
                chat.updateStreamPreview(AiStreamPreview.Snapshot(content = "$streamText updated again",
                    reasoning = "Never copy this reasoning", phase = AiStreamPreview.Phase.CONTENT))
            }
            clickCopy("$streamText updated again")

            stage("interrupted-copy-and-reset")
            test.runOnMainSync {
                chat.render(emptyList(), "", false, "")
                chat.updateStreamPreview(AiStreamPreview.Snapshot(content = partialText, contentTruncated = true,
                    reasoning = "Never copy this reasoning", phase = AiStreamPreview.Phase.INTERRUPTED))
            }
            test.waitForIdleSync()
            press(point(target("ai/chat/partial-answer")), longPress)
            clickCopy(partialText)

            seedClipboard()
            press(point(target("ai/chat/partial-answer")), longPress)
            menuButton("Copy")
            test.runOnMainSync { chat.resetStreamPreview() }
            test.waitForIdleSync()
            assertNoMenu("A menu from a discarded streaming preview remained actionable")
            assertClip(seedClip, "Discarding the stream changed the clipboard")

            stage("card-and-attachment")
            val cardLines = listOf(AiChatLine(false, "", curveCards = listOf(card)), AiChatLine(true, "", listOf(image)))
            test.runOnMainSync {
                cardReferenceAvailable = true
                chat.render(cardLines, "", false, "")
                val primary = "ai/curve-card/${card.id}/primary"
                check(target(primary).isEnabled)
                cardReferenceAvailable = false
                chat.render(cardLines, "", false, "")
                check(!target(primary).isEnabled) { "Same transcript retained outdated curve availability" }
            }
            test.waitForIdleSync()
            press(point(target("ai/curve-card/${card.id}")), longPress)
            assertNoMenu("Holding a card opened a copy menu")
            assertClip(seedClip, "Holding a card changed the clipboard")
            press(point(target("ai/chat/message-image")), longPress)
            assertNoMenu("Holding an image attachment opened a copy menu")
            assertClip(seedClip, "Holding an image attachment changed the clipboard")

            stage("vertical-scroll")
            test.runOnMainSync {
                chat.render(listOf(AiChatLine(false, "A long line in the conversation.\n".repeat(80))), "", false, "")
                chat.scroll.scrollTo(0, 0)
            }
            test.waitForIdleSync()
            val (x, y) = point(body("ai/chat/assistant"))
            val down = SystemClock.uptimeMillis()
            touch(test, down, MotionEvent.ACTION_DOWN, x to y)
            val distance = 8 * ViewConfiguration.get(activity).scaledTouchSlop
            for (step in 1..4) {
                SystemClock.sleep(16)
                touch(test, down, MotionEvent.ACTION_MOVE, x to (y - distance * step / 4))
            }
            SystemClock.sleep(longPress)
            touch(test, down, MotionEvent.ACTION_UP, x to (y - distance))
            test.waitForIdleSync()
            assertNoMenu("Dragging the transcript opened a copy menu")
            assertClip(seedClip, "Dragging the transcript copied a message")
            test.runOnMainSync { check(chat.scroll.scrollY > 0) { "Gesture did not actually scroll" } }

            stage("chinese-small-light")
            install(AppLanguage.CHINESE, AppFontSize.SMALL, dark = false)
            press(point(body("ai/chat/user")), longPress)
            clickCopy(userText, "复制")
            seedClipboard()
            stage("accessibility-long-click")
            test.runOnMainSync {
                check(body("ai/chat/user").performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
            }
            test.waitForIdleSync()
            assertClip(seedClip, "Accessibility long click copied without confirmation")
            clickCopy(userText, "复制")
            stage("complete")
        } finally {
            test.runOnMainSync { activity.finish() }
        }
    }

    private fun touch(test: Instrumentation, down: Long, action: Int, at: Pair<Float, Float>) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, at.first, at.second, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            test.sendPointerSync(event)
        } finally { event.recycle() }
    }
}
