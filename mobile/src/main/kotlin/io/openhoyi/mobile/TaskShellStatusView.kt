package io.openhoyi.mobile

import android.view.Gravity
import android.view.View
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker

/** Fixed shell actions that remain visible while a page scrolls or is hidden. */
internal class TaskShellStatusView(
    private val host: MainActivity,
    onSleep: () -> Unit,
    onScale: () -> Unit,
    onReconnectScale: () -> Unit,
    onDisconnectScale: () -> Unit,
) : LinearLayout(host) {
    private val style = TaskProductVisualStyle(host)
    private var mode: TaskNavigationMode? = null
    private var scaleLabel = ""
    private var scaleState = DeviceState.DISCONNECTED
    private val divider = View(host).apply { tag = "task-shell-divider"; setBackgroundColor(style.border) }
    private fun Button.shellText(minimumDp: Int) {
        minHeight = style.dp(minimumDp); minimumHeight = style.dp(minimumDp)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        val line = style.sp(20).toInt()
        if (android.os.Build.VERSION.SDK_INT >= 28) setLineHeight(line)
        else setLineSpacing((line - paint.fontMetricsInt.descent + paint.fontMetricsInt.ascent)
            .coerceAtLeast(0).toFloat(), 1f)
        isSingleLine = false; maxLines = Int.MAX_VALUE; ellipsize = null
        gravity = Gravity.CENTER
    }
    private fun Button.showText(value: String) { if (text.toString() != value) text = value }
    private val sleep = Button(host).apply {
        tag = "task-shell-sleep"; setOnClickListener { onSleep() }; style.button(this, outline = true)
        shellText(56)
    }
    private val scale = Button(host).apply {
        tag = "task-shell-scale"; setOnClickListener { onScale() }; style.button(this)
        shellText(56)
    }
    private val reconnect = Button(host).apply {
        tag = "task-shell-scale-reconnect"
        setOnClickListener {
            if (host.currentService?.snapshot?.scaleState == DeviceState.UNSUPPORTED) onScale()
            else onReconnectScale()
        }; style.button(this, outline = true)
        shellText(48)
    }
    private val disconnect = Button(host).apply {
        tag = "task-shell-scale-disconnect"
        setOnClickListener { onDisconnectScale() }; style.button(this, outline = true)
        shellText(48)
    }

    init { setBackgroundColor(style.surface); renderMode(TaskNavigationMode.BOTTOM); present(null) }

    /** Measure the current real labels at rail width without changing the live view hierarchy. */
    fun requiredRailHeight(width: Int): Int {
        val inner = (width - style.dp(16)).coerceAtLeast(0)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(inner, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val controls = listOf(sleep, scale, reconnect, disconnect).filter { it.visibility == View.VISIBLE }
        controls.forEach { it.measure(widthSpec, heightSpec) }
        return style.dp(12 + 1 + 8 + 8) + controls.sumOf { it.measuredHeight } +
            style.dp(8) * (controls.size - 1).coerceAtLeast(0)
    }

    fun renderMode(next: TaskNavigationMode) {
        if (mode == next) return
        mode = next
        removeAllViews()
        orientation = if (next == TaskNavigationMode.RAIL) VERTICAL else HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(style.dp(8), if (next == TaskNavigationMode.RAIL) style.dp(12) else style.dp(8),
            style.dp(8), style.dp(8))
        if (next == TaskNavigationMode.RAIL) {
            addView(divider, LayoutParams(-1, style.dp(1)))
            addView(sleep, LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
            addView(scale, LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
            addView(reconnect, LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
            addView(disconnect, LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
        } else {
            addView(sleep, LayoutParams(0, -2, 1f))
            addView(scale, LayoutParams(0, -2, 1f).apply { marginStart = style.dp(8) })
            addView(reconnect, LayoutParams(-2, -2).apply { marginStart = style.dp(8) })
            addView(disconnect, LayoutParams(-2, -2).apply { marginStart = style.dp(8) })
        }
        showScaleLabel()
    }

    private fun showScaleLabel() {
        val state = when (scaleState) {
            DeviceState.READY -> host.getString(R.string.task_shell_scale_connected)
            DeviceState.UNSUPPORTED -> host.getString(R.string.task_shell_scale_unsupported)
            else -> DeviceStatusText.label(host, scaleState)
        }
        val label = host.getString(R.string.home_scale) + "\n● " + state
        if (scale.text.toString() != label) {
            val colored = SpannableString(label)
            colored.setSpan(ForegroundColorSpan(host.getColor(if (scaleState == DeviceState.READY)
                R.color.mobile_success else R.color.mobile_muted)), label.indexOf('●'), label.indexOf('●') + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            scale.text = colored
        }
    }

    fun present(owner: MobileService?) {
        val snapshot = owner?.snapshot ?: MobileSnapshot()
        val idle = LiveTelemetry.machine(snapshot.coffee, snapshot.coffeeState, snapshot.coffeeAt,
            MobileMonotonicClock.elapsedRealtime()) as? IdleTelemetry
        val active = owner?.manualShotActive == true || owner?.shotState in setOf(
            ExtractionState.STARTING, ExtractionState.RUNNING, ExtractionState.STOP_REQUESTED)
        val settingBusy = owner?.settingWriteState in setOf(SettingsWriteTracker.State.WRITING,
            SettingsWriteTracker.State.WAITING_READBACK, SettingsWriteTracker.State.UNKNOWN)
        val sleepText = when {
            idle?.sleepStateRaw == 1 -> R.string.task_shell_sleep_asleep
            active -> R.string.task_shell_sleep_extraction
            owner?.sleepNowState == SleepNowTracker.State.UNKNOWN ||
                owner?.settingWriteState == SettingsWriteTracker.State.UNKNOWN -> R.string.task_shell_sleep_unknown_result
            owner?.sleepNowState in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP) || settingBusy ->
                R.string.task_shell_sleep_waiting
            owner != null && (owner.brewPreparationState != BrewPreparation.State.IDLE ||
                owner.machineControlSafetyMessage != null) -> R.string.task_shell_sleep_unavailable
            owner?.running != true -> R.string.task_shell_sleep_unconfirmed
            idle?.sleepStateRaw == 0 -> R.string.task_shell_sleep_awake
            else -> R.string.task_shell_sleep_unconfirmed
        }
        sleep.showText(host.getString(sleepText))
        val sleepReason = when {
            idle?.sleepStateRaw == 1 -> host.getString(R.string.task_shell_sleep_wake_hint)
            owner?.machineControlSafetyMessage != null -> owner.machineControlSafetyMessage
            owner != null && owner.brewPreparationState != BrewPreparation.State.IDLE ->
                host.getString(R.string.task_shell_sleep_preparing_reason)
            else -> null
        }
        sleep.contentDescription = host.getString(R.string.home_sleep_button) + " · " + sleep.text.toString().replace('\n', ' ') +
            (sleepReason?.let { " · $it" } ?: "")
        sleep.isEnabled = owner?.running == true && !active && !settingBusy &&
            owner.brewPreparationState == BrewPreparation.State.IDLE && idle?.sleepStateRaw == 0 &&
            owner.sleepNowState !in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP,
                SleepNowTracker.State.UNKNOWN) && owner.machineControlSafetyMessage == null
        scaleLabel = host.getString(R.string.home_scale) + " · " + DeviceStatusText.label(host, snapshot.scaleState)
        val oldState = scaleState
        scaleState = snapshot.scaleState
        if (oldState != scaleState) scale.text = ""
        showScaleLabel()
        scale.contentDescription = scaleLabel
        val disconnected = snapshot.scaleState in setOf(DeviceState.DISCONNECTED, DeviceState.FAILED, DeviceState.UNSUPPORTED)
        reconnect.visibility = if (disconnected) View.VISIBLE else View.GONE
        reconnect.isEnabled = !active
        reconnect.showText(host.getString(when (snapshot.scaleState) {
            DeviceState.UNSUPPORTED -> R.string.task_shell_scale_select
            DeviceState.FAILED -> R.string.task_shell_scale_reconnect
            else -> R.string.task_shell_scale_connect
        }))
        reconnect.contentDescription = (if (snapshot.scaleState == DeviceState.UNSUPPORTED)
            host.getString(R.string.extraction_connect_scale) else host.getString(R.string.home_connect)) +
            " · " + host.getString(R.string.home_scale)
        disconnect.visibility = if (disconnected) View.GONE else View.VISIBLE
        disconnect.isEnabled = !active && owner != null
        disconnect.showText(host.getString(R.string.task_shell_scale_disconnect))
        disconnect.contentDescription = host.getString(R.string.home_scale_disconnect)
    }
}
