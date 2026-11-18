package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Exercises the real chat window with locally rendered messages, never a key or a send action. */
internal class PageBackgroundAiChecks(
    private val test: Instrumentation,
    private val chat: AiChatActivity,
    private val ui: AiChatPresentation,
    private val output: File,
) {
    private val style = TaskProductVisualStyle(chat)
    private fun <T> main(action: () -> T) = BoundedInstrumentationMain.run("AI backdrop chrome", action = action)

    fun run(mode: String) {
        val toolbar = checkNotNull(ui.root.findViewWithTag<ViewGroup>("ai/chat/toolbar"))
        val title = checkNotNull(ui.root.findViewWithTag<View>("ai/chat/brand-title"))
        val user = AiChatLine(true, "这支豆子怎么萃取？")
        main {
            ui.render(listOf(user) + List(5) {
                AiChatLine(false, "低压预浸，再平稳升压。后段逐步减压，记录杯中变化。".repeat(5))
            }, ui.input.text.toString(), false, "")
            ui.scroll.scrollTo(0, 0)
        }
        await("long transcript can scroll below the fixed AI toolbar") {
            ui.scroll.canScrollVertically(1) && ui.scroll.scrollY == 0
        }
        val bubble = main { (ui.transcript.findViewWithTag<ViewGroup>("ai/chat/user")).getChildAt(0) }
        data class FadeProbes(val y: Int, val left: Int, val right: Int, val bubbleInk: IntArray, val scroll: Int)
        val probes = main {
            val bar = screenBounds(toolbar)
            val ink = screenBounds(bubble)
            val y = bar.bottom - style.dp(5)
            check(ink.top > y && fullyVisible(title))
            // Left padding of the right-aligned user bubble has no text or rounded corners here.
            val xs = IntArray(3) { ink.left + style.dp(8 + it * 2) }
            FadeProbes(y, bar.left + style.dp(6), bar.right - style.dp(6), xs, ink.centerY() - y)
        }
        var beforeBubble = IntArray(0)
        screenshot {
            if (distance(it.getPixel(probes.left, probes.y), it.getPixel(probes.right, probes.y)) < 12) return@screenshot false
            beforeBubble = probes.bubbleInk.map { x -> it.getPixel(x, probes.y) }.toIntArray()
            true
        }
        main { ui.scroll.scrollTo(0, probes.scroll) }
        await("a real message moved under the lower toolbar") {
            ui.scroll.scrollY == probes.scroll && screenBounds(bubble).centerY() == probes.y
        }
        screenshot("$mode-ai-fade.png") { frame ->
            probes.bubbleInk.indices.count { i ->
                val blended = frame.getPixel(probes.bubbleInk[i], probes.y)
                distance(blended, beforeBubble[i]) >= 12 && distance(blended, style.primary) >= 12
            } >= 2
        }

        val options = main { toolbar.getChildAt(toolbar.childCount - 1) }
        tap(options)
        await("toolbar options actually opens when messages are beneath it") {
            ui.options?.takeIf { it.isShowing }?.contentView?.tag == "ai/chat/options"
        }
        main { ui.dismissOptions() }
        await("chat window is focused after closing toolbar options") { chat.hasWindowFocus() }

        val inputFrame = checkNotNull(ui.root.findViewWithTag<ViewGroup>("ai/chat/input-frame"))
        val plus = main {
            (0 until inputFrame.childCount).map(inputFrame::getChildAt).single {
                it.contentDescription == chat.getString(R.string.ai_attachment_add)
            }
        }
        fun composerReady() = inputFrame.isActivated &&
            fullyVisible(plus) && fullyVisible(ui.send) && ui.send.isEnabled && ui.send.isClickable
        fun composerGeometry(menu: View? = null) = ComposerGeometry(
            screenBounds(inputFrame), listOf(screenBounds(plus), screenBounds(ui.send)), menu?.let(::screenBounds),
        )

        tap(ui.input)
        await("actual keyboard and activated shared composer outline") {
            ui.input.hasFocus() && imeVisible() && composerReady()
        }
        captureComposer("$mode-ai-composer") {
            composerGeometry().takeIf { ui.input.hasFocus() && imeVisible() && composerReady() && ui.options == null }
        }
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("Back closes only the software keyboard") {
            imeHidden() && chat.hasWindowFocus() && !chat.isFinishing && ui.input.hasFocus() && composerReady()
        }

        tap(plus)
        fun visibleAttachmentMenu(): ViewGroup? {
            val menu = ui.options?.takeIf { it.isShowing }?.contentView as? ViewGroup ?: return null
            return menu.takeIf {
                it.tag == "ai/chat/attachment-menu" && composerReady() && it.childCount == 2 &&
                    (0 until it.childCount).map(it::getChildAt).all { choice ->
                        choice is Button && choice.isEnabled && choice.isClickable && fullyVisible(choice)
                    } && screenBounds(it).bottom <= screenBounds(inputFrame).top
            }
        }
        await("attachment popup displayed above the composer") { imeHidden() && visibleAttachmentMenu() != null }
        captureComposer("$mode-ai-attachment-menu") {
            if (imeHidden()) visibleAttachmentMenu()?.let(::composerGeometry) else null
        }
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("Back closes only the attachment menu") {
            ui.options == null && chat.hasWindowFocus() && !chat.isFinishing && ui.input.hasFocus() && composerReady()
        }
    }

    private data class ComposerGeometry(val outline: Rect, val buttons: List<Rect>, val menu: Rect?)

    private fun captureComposer(name: String, geometry: () -> ComposerGeometry?) {
        var composerPixels = "no compositor sample"
        screenshot("$name.png", "$name.failed.png", detail = { composerPixels }) { frame ->
            // Read the expected state's current geometry on each compositor sample.
            val (outline, buttons, menu) = main(geometry) ?: run {
                composerPixels = "$name geometry changed during capture"
                return@screenshot false
            }
            // Both states must show the real shared stroke on top and bottom of each button.
            val edge = style.dp(2).coerceAtLeast(2)
            val segments = buttons.flatMap { button ->
                (-1..1).map { offset ->
                    val x = button.centerX() + offset * style.dp(6)
                    val top = (outline.top until outline.top + edge).minOf { y -> distance(frame.getPixel(x, y), style.accent) }
                    val bottom = (outline.bottom - edge until outline.bottom).minOf { y -> distance(frame.getPixel(x, y), style.accent) }
                    Triple(x, top, bottom)
                }
            }
            // The popup has a separate surface: its interior must also appear in the compositor.
            var menuResult = "menu not expected"
            val menuError = menu?.let {
                val x = it.left + style.dp(7)
                val y = it.centerY()
                val color = frame.getPixel(x, y)
                distance(color, style.surface).also { error ->
                    menuResult = "menu=$it sample($x,$y)=#${color.toUInt().toString(16)} menuError=$error"
                }
            }
            composerPixels = "frame=$outline buttons=$buttons accent=#${style.accent.toUInt().toString(16)} " +
                "segments(x,topError,bottomError)=$segments $menuResult"
            segments.all { (_, top, bottom) -> top <= 8 && bottom <= 8 } && (menuError == null || menuError <= 8)
        }
    }

    private fun imeHidden(): Boolean {
        val type = WindowInsets.Type.ime()
        return ui.root.rootWindowInsets?.let { !it.isVisible(type) && it.getInsets(type).bottom == 0 } == true
    }

    private fun imeVisible(): Boolean {
        val type = WindowInsets.Type.ime()
        return ui.root.rootWindowInsets?.let { it.isVisible(type) && it.getInsets(type).bottom >= style.dp(100) } == true
    }

    private fun screenBounds(view: View): Rect {
        val location = IntArray(2).also { view.getLocationOnScreen(it) }
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun fullyVisible(view: View): Boolean = Rect().let {
        view.getGlobalVisibleRect(it) && it.width() == view.width && it.height() == view.height
    }

    private fun tap(view: View) {
        val at = main { screenBounds(view).also { check(fullyVisible(view) && view.isEnabled) } }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, at.exactCenterX(), at.exactCenterY(), 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                check(test.uiAutomation.injectInputEvent(event, true))
            } finally { event.recycle() }
        }
    }

    private fun screenshot(
        name: String? = null,
        failureName: String? = null,
        detail: () -> String = { "" },
        qualifies: (Bitmap) -> Boolean,
    ) {
        repeat(5) { attempt ->
            val submitted = CountDownLatch(1)
            main {
                check(ui.root.isHardwareAccelerated)
                ui.root.viewTreeObserver.registerFrameCommitCallback { submitted.countDown() }
                ui.root.invalidate()
            }
            check(submitted.await(5, TimeUnit.SECONDS))
            val image = checkNotNull(test.uiAutomation.takeScreenshot())
            try {
                if (qualifies(image)) {
                    if (name != null) writeScreenshot(name, image)
                    return
                }
                if (attempt == 4 && failureName != null) writeScreenshot(failureName, image)
            } finally { image.recycle() }
        }
        error("${name ?: "AI toolbar baseline"} did not show the expected compositor pixels: ${detail()}")
    }

    private fun writeScreenshot(name: String, image: Bitmap) {
        File(output, name).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun distance(a: Int, b: Int) = maxOf(abs(Color.red(a) - Color.red(b)),
        abs(Color.green(a) - Color.green(b)), abs(Color.blue(a) - Color.blue(b)))

    private fun await(label: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        do {
            if (main(condition)) return
            SystemClock.sleep(30)
        } while (SystemClock.uptimeMillis() < end)
        error(label)
    }
}
