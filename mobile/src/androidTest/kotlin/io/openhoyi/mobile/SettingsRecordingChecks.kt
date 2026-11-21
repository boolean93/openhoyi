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
            fun waitFor(stage: String, check: () -> Boolean) {
                val until = SystemClock.uptimeMillis() + 3500
                while (!main(check)) {
                    check(SystemClock.uptimeMillis() < until) { "Settings recording route did not settle: $stage" }
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
                if (steps == 0) SystemClock.sleep(65)
                else repeat(6) {
                    SystemClock.sleep(23)
                    send(MotionEvent.ACTION_MOVE, toY)
                }
                send(MotionEvent.ACTION_UP, toY)
            }
            fun tap(view: View) {
                val rect = main { point(view) }
                gesture(rect.exactCenterX(), rect.exactCenterY(), rect.exactCenterY(), 0)
            }
            fun swipe(distance: Int? = null) {
                val r = main { point(document.scroll) }
                val max = r.height() * .61f
                val direction = if (distance != null && distance < 0) -1 else 1
                val travel = distance?.let { (kotlin.math.abs(it) + HoyiUi.dp(page.activity, 14)).toFloat().coerceIn(38f, max) } ?: max
                val from = if (direction > 0) r.top + r.height() * .81f else r.top + r.height() * .19f
                // Use the blank inset rather than letting an EditText consume the drag.
                gesture(r.left + 6f, from, from - direction * travel, 16)
            }
            waitFor("default Devices") { category(SettingsCategory.DEVICE).isSelected }
            SystemClock.sleep(1300)
            val start = main { document.scroll.scrollY }
            swipe()
            waitFor("initial real swipe") { document.scroll.scrollY > start }
            SystemClock.sleep(1000)
            tap(main { category(SettingsCategory.MACHINE) })
            waitFor("Machines directory tap") { document.isAtSectionLanding("machine") }
            SystemClock.sleep(1200)
            // Measure the scroll target, but move toward it only with visible real gestures.
            // A phone has a longer machine section than the tablet's paired cards.
            repeat(14) {
                if (!main { category(SettingsCategory.BREWING).isSelected }) {
                    val delta = main {
                        val target = Rect()
                        val heading = document.findViewWithTag<View>("settings/heading/shortcuts")
                        document.body.offsetDescendantRectToMyCoords(heading, target)
                        target.top - document.scroll.scrollY
                    }
                    swipe(delta)
                }
            }
            waitFor("Brewing highlight from scroll") { category(SettingsCategory.BREWING).isSelected }
            SystemClock.sleep(1300)
            tap(main { category(SettingsCategory.APPEARANCE) })
            waitFor("Appearance directory tap") { document.isAtSectionLanding("appearance") }
            SystemClock.sleep(1350)
            val prefs = (page.activity.application as MobileApplication).appearancePreferences
            val appearanceBefore = main { prefs.current }
            tap(main { document.findViewWithTag<View>("settings/appearance/palette/sea-salt") })
            waitFor("original palette preview") { fragment.appearanceCard?.dialog?.isShowing == true }
            SystemClock.sleep(1650)
            tap(main { requireNotNull(fragment.appearanceCard?.dialog).getButton(android.content.DialogInterface.BUTTON_NEGATIVE) })
            waitFor("palette preview cancelled without write") { fragment.appearanceCard?.dialog?.isShowing != true && prefs.current == appearanceBefore }
            SystemClock.sleep(1600)
        } finally {
            main { page.activity.finish() }
        }
    }
}
