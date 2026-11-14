package io.openhoyi.mobile

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.SettingsWriteTracker

/** Two independent wire bits. Missing readback uses disabled labels, never a writable default. */
internal class MachineModeControls(context: Context, private val submit: (MachineSettingChange.LeverMode) -> Unit) : LinearLayout(context) {
    private var rendering = false
    private val style = TaskProductVisualStyle(context)
    private val palette = CurveEditorColors(context)
    private var pressure = false
    private var flow = false
    private var pressureBeforeFlow = false
    private val mode: CurveEditorSegments = toggle("machine-mode/control", R.string.curve_control_pressure, R.string.curve_control_flow) { selected ->
        val next = CurveModeSelection(flow, pressure, pressureBeforeFlow).selectFlow(selected)
        pressureBeforeFlow = next.pressureBeforeFlow
        submit(MachineSettingChange.LeverMode(next.pressure, next.flow))
    }
    private val logic: CurveEditorSegments = toggle("machine-mode/logic", R.string.curve_control_constant, R.string.curve_control_follow) { selected -> submit(MachineSettingChange.LeverMode(selected, flow)) }
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
    private fun toggle(key: String, first: Int, second: Int, change: (Boolean) -> Unit): CurveEditorSegments =
        CurveEditorSegments(context, style, context.getString(first), context.getString(second)).apply {
            tag = key; compact()
            onSelected = { checked ->
                if (!rendering && isEnabled) {
                    // Keep the observed selection until the machine acknowledges the write.
                    select(if (key.endsWith("control")) flow else pressure)
                    mode.isEnabled = false
                    logic.isEnabled = false
                    change(checked)
                }
            }
        }
    private fun android.widget.TextView.showLabel(value: String) {
        if (text.toString() != value) text = value
    }
    fun render(owner: MobileService?) {
        val settings = owner?.snapshot?.settings
        rendering = true
        pressure = settings?.flags?.and(0x80) != 0 && settings != null
        flow = settings?.flags?.and(0x40) != 0 && settings != null
        mode.select(flow); logic.select(pressure)
        mode.colors(palette.pressureFill, palette.flowFill)
        logic.labels(context.getString(if (flow) R.string.curve_control_slow else R.string.curve_control_constant),
            context.getString(if (flow) R.string.curve_control_fast else R.string.curve_control_follow))
        logic.colors(palette.fill(flow), palette.fill(flow))
        val known = settings != null
        val enabled = known && MachineSettingsEditGate.allowed(owner)
        listOf(mode, logic).forEach { toggle ->
            toggle.isEnabled = enabled
            toggle.contentDescription = if (known)
                context.getString(R.string.machine_mode_title) + " · " + toggle.text
            else context.getString(R.string.machine_mode_missing)
            if (android.os.Build.VERSION.SDK_INT >= 30) toggle.stateDescription =
                if (known) null else context.getString(R.string.machine_mode_missing)
        }
        val state = if (owner?.pendingSetting is MachineSettingChange.LeverMode) when (owner.settingWriteState) {
            SettingsWriteTracker.State.WRITING -> R.string.home_setting_writing
            SettingsWriteTracker.State.WAITING_READBACK -> R.string.home_setting_waiting
            SettingsWriteTracker.State.FAILED -> R.string.home_setting_failed
            SettingsWriteTracker.State.UNKNOWN -> R.string.home_setting_unknown
            else -> null
        } else null
        status.visibility = if (state == null) View.GONE else View.VISIBLE
        status.showLabel(state?.let(context::getString).orEmpty())
        contentDescription = context.getString(R.string.machine_mode_title) + (state?.let { " · " + context.getString(it) } ?: "")
        rendering = false
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        mode.layoutParams.width = mode.requiredWidth()
        logic.layoutParams.width = logic.requiredWidth()
        mode.measure(MeasureSpec.makeMeasureSpec(mode.requiredWidth(), MeasureSpec.EXACTLY), free)
        logic.measure(MeasureSpec.makeMeasureSpec(logic.requiredWidth(), MeasureSpec.EXACTLY), free)
        val vertical = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED &&
            mode.measuredWidth + logic.measuredWidth + dp(12) > available
        row.orientation = if (vertical) VERTICAL else HORIZONTAL
        (logic.layoutParams as LayoutParams).apply { marginStart = if (vertical) 0 else dp(12) }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** Keeps the machine heading in place; narrow windows put controls below it. */
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
