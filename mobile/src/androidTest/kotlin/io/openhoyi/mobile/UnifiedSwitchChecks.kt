package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.SparseArray
import android.os.Parcelable
import android.view.View
import android.widget.Switch

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
                }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
