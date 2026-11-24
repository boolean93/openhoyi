package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.SparseArray
import android.os.Parcelable
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.SettingsWriteTracker

/** Offscreen component regression: no preference, service or device writes. */
internal class UnifiedSwitchChecks(private val test: Instrumentation) {
    fun run() {
        var failure: Throwable? = null
        test.runOnMainSync {
            try {
                for (night in listOf(false, true)) {
                    val config = Configuration(test.targetContext.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    }
                    val context = test.targetContext.createConfigurationContext(config)
                    val toggle: Switch = HoyiSwitch(context).apply {
                        id = 1234
                        layoutParams = android.view.ViewGroup.LayoutParams(-2, -2)
                    }
                    val density = context.resources.displayMetrics.density
                    fun render(rtl: Boolean = false): Bitmap {
                        toggle.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                        toggle.measure(View.MeasureSpec.makeMeasureSpec((64 * density).toInt(), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec((48 * density).toInt(), View.MeasureSpec.EXACTLY))
                        toggle.layout(0, 0, toggle.measuredWidth, toggle.measuredHeight)
                        return Bitmap.createBitmap(toggle.width, toggle.height, Bitmap.Config.ARGB_8888).also { toggle.draw(Canvas(it)) }
                    }
                    check(toggle.minimumWidth >= 48 * density - 1 && toggle.minimumHeight >= 48 * density - 1)
                    val off = render()
                    var calls = 0
                    toggle.setOnCheckedChangeListener { _, _ -> calls++ }
                    toggle.performClick()
                    check(toggle.isChecked && calls == 1)
                    val on = render()
                    check(!off.sameAs(on)) { "ON must remain visually distinct from OFF ($night)" }
                    val rtl = render(true)
                    check(!on.sameAs(rtl)) { "RTL must move the thumb to the opposite side" }
                    toggle.isEnabled = false
                    check(!rtl.sameAs(render(true))) { "Disabled must be visually distinct" }
                    toggle.isEnabled = true
                    toggle.setOnCheckedChangeListener { button, checked -> if (!checked) button.isChecked = true }
                    toggle.performClick()
                    check(toggle.isChecked) { "Rejected persistence must restore checked state" }
                    val state = SparseArray<Parcelable>()
                    toggle.saveHierarchyState(state)
                    val restored = HoyiSwitch(context).apply { id = 1234 }
                    restored.restoreHierarchyState(state)
                    check(restored.isChecked) { "Native state restoration must survive recreation" }
                    toggle.setOnCheckedChangeListener(null)
                    repeat(8) { toggle.performClick() }
                    check(toggle.isChecked) { "Repeated toggles must preserve final native state" }
                    off.recycle(); on.recycle(); rtl.recycle()
                    verifyBoilerKeyboardActivation(context)
                    verifyBoilerPendingInk(context)
                    verifyBoilerArc(context)
                }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
    private fun verifyBoilerKeyboardActivation(context: android.content.Context) {
        val power = HoyiSwitch(context)
        var reads = 0
        var writes = 0
        val fresh = Any()
        power.authoritativeToggleIdentity = { power.drawnToggleIdentity }
        power.setOnCheckedChangeListener { _, _ -> writes++ }
        val readOnly = Button(context).apply { setOnClickListener { power.readbackRetry?.invoke() } }
        fun key(action: Int, code: Int = KeyEvent.KEYCODE_DPAD_CENTER) =
            power.dispatchKeyEvent(KeyEvent(action, code))

        for ((index, code) in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE).withIndex()) {
            power.drawnToggleIdentity = null
            power.readbackRetry = { reads++ }
            check(power.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO)
            check(!power.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            check(readOnly.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            check(key(KeyEvent.ACTION_DOWN, code))
            power.readbackRetry = null
            power.drawnToggleIdentity = fresh
            check(key(KeyEvent.ACTION_UP, code))
            check(readOnly.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            check(reads == (index + 1) * 2 && writes == 0 && !power.isChecked)
        }

        check(key(KeyEvent.ACTION_DOWN))
        power.readbackRetry = { reads++ }
        power.drawnToggleIdentity = null
        power.readbackRetry = null
        power.drawnToggleIdentity = fresh
        check(key(KeyEvent.ACTION_UP))
        check(reads == 4 && writes == 0 && !power.isChecked)

        check(key(KeyEvent.ACTION_DOWN) && power.isPressed)
        // CompoundButton toggles synchronously, then returns whether View called an OnClickListener.
        // These switches write through OnCheckedChangeListener and have no OnClickListener.
        key(KeyEvent.ACTION_UP)
        check(writes == 1 && power.isChecked && !power.isPressed)
    }

    private fun verifyBoilerArc(context: android.content.Context) {
        for ((steam, goal) in listOf(false to 92f, true to 120f)) {
            val current = TextView(context)
            val dial = BoilerTemperatureDial(context, TextView(context), current,
                TextView(context), TextView(context), HoyiSwitch(context), steam)
            val side = 300
            val exact = View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY)
            fun samples(reading: Float?, target: Float?): List<Int> {
                dial.present(reading, target)
                dial.measure(exact, exact); dial.layout(0, 0, side, side)
                val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
                return try {
                    dial.draw(Canvas(bitmap))
                    listOf(145.0, 230.0, 330.0).map { angle ->
                        val radians = Math.toRadians(angle)
                        val r = side * .442
                        bitmap.getPixel((side / 2 + r * kotlin.math.cos(radians)).toInt(),
                            (side / 2 + r * kotlin.math.sin(radians)).toInt())
                    }
                } finally { bitmap.recycle() }
            }
            val empty = samples(30f, goal)
            check(samples(20f, goal) == empty && current.text.toString() == "20.0 °C")
            check(samples(goal, 30f) == empty && samples(goal, null) == empty)
            val middle = samples((30f + goal) / 2f, goal)
            val full = samples(goal, goal)
            check(middle.take(2) == full.take(2) && middle[2] == empty[2] && full.zip(empty).all { it.first != it.second })
            check(samples(goal + 5f, goal) == full && current.text.toString() == "${goal + 5f} °C")
        }
    }

    // Real dial and switch drawing, but deliberately no HomeFragment or service writes.
    private fun verifyBoilerPendingInk(context: android.content.Context) {
        val power = HoyiSwitch(context).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
        fun label(text: String) = TextView(context).apply { this.text = text }
        val temperature = label("")
        val target = label("")
        val pressure = label("")
        val dial = BoilerTemperatureDial(context, label("Brew"), temperature,
            target, pressure, power, steam = false)
        val hold = HomeBoilerDialHold()
        val owner = Any()
        val address = "00:00:00:AB:CD:01"
        val frame = IdleTelemetry(9140, 11980, 88, 9, 0, 0, 1, 0, ByteFrame(byteArrayOf()))
        fun present(display: HomeBoilerDialHold.Display) {
            val live = display.frame as? IdleTelemetry
            val current = live?.brewTemperatureHundredthsC?.div(100f)
            target.text = display.brewTarget?.let { "$it °C" } ?: "—"
            pressure.text = MachineHomeReadings.from(live).brewPressure
            dial.present(current, display.brewTarget?.toFloat())
        }
        present(hold.resolve(owner, address, frame, 1000, 92, 120, 1000, hold = false))
        val diameter = (200 * context.resources.displayMetrics.density).toInt()
        val exact = View.MeasureSpec.makeMeasureSpec(diameter, View.MeasureSpec.EXACTLY)
        dial.measure(exact, exact)
        dial.layout(0, 0, diameter, diameter)
        check(power.width > 0 && power.height > 0)
        fun render() = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888).also {
            dial.draw(Canvas(it))
        }

        var writes = 0
        power.setOnCheckedChangeListener { _, _ -> writes++ }
        power.performClick()
        check(power.isChecked && writes == 1)
        val enabled = render()
        val pending = MachineSettingChange.BrewHeating(true)
        val waiting = SettingsWriteTracker.State.WAITING_READBACK
        fun project() {
            power.isEnabled = false
            // Match Home’s rendering guard: projecting readback is not a second user click.
            power.setOnCheckedChangeListener(null)
            power.isChecked = false
        }
        try {
            project()
            present(hold.resolve(owner, address, null, null, null, null, 2800, hold = true))
            check(temperature.text.toString() == "91.4 °C" && target.text.toString() == "92 °C")
            check(!power.isChecked && !power.isEnabled)
            check(HomeBoilerSwitchPresentation.showInline(
                HomeBoilerSwitchPresentation.label(true, false, pending, waiting)))
            check(render().let { image -> try { !enabled.sameAs(image) } finally { image.recycle() } }) {
                "Pending boiler switch must remain visually distinct from a confirmed ON"
            }
            val failed = SettingsWriteTracker.State.FAILED
            project()
            present(hold.resolve(owner, address, null, null, null, null, 4000, hold = false))
            check(temperature.text.toString() == "—" && target.text.toString() == "—")
            check(!power.isChecked && writes == 1)
            check(HomeBoilerSwitchPresentation.showInline(
                HomeBoilerSwitchPresentation.label(true, false, pending, failed)))
            check(render().let { image -> try { !enabled.sameAs(image) } finally { image.recycle() } })
        } finally {
            enabled.recycle()
        }
    }

}
