package io.openhoyi.mobile

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.ScaleSelectionPolicy
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker

/** Read-only device facts sit beside separate gated shell actions. */
internal class TaskShellStatusView(
    private val host: MainActivity,
    onSleep: () -> Unit,
    onScale: () -> Unit,
    onReconnectScale: () -> Unit,
    onDisconnectScale: () -> Unit,
) : LinearLayout(host) {
    private val style = TaskProductVisualStyle(host)
    private var mode: TaskNavigationMode? = null
    private val divider = View(host).apply { tag = "task-shell-divider"; setBackgroundColor(style.border) }
    private fun statusText(id: String, ink: Boolean = false) = TextView(host).apply {
        tag = id
        style.apply(this, TaskProductVisualStyle.Type(14, if (ink) 500 else 400, 20), !ink)
        gravity = Gravity.CENTER; setSingleLine(false); maxLines = Int.MAX_VALUE; ellipsize = null
    }
    private fun group(id: String) = LinearLayout(host).apply {
        tag = id; orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, style.dp(4), 0, style.dp(4))
        background = style.shape(style.selected, radius = 10)
    }
    private val coffeeGroup = group("task-shell-coffee-group")
    private val scaleGroup = group("task-shell-scale-group")
    private val coffeeName = statusText("task-shell-coffee-name", true).apply { text = host.getString(R.string.home_coffee) }
    private val coffeeStatus = statusText("task-shell-coffee-status")
    private val scaleName = statusText("task-shell-scale-name", true).apply { text = host.getString(R.string.home_scale) }
    private val scaleStatus = statusText("task-shell-scale")
    private fun action(id: String, click: () -> Unit) = Button(host).apply {
        tag = id; style.button(this); setOnClickListener { click() }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        val line = style.sp(20).toInt()
        if (android.os.Build.VERSION.SDK_INT >= 28) setLineHeight(line)
        else setLineSpacing((line - paint.fontMetricsInt.descent + paint.fontMetricsInt.ascent)
            .coerceAtLeast(0).toFloat(), 1f)
        isSingleLine = false; maxLines = Int.MAX_VALUE; ellipsize = null
        gravity = Gravity.CENTER; minHeight = style.dp(48); minimumHeight = style.dp(48)
        compoundDrawablePadding = style.dp(8)
        setPadding(style.dp(4), style.dp(4), style.dp(4), style.dp(4))
    }
    private val sleep = action("task-shell-sleep", onSleep)
    private val reconnect = action("task-shell-scale-reconnect") {
        if (host.currentService?.snapshot?.scaleState == DeviceState.UNSUPPORTED) onScale()
        else onReconnectScale()
    }
    private val disconnect = action("task-shell-scale-disconnect", onDisconnectScale)
    private var sleepIcon = ""
    private var reconnectIcon = ""
    private var disconnectIcon = ""

    init {
        setBackgroundColor(style.surface)
        coffeeGroup.addView(coffeeName, LayoutParams(-1, -2))
        coffeeGroup.addView(coffeeStatus, LayoutParams(-1, -2))
        coffeeGroup.addView(sleep, LayoutParams(-1, -2))
        scaleGroup.addView(scaleName, LayoutParams(-1, -2))
        scaleGroup.addView(scaleStatus, LayoutParams(-1, -2))
        scaleGroup.addView(reconnect, LayoutParams(-1, -2))
        scaleGroup.addView(disconnect, LayoutParams(-1, -2))
        renderMode(TaskNavigationMode.BOTTOM)
        present(null)
    }

    private fun icon(button: Button, kind: String) {
        if (button === sleep && sleepIcon == kind || button === reconnect && reconnectIcon == kind ||
            button === disconnect && disconnectIcon == kind) return
        val drawable = HomeVisualIcon.drawable(host, kind, button.currentTextColor,
            currentTint = { button.currentTextColor }).apply { setBounds(0, 0, style.dp(20), style.dp(20)) }
        button.setCompoundDrawablesRelative(drawable, null, null, null)
        if (button === sleep) sleepIcon = kind
        if (button === reconnect) reconnectIcon = kind
        if (button === disconnect) disconnectIcon = kind
    }
    private fun Button.label(value: String) { if (text.toString() != value) text = value }
    private fun Button.describe(value: String) {
        if (contentDescription?.toString() != value) contentDescription = value
        if (tooltipText?.toString() != value) tooltipText = value
    }

    /** Measure current real text at the 104dp rail's 88dp inner width. */
    fun requiredRailHeight(width: Int): Int {
        val inner = (width - style.dp(16)).coerceAtLeast(0)
        val widthSpec = MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        coffeeGroup.measure(widthSpec, heightSpec)
        scaleGroup.measure(widthSpec, heightSpec)
        return style.dp(12 + 8 + 8) + style.dp(1) + coffeeGroup.measuredHeight +
            style.dp(8) + scaleGroup.measuredHeight
    }

    fun renderMode(next: TaskNavigationMode) {
        if (mode == next) return
        mode = next; removeAllViews()
        orientation = if (next == TaskNavigationMode.RAIL) VERTICAL else HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(style.dp(8), if (next == TaskNavigationMode.RAIL) style.dp(12) else style.dp(8),
            style.dp(8), style.dp(8))
        if (next == TaskNavigationMode.RAIL) {
            addView(divider, LayoutParams(-1, style.dp(1)))
            addView(coffeeGroup, LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
            addView(scaleGroup, LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
        } else {
            addView(coffeeGroup, LayoutParams(0, -2, 1f))
            addView(scaleGroup, LayoutParams(0, -2, 1f).apply { marginStart = style.dp(8) })
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
        val statusId = TaskShellDeviceStatus.coffee(
            active, owner?.sleepNowState, owner?.settingWriteState,
            owner != null && (owner.brewPreparationState != BrewPreparation.State.IDLE ||
                owner.machineControlSafetyMessage != null), owner?.running == true,
            snapshot.coffeeState, idle?.sleepStateRaw)
        val status = host.getString(statusId)
        val coffeeLine = "● $status"
        if (coffeeStatus.text.toString() != coffeeLine) {
            val colored = SpannableString(coffeeLine)
            colored.setSpan(ForegroundColorSpan(if (statusId == R.string.task_shell_machine_awake)
                host.getColor(R.color.mobile_success) else style.muted), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            coffeeStatus.text = colored
        }
        val asleep = statusId == R.string.task_shell_machine_asleep
        val sleepAction = host.getString(if (asleep) R.string.task_shell_action_wake_on_machine
            else R.string.task_shell_action_sleep)
        sleep.label(sleepAction); icon(sleep, "moon")
        val sleepReason = when {
            asleep -> host.getString(R.string.task_shell_sleep_wake_hint)
            owner?.machineControlSafetyMessage != null -> owner.machineControlSafetyMessage
            owner != null && owner.brewPreparationState != BrewPreparation.State.IDLE ->
                host.getString(R.string.task_shell_sleep_preparing_reason)
            else -> null
        }
        sleep.describe(coffeeName.text.toString() + " · " + status + " · " + sleepAction +
            (sleepReason?.let { " · $it" } ?: ""))
        sleep.isEnabled = owner?.running == true && !active && !settingBusy &&
            owner.brewPreparationState == BrewPreparation.State.IDLE && idle?.sleepStateRaw == 0 &&
            owner.sleepNowState !in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP,
                SleepNowTracker.State.UNKNOWN) && owner.machineControlSafetyMessage == null

        val state = snapshot.scaleState
        val connecting = TaskShellDeviceStatus.connecting(state)
        val prefs = host.getSharedPreferences("devices", Context.MODE_PRIVATE)
        val remembered = ScaleSelectionPolicy.remembered(
            prefs.getString("scale", null), prefs.getString("scaleProtocol", null))
            ?.takeIf { BluetoothAdapter.checkBluetoothAddress(it.address) } != null
        val suppressed = prefs.getBoolean("scaleAutoSuppressed", false)
        val scaleLabel = host.getString(TaskShellDeviceStatus.scale(state, suppressed))
        val scaleLine = "● $scaleLabel"
        if (scaleStatus.text.toString() != scaleLine) {
            val colored = SpannableString(scaleLine)
            colored.setSpan(ForegroundColorSpan(if (state == DeviceState.READY)
                host.getColor(R.color.mobile_success) else style.muted), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            scaleStatus.text = colored
        }
        val connected = state == DeviceState.READY
        reconnect.visibility = if (connected) View.GONE else View.VISIBLE
        disconnect.visibility = if (connected) View.VISIBLE else View.GONE
        val actionId = when {
            state == DeviceState.UNSUPPORTED -> R.string.task_shell_action_select
            connecting -> R.string.task_shell_action_connecting
            !remembered || host.missingBle().isNotEmpty() -> R.string.task_shell_action_connect
            else -> R.string.task_shell_action_retry
        }
        val action = host.getString(actionId)
        reconnect.label(action)
        icon(reconnect, when {
            state == DeviceState.UNSUPPORTED -> "select"
            connecting -> "progress"
            actionId == R.string.task_shell_action_connect -> "new"
            else -> "retry"
        })
        reconnect.isEnabled = TaskShellDeviceStatus.canReconnect(state, active)
        reconnect.describe(scaleName.text.toString() + " · " + scaleLabel + " · " + action)
        val disconnectLabel = host.getString(R.string.task_shell_action_disconnect)
        disconnect.label(disconnectLabel); icon(disconnect, "disconnect")
        disconnect.isEnabled = !active && owner != null
        disconnect.describe(scaleName.text.toString() + " · " + scaleLabel + " · " + disconnectLabel)
    }
}
