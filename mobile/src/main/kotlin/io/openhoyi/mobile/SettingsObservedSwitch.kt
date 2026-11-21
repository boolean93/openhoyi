package io.openhoyi.mobile

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/** Unknown is a read-only value, not the visual Off position of an editable switch. */
internal class SettingsObservedSwitch(
    val control: HoyiSwitch,
    style: TaskProductVisualStyle,
    private val request: (Boolean) -> Unit,
) : FrameLayout(control.context) {
    private val unknown = style.text(context.getString(R.string.machine_settings_readback_missing), TaskProductVisualStyle.auxiliary,
        "settings/machine/switch-unknown").apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setTextColor(style.muted)
        minimumHeight = style.dp(48)
        setPaddingRelative(style.dp(8), 0, style.dp(8), 0)
        androidx.core.view.ViewCompat.setAccessibilityLiveRegion(this, View.ACCESSIBILITY_LIVE_REGION_NONE)
        contentDescription = "${control.contentDescription} · ${text}"
    }

    private var observed: Boolean? = null
    private var projecting = false

    init {
        addView(control, LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        addView(unknown, LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        present(null, false)
        control.setOnCheckedChangeListener { _, chosen ->
            if (!projecting) {
                val before = observed
                val editable = control.isEnabled
                project(before)
                if (editable && before != null && chosen != before) request(chosen)
            }
        }
    }

    fun present(value: Boolean?, editable: Boolean) {
        // Keep the allotted size stable as readback arrives rather than pushing the surrounding UI around.
        control.visibility = if (value == null) INVISIBLE else VISIBLE
        unknown.visibility = if (value == null) VISIBLE else INVISIBLE
        control.isEnabled = value != null && editable
        observed = value
        project(value)
    }

    private fun project(value: Boolean?) {
        if (value == null || control.isChecked == value) return
        projecting = true
        try { control.isChecked = value }
        finally { projecting = false }
    }
}
