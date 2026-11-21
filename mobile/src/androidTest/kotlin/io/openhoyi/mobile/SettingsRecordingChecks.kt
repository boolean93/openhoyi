package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

/** Real touch input on the actual Settings surface; no application or hardware changes. */
internal class SettingsRecordingChecks(private val test: Instrumentation) {
    private fun <T> main(action: () -> T) = BoundedInstrumentationMain.run("Settings movie", action = action)

    fun run() {
        val page = MainPageTestHost.launch(test, TaskDestination.SETTINGS, timeoutMs = 15_000)
        try {
            val fragment = main { page.active("root:SETTINGS") as AppSettingsFragment }
            val document = main { fragment.requireView() as SettingsDocumentLayout }
            fun category(type: SettingsCategory) = document.findViewWithTag<View>("settings/category/${type.key}")
            fun waitFor(check: () -> Boolean) {
                val until = SystemClock.uptimeMillis() + 3500
                while (!main(check)) {
                    check(SystemClock.uptimeMillis() < until) { "Settings recording route did not settle" }
                    SystemClock.sleep(50)
                }
            }
            fun point(view: View): Rect {
                val rect = Rect()
                check(view.getGlobalVisibleRect(rect) && view.isShown && rect.width() > 0 && rect.height() > 0)
                return rect
            }
            fun gesture(x: Float, fromY: Float, toY: Float, steps: Int) {
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, y: Float) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { check(test.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
                }
                send(MotionEvent.ACTION_DOWN, fromY)
                for (step in 1..steps) {
                    SystemClock.sleep(27)
                    send(MotionEvent.ACTION_MOVE, fromY + (toY - fromY) * step / steps)
                }
                SystemClock.sleep(if (steps == 0) 65 else 160)
                send(MotionEvent.ACTION_UP, toY)
            }
            fun tap(view: View) {
                val rect = main { point(view) }
                gesture(rect.exactCenterX(), rect.exactCenterY(), rect.exactCenterY(), 0)
            }
            fun swipe() {
                val r = main { point(document.scroll) }
                gesture(r.left + r.width() * .10f, r.top + r.height() * .80f, r.top + r.height() * .22f, 20)
            }
            waitFor { category(SettingsCategory.DEVICE).isSelected }
            SystemClock.sleep(1300)
            val start = main { document.scroll.scrollY }
            swipe()
            waitFor { document.scroll.scrollY > start }
            SystemClock.sleep(1000)
            tap(main { category(SettingsCategory.MACHINE) })
            waitFor { document.isAtSectionLanding("machine") }
            SystemClock.sleep(1200)
            // Align close to a category boundary to guarantee the change is visible in both viewports.
            // Navigation is via a real directory tap; highlight transition below comes only from a swipe.
            repeat(7) {
                if (!main { category(SettingsCategory.BREWING).isSelected }) {
                    swipe()
                    SystemClock.sleep(520)
                }
            }
            waitFor { category(SettingsCategory.BREWING).isSelected }
            SystemClock.sleep(1300)
            tap(main { category(SettingsCategory.APPEARANCE) })
            waitFor { document.isAtSectionLanding("appearance") }
            SystemClock.sleep(1350)
            val prefs = (page.activity.application as MobileApplication).appearancePreferences
            val appearanceBefore = main { prefs.current }
            tap(main { document.findViewWithTag<View>("settings/appearance/palette/sea-salt") })
            waitFor { fragment.appearanceCard?.dialog?.isShowing == true }
            SystemClock.sleep(1650)
            tap(main { requireNotNull(fragment.appearanceCard?.dialog).getButton(android.content.DialogInterface.BUTTON_NEGATIVE) })
            waitFor { fragment.appearanceCard?.dialog?.isShowing != true && prefs.current == appearanceBefore }
            SystemClock.sleep(1600)
        } finally {
            main { page.activity.finish() }
        }
    }
}
