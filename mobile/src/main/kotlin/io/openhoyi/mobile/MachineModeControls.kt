package io.openhoyi.mobile

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.SettingsWriteTracker

/** Two independent wire bits; displayed selection always comes from the last settings readback. */
internal class MachineModeControls(context: Context, private val submit: (MachineSettingChange.LeverMode) -> Unit) : LinearLayout(context) {
    private var rendering = false
    private var pressure = false
    private var flow = false
    private var pressureBeforeFlow = false
    private val mode: HoyiSwitch = toggle("machine-mode/control") { selected ->
        val next = CurveModeSelection(flow, pressure, pressureBeforeFlow).selectFlow(selected)
        pressureBeforeFlow = next.pressureBeforeFlow
        submit(MachineSettingChange.LeverMode(next.pressure, next.flow))
    }
    private val logic: HoyiSwitch = toggle("machine-mode/logic") { selected -> submit(MachineSettingChange.LeverMode(selected, flow)) }
    private val row = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
    private val status = android.widget.TextView(context).apply {
        textSize = 12f; setTextColor(context.getColor(R.color.mobile_muted))
        visibility = View.GONE
    }
    init {
        orientation = VERTICAL
        isBaselineAligned = false
        tag = "machine-mode-controls"
        addView(row, LayoutParams(-1, -2))
        row.addView(mode, LayoutParams(-2, -2))
        row.addView(logic, LayoutParams(-2, -2).apply { marginStart = dp(12) })
        addView(status, LayoutParams(-1, -2))
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun toggle(key: String, change: (Boolean) -> Unit): HoyiSwitch = HoyiSwitch(context).apply {
        tag = key; textSize = 14f
        typeface = TaskTypography.face(context, 400)
        setTextColor(context.getColor(R.color.mobile_text))
        setPadding(0, dp(4), 0, dp(4))
        setOnCheckedChangeListener { _, checked ->
            if (!rendering) {
                // The thumb must not claim application before readback; the owner renders pending state.
                rendering = true
                isChecked = if (key.endsWith("control")) flow else pressure
                rendering = false
                mode.isEnabled = false
                logic.isEnabled = false
                change(checked)
            }
        }
    }
    fun render(owner: MobileService?) {
        val settings = owner?.snapshot?.settings
        rendering = true
        pressure = settings?.flags?.and(0x80) != 0 && settings != null
        flow = settings?.flags?.and(0x40) != 0 && settings != null
        mode.isChecked = flow; logic.isChecked = pressure
        mode.text = if (settings == null) "—" else context.getString(if (flow) R.string.curve_control_flow else R.string.curve_control_pressure)
        logic.text = if (settings == null) "—" else context.getString(when {
            flow && pressure -> R.string.curve_control_fast
            flow -> R.string.curve_control_slow
            pressure -> R.string.curve_control_follow
            else -> R.string.curve_control_constant
        })
        mode.contentDescription = context.getString(R.string.machine_mode_title) + " · " + mode.text
        logic.contentDescription = context.getString(R.string.machine_mode_title) + " · " + logic.text
        val enabled = MachineSettingsEditGate.allowed(owner)
        mode.isEnabled = enabled; logic.isEnabled = enabled
        val state = if (owner?.pendingSetting is MachineSettingChange.LeverMode) when (owner.settingWriteState) {
            SettingsWriteTracker.State.WRITING -> R.string.home_setting_writing
            SettingsWriteTracker.State.WAITING_READBACK -> R.string.home_setting_waiting
            SettingsWriteTracker.State.FAILED -> R.string.home_setting_failed
            SettingsWriteTracker.State.UNKNOWN -> R.string.home_setting_unknown
            else -> null
        } else null
        status.visibility = if (state == null) View.GONE else View.VISIBLE
        status.text = state?.let(context::getString).orEmpty()
        contentDescription = context.getString(R.string.machine_mode_title) + (state?.let { " · " + context.getString(it) } ?: "")
        rendering = false
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        mode.measure(free, free); logic.measure(free, free)
        val vertical = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED &&
            mode.measuredWidth + logic.measuredWidth + dp(12) > available
        row.orientation = if (vertical) VERTICAL else HORIZONTAL
        (logic.layoutParams as LayoutParams).apply { marginStart = if (vertical) 0 else dp(12) }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** Keeps the existing home status/connect row intact; narrow windows put controls below it. */
internal class MachineModeHeader(context: Context, private val heading: LinearLayout,
    private val controls: MachineModeControls) : LinearLayout(context) {
    init {
        tag = "product/home/machine-status"; gravity = android.view.Gravity.CENTER_VERTICAL
        isBaselineAligned = false
        addView(heading, LayoutParams(0, -2, 1f))
        addView(controls, LayoutParams(-2, -2))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        heading.measure(free, free); controls.measure(free, free)
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val stacked = heading.measuredWidth + controls.measuredWidth + (resources.displayMetrics.density * 12).toInt() > available
        orientation = if (stacked) VERTICAL else HORIZONTAL
        (heading.layoutParams as LayoutParams).apply { width = if (stacked) -1 else 0; weight = if (stacked) 0f else 1f }
        (controls.layoutParams as LayoutParams).apply {
            width = if (stacked) -1 else -2; marginStart = if (stacked) 0 else (resources.displayMetrics.density * 12).toInt()
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
