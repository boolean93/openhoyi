package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/** Uses real window input on an isolated Mock, not performClick/performLongClick. */
internal object AiMessageCopyChecks {
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
        var opened = 0
        var cardReferenceAvailable = false
        lateinit var chat: AiChatPresentation
        lateinit var windowContent: DownObserver
        val longPress = ViewConfiguration.getLongPressTimeout().toLong() + 200
        try {
            test.runOnMainSync {
                // Representative non-default dark theme; shared presentation and gestures stay identical.
                val base = AppLanguageContext.wrap(activity, AppLanguage.ENGLISH, true)
                val context = android.view.ContextThemeWrapper(
                    AppAppearanceContext(base, AppAppearance(true, AppThemePalette.COFFEE)), R.style.MobileTheme
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
                chat.render(lines, "", false, "")
            }
            test.waitForIdleSync()
            val focusDeadline = SystemClock.uptimeMillis() + 3000
            while (!activity.hasWindowFocus() && SystemClock.uptimeMillis() < focusDeadline) SystemClock.sleep(50)
            check(activity.hasWindowFocus()) { "Clipboard requires a focused foreground Activity" }
            test.runOnMainSync { clipboard.clearPrimaryClip() }

            fun assertClip(expected: String?, reason: String) {
                test.waitForIdleSync()
                test.runOnMainSync {
                    check(clipboard.primaryClip?.getItemAt(0)?.text?.toString() == expected) { reason }
                }
            }
            fun target(tag: String) = requireNotNull(chat.root.findViewWithTag<View>(tag))
            fun bubble(tag: String) = (target(tag) as ViewGroup).getChildAt(0) as ViewGroup
            fun body(tag: String) = bubble(tag).getChildAt(0) as TextView
            fun clearClip() = test.runOnMainSync { clipboard.clearPrimaryClip() }
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
            press(link)
            check(opened == 1) { "Short tap did not open the link" }
            assertClip(null, "Short link tap copied")
            press(link, longPress)
            check(opened == 1) { "Long press also opened the link" }
            assertClip(rendered, "Long press did not copy rendered assistant text")

            press(point(body("ai/chat/user")), longPress)
            assertClip(userText, "Long press did not copy user text")

            press(nonLink, longPress) {
                // Before the system long-press timeout: a status-only render used to detach the pressed TextView.
                chat.render(lines, "", false, "Status refreshed")
            }
            assertClip(rendered, "Status-only render cancelled the assistant text long press")
            check(opened == 1) { "Holding non-link text opened the link" }
            test.runOnMainSync { check(chat.root.findViewWithTag<View>("ai/chat/message-copy") == null) }

            clearClip()
            var padding = 0f to 0f
            test.runOnMainSync {
                val container = bubble("ai/chat/assistant")
                val location = IntArray(2).also { container.getLocationOnScreen(it) }
                check(container.paddingTop > 0 && message.top >= container.paddingTop)
                padding = (location[0] + container.width / 2f) to (location[1] + container.paddingTop / 2f)
            }
            press(padding, longPress)
            assertClip(rendered, "Long press on bubble padding did not copy the assistant text")

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
            assertClip("$streamText updated", "Streaming long press lost current content during refresh")

            test.runOnMainSync {
                chat.render(emptyList(), "", false, "")
                chat.updateStreamPreview(AiStreamPreview.Snapshot(content = partialText, contentTruncated = true,
                    phase = AiStreamPreview.Phase.INTERRUPTED))
            }
            test.waitForIdleSync()
            press(point(target("ai/chat/partial-answer")), longPress)
            assertClip(partialText, "Interrupted reply copy included a status label or lost text")

            val cardLines = listOf(AiChatLine(false, "", curveCards = listOf(card)))
            test.runOnMainSync {
                chat.resetStreamPreview()
                cardReferenceAvailable = true
                chat.render(cardLines, "", false, "")
                val primary = "ai/curve-card/${card.id}/primary"
                check(target(primary).isEnabled)
                cardReferenceAvailable = false
                chat.render(cardLines, "", false, "")
                check(!target(primary).isEnabled) { "Same transcript retained outdated curve availability" }
            }
            test.waitForIdleSync()
            val cardView = requireNotNull(chat.root.findViewWithTag<View>("ai/curve-card/${card.id}"))
            test.runOnMainSync { check(cardView.findViewWithTag<View>("ai/chat/message-copy") == null) }
            press(point(cardView), longPress)
            assertClip(partialText, "Holding a card changed the clipboard")

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
            assertClip(partialText, "Dragging the transcript copied a message")
            test.runOnMainSync { check(chat.scroll.scrollY > 0) { "Gesture did not actually scroll" } }
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
