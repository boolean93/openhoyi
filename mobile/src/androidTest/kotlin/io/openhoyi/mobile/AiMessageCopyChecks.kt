package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.ClipboardManager
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
import android.widget.TextView

/** Uses real window input on an isolated Mock, not performClick/performLongClick. */
internal object AiMessageCopyChecks {
    fun run(test: Instrumentation) {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        val activity = test.startActivitySync(Intent(test.targetContext, AiChatPreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AiChatPreviewActivity
        val clipboard = requireNotNull(activity.getSystemService(ClipboardManager::class.java))
        val answer = "Read **this** [guide](https://example.test/local)"
        val rendered = "Read this guide"
        val userText = "A local question"
        val partialText = "Only the partial answer"
        val card = AiCurveCardConstructionChecks.cards().first()
        var opened = 0
        lateinit var chat: AiChatPresentation
        val longPress = ViewConfiguration.getLongPressTimeout().toLong() + 200
        try {
            test.runOnMainSync {
                val context = AppLanguageContext.wrap(activity, AppLanguage.ENGLISH)
                chat = AiChatPresentation(context, SettingsVisualStyle(context), "", 0, 0,
                    {}, {}, {}, { error("No request is allowed") },
                    onOpenLink = { check(it == "https://example.test/local"); opened++ })
                activity.setContentView(chat.root)
                chat.render(listOf(AiChatLine(true, userText), AiChatLine(false, answer)), "", false, "")
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
            fun copy(parent: View) = requireNotNull(parent.findViewWithTag<TextView>("ai/chat/message-copy")) {
                "No visible Copy action on this message"
            }
            fun point(view: View): Pair<Float, Float> {
                var result = 0f to 0f
                test.runOnMainSync {
                    val bounds = Rect()
                    check(view.isShown && view.getGlobalVisibleRect(bounds)) { "Touch target not visible: ${view.tag}" }
                    if (view.tag == "ai/chat/message-copy") {
                        val minimum = (48 * view.resources.displayMetrics.density).toInt()
                        check(bounds.width() >= minimum && bounds.height() >= minimum)
                        check((view as TextView).text.toString() == "Copy")
                    }
                    val rootOrigin = IntArray(2).also { view.rootView.getLocationOnScreen(it) }
                    result = (rootOrigin[0] + bounds.exactCenterX()) to (rootOrigin[1] + bounds.exactCenterY())
                }
                return result
            }
            fun press(at: Pair<Float, Float>, duration: Long = 60) {
                val time = SystemClock.uptimeMillis()
                touch(test, time, MotionEvent.ACTION_DOWN, at)
                SystemClock.sleep(duration)
                touch(test, time, MotionEvent.ACTION_UP, at)
                test.waitForIdleSync()
            }
            val assistant = target("ai/chat/assistant") as ViewGroup
            val message = (assistant.getChildAt(0) as ViewGroup).getChildAt(0) as TextView
            var link = 0f to 0f
            test.runOnMainSync {
                val layout = requireNotNull(message.layout)
                val text = message.text as Spanned
                val span = text.getSpans(0, text.length, ClickableSpan::class.java).single()
                val index = text.getSpanStart(span) + 1
                val line = layout.getLineForOffset(index)
                val location = IntArray(2).also { message.getLocationOnScreen(it) }
                link = (location[0] + message.totalPaddingLeft + layout.getPrimaryHorizontal(index)) to
                    (location[1] + message.totalPaddingTop +
                        (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f)
            }
            press(link)
            check(opened == 1) { "Short tap did not open the link" }
            assertClip(null, "Short link tap copied")
            press(link, longPress)
            check(opened == 1) { "Long press also opened the link" }
            assertClip(rendered, "Long press did not copy rendered assistant text")

            press(point(copy(target("ai/chat/user"))))
            assertClip(userText, "Copy action did not copy user text")
            press(point(copy(assistant)))
            assertClip(rendered, "Copy action did not copy rendered assistant text")

            test.runOnMainSync {
                chat.render(emptyList(), "", false, "")
                chat.updateStreamPreview(AiStreamPreview.Snapshot(content = partialText, contentTruncated = true,
                    phase = AiStreamPreview.Phase.INTERRUPTED))
            }
            test.waitForIdleSync()
            press(point(copy(target("ai/chat/stream-preview"))))
            assertClip(partialText, "Partial copy included a status label or lost text")

            test.runOnMainSync {
                chat.resetStreamPreview()
                chat.render(listOf(AiChatLine(false, "", curveCards = listOf(card))), "", false, "")
                check(target("ai/chat/assistant").findViewWithTag<View>("ai/chat/message-copy") == null)
                check(!copy(target("ai/chat/stream-preview")).isShown)
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
            val (x, y) = point(target("ai/chat/assistant"))
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
