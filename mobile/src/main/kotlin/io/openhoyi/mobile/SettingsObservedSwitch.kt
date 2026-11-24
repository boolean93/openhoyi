package io.openhoyi.mobile

import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout

/** Unknown is a read-only value, not the visual Off position of an editable switch. */
internal class SettingsObservedSwitch(
    val control: HoyiSwitch,
    style: TaskProductVisualStyle,
    private val compactKnown: Boolean = false,
    private val request: (Boolean) -> Unit,
) : FrameLayout(control.context) {
    private val unknown = style.text(context.getString(R.string.machine_settings_readback_missing), TaskProductVisualStyle.auxiliary,
        "settings/machine/switch-unknown").apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setTextColor(style.muted)
        minimumHeight = style.dp(48)
        setPaddingRelative(style.dp(8), 0, style.dp(8), 0)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        contentDescription = "${control.contentDescription} · ${text}"
    }

    private var retry: (() -> Unit)? = null
    private val readback = style.button(Button(context)).apply {
        text = context.getString(R.string.home_boiler_refresh)
        tag = "${control.tag}/readback-action"
        contentDescription = "${control.contentDescription} · $text · " +
            context.getString(R.string.machine_settings_sync_retry_state)
        minimumHeight = style.dp(48)
        setOnClickListener { retry?.invoke() }
        visibility = View.GONE
    }

    private var observed: Boolean? = null
    private var projecting = false

    init {
        addView(control, LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        addView(unknown, LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        addView(readback, LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
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
        retry = control.readbackRetry
        readback.visibility = if (retry == null) View.GONE else View.VISIBLE
        readback.isEnabled = retry != null
        // Keep the allotted size stable as readback arrives rather than pushing the surrounding UI around.
        control.visibility = if (value == null) INVISIBLE else VISIBLE
        unknown.visibility = if (retry != null) GONE else if (value == null) VISIBLE
            else if (compactKnown) GONE else INVISIBLE
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
