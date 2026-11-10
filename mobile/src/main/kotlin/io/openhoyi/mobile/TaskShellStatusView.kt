package io.openhoyi.mobile

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
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
    onReconnectCoffee: () -> Unit = {},
) : LinearLayout(host) {
    private val style = TaskProductVisualStyle(host)
    private val chrome = TaskShellVisualStyle(host)
    private var mode: TaskNavigationMode? = null
    private val divider = View(host).apply { tag = "task-shell-divider"; setBackgroundColor(style.border) }
    private fun statusText(id: String, ink: Boolean = false) = TextView(host).apply {
        tag = id
        style.apply(this, TaskProductVisualStyle.Type(14, if (ink) 500 else 400, 20), !ink)
        setTextColor(if (ink) chrome.ink else chrome.muted)
        gravity = Gravity.CENTER; setSingleLine(false); maxLines = Int.MAX_VALUE; ellipsize = null
    }
    private fun group(id: String) = LinearLayout(host).apply {
        tag = id; orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, style.dp(4), 0, style.dp(4))
        background = chrome.cardBackground()
    }
    private val coffeeGroup = group("task-shell-coffee-group")
    private val scaleGroup = group("task-shell-scale-group")
    private val coffeeName = statusText("task-shell-coffee-name", true).apply { text = host.getString(R.string.home_coffee) }
    private val coffeeStatus = statusText("task-shell-coffee-status")
    private val scaleName = statusText("task-shell-scale-name", true).apply { text = host.getString(R.string.home_scale) }
    private val scaleStatus = statusText("task-shell-scale")
    private fun facts(id: String, name: TextView, status: TextView) = LinearLayout(host).apply {
        tag = id; orientation = VERTICAL; gravity = Gravity.CENTER
        minimumHeight = style.dp(48)
        isClickable = true; isFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        background = RippleDrawable(ColorStateList.valueOf(style.border), null,
            style.shape(android.graphics.Color.WHITE, radius = 8))
        name.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        status.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(name, LayoutParams(-1, -2)); addView(status, LayoutParams(-1, -2))
        setOnClickListener { host.openSettingsSection("devices") }
    }
    private val coffeeFacts = facts("task-shell-coffee-facts", coffeeName, coffeeStatus)
    private val scaleFacts = facts("task-shell-scale-facts", scaleName, scaleStatus)
    private fun action(id: String, click: () -> Unit) = Button(host).apply {
        tag = id; style.button(this); setOnClickListener { click() }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        val line = style.sp(20).toInt()
        if (android.os.Build.VERSION.SDK_INT >= 28) setLineHeight(line)
        else setLineSpacing((line - paint.fontMetricsInt.descent + paint.fontMetricsInt.ascent)
            .coerceAtLeast(0).toFloat(), 1f)
        isSingleLine = false; maxLines = Int.MAX_VALUE; ellipsize = null
        gravity = Gravity.CENTER; minHeight = style.dp(48); minimumHeight = style.dp(48)
        chrome.action(this)
        setPadding(style.dp(4), style.dp(4), style.dp(4), style.dp(4))
    }
    private val coffeeHint=statusText("task-shell-coffee-hint")
    private val coffeeRetry=action("task-shell-coffee-retry",onReconnectCoffee)
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
        setBackgroundColor(chrome.surface)
        coffeeGroup.addView(coffeeFacts, LayoutParams(-1, -2))
        coffeeFacts.addView(coffeeHint,LayoutParams(-1,-2))
        coffeeGroup.addView(coffeeRetry,actionParams())
        coffeeGroup.addView(sleep, actionParams())
        scaleGroup.addView(scaleFacts, LayoutParams(-1, -2))
        scaleGroup.addView(reconnect, actionParams())
        scaleGroup.addView(disconnect, actionParams())
        renderMode(TaskNavigationMode.BOTTOM)
        present(null)
    }

    private fun actionParams() = LayoutParams(-1, -2).apply {
        marginStart = style.dp(4); marginEnd = style.dp(4); topMargin = style.dp(4)
    }

    /** Inline icon follows the same centered line layout as its label, including RTL/wrapping. */
    private inner class ActionIconSpan(private val button: Button, private val drawable: Drawable) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int,
            fm: Paint.FontMetricsInt?): Int = style.dp(28)

        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float,
            top: Int, y: Int, bottom: Int, paint: Paint) {
            val metrics = paint.fontMetricsInt
            val center = y + (metrics.ascent + metrics.descent) / 2f
            canvas.save()
            canvas.translate(x + if (button.layoutDirection == View.LAYOUT_DIRECTION_RTL) style.dp(8) else 0,
                center - style.dp(20) / 2f)
            drawable.draw(canvas)
            canvas.restore()
        }
    }

    private fun icon(button: Button, kind: String) {
        if (button === sleep && sleepIcon == kind || button === reconnect && reconnectIcon == kind ||
            button === disconnect && disconnectIcon == kind) return
        val drawable = HomeVisualIcon.drawable(host, kind, button.currentTextColor,
            currentTint = { button.currentTextColor }, strokeDp = chrome.iconStrokeDp, solidAccents = chrome.solidIcons).apply { setBounds(0, 0, style.dp(20), style.dp(20)) }
        val label = button.text.toString().removePrefix("\uFFFC")
        button.text = SpannableString("\uFFFC$label").apply {
            setSpan(ActionIconSpan(button, drawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (button === sleep) sleepIcon = kind
        if (button === reconnect) reconnectIcon = kind
        if (button === disconnect) disconnectIcon = kind
    }
    private fun Button.label(value: String) {
        if (text.toString().removePrefix("\uFFFC") == value) return
        val span = (text as? Spanned)?.getSpans(0, text.length, ActionIconSpan::class.java)?.firstOrNull()
        text = if (span == null) value else SpannableString("\uFFFC$value").apply {
            setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    private fun Button.describe(value: String) {
        if (contentDescription?.toString() != value) contentDescription = value
        if (tooltipText?.toString() != value) tooltipText = value
    }

    private fun typography(rail: Boolean) {
        fun metrics(view: TextView, size: Int, lineSp: Int) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
            val line = style.sp(lineSp).toInt()
            if (android.os.Build.VERSION.SDK_INT >= 28) view.setLineHeight(line)
            else view.setLineSpacing((line - view.paint.fontMetricsInt.descent + view.paint.fontMetricsInt.ascent)
                .coerceAtLeast(0).toFloat(), 1f)
            if (view !is Button) view.minHeight = line
        }
        listOf(coffeeName, scaleName).forEach { metrics(it, if (rail) 16 else 14, if (rail) 24 else 20) }
        listOf(coffeeStatus, scaleStatus).forEach { metrics(it, if (rail) 15 else 14, if (rail) 22 else 20) }
        listOf(sleep, coffeeRetry, reconnect, disconnect).forEach { metrics(it, if (rail) 16 else 14, if (rail) 24 else 20) }
    }

    /** Rail eligibility must be independent of the currently attached fallback arrangement. */
    fun requiredRailHeight(width: Int): Int {
        val inner = (width - style.dp(16)).coerceAtLeast(0)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        fun natural(view: View, available: Int): Int {
            view.measure(MeasureSpec.makeMeasureSpec(available.coerceAtLeast(0), MeasureSpec.EXACTLY), heightSpec)
            return view.measuredHeight
        }
        fun groupHeight(facts: LinearLayout, name: TextView, status: TextView, vararg actions: Button): Int =
            style.dp(8) + maxOf(facts.minimumHeight, natural(facts,inner)) +
                actions.filter { it.visibility != GONE }.sumOf { style.dp(4)+natural(it,inner-style.dp(8)) }
        val temporaryRailTypography = mode != TaskNavigationMode.RAIL
        if (temporaryRailTypography) typography(true)
        return try {
            style.dp(29 + 8) + groupHeight(coffeeFacts, coffeeName, coffeeStatus, sleep, coffeeRetry) +
                groupHeight(scaleFacts, scaleName, scaleStatus, reconnect, disconnect)
        } finally { if (temporaryRailTypography) typography(false) }
    }

    private fun arrangeGroup(group: LinearLayout, facts: LinearLayout, vararg actions: Button) {
        // Reparent only on a presentation transition; the labels and action instances stay stable.
        listOf(facts, *actions).forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        group.removeAllViews()
        val rail = mode == TaskNavigationMode.RAIL
        group.orientation = if (rail) VERTICAL else HORIZONTAL
        group.gravity = Gravity.CENTER_VERTICAL
        if (rail) {
            facts.setPadding(0, 0, 0, 0)
            group.addView(facts, LayoutParams(-1, -2))
            actions.forEach { group.addView(it, actionParams()) }
        } else {
            facts.setPadding(style.dp(8), 0, style.dp(8), 0)
            group.addView(facts, LayoutParams(0, -2, 1f))
            actions.forEach { group.addView(it, LayoutParams(-2, -2).apply { marginEnd = style.dp(4) }) }
        }
    }

    fun renderMode(next: TaskNavigationMode) {
        if (mode == next) return
        mode = next; typography(next == TaskNavigationMode.RAIL); removeAllViews()
        arrangeGroup(coffeeGroup, coffeeFacts, sleep, coffeeRetry)
        arrangeGroup(scaleGroup, scaleFacts, reconnect, disconnect)
        orientation = if (next == TaskNavigationMode.RAIL) VERTICAL else HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(style.dp(8), if (next == TaskNavigationMode.RAIL) style.dp(12) else style.dp(4),
            style.dp(8), if (next == TaskNavigationMode.RAIL) style.dp(8) else style.dp(4))
        if (next == TaskNavigationMode.RAIL) {
            addView(divider, LayoutParams(-1, style.dp(1)))
            addView(coffeeGroup, LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
            addView(scaleGroup, LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
        } else {
            addView(coffeeGroup, LayoutParams(0, -2, 1f))
            addView(scaleGroup, LayoutParams(0, -2, 1f).apply { marginStart = style.dp(8) })
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (mode == TaskNavigationMode.BOTTOM) {
            val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
            val natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            fun wanted(name: TextView, status: TextView, vararg actions: Button): Int {
                name.measure(natural, natural); status.measure(natural, natural)
                val actionWidth = actions.filter { it.visibility != GONE }.sumOf { button ->
                    // A prior fallback width cap must not influence the next presentation decision.
                    val previousMaxWidth = button.maxWidth
                    try {
                        if (previousMaxWidth != Int.MAX_VALUE) button.maxWidth = Int.MAX_VALUE
                        button.measure(natural, natural)
                        button.measuredWidth.coerceAtLeast(style.dp(80))
                    } finally {
                        if (button.maxWidth != previousMaxWidth) button.maxWidth = previousMaxWidth
                    }
                }
                return maxOf(name.measuredWidth, status.measuredWidth) + actionWidth + style.dp(20)
            }
            val stacked = wanted(coffeeName, coffeeStatus, sleep, coffeeRetry) + wanted(scaleName, scaleStatus, reconnect, disconnect) + style.dp(8) > available
            val next = if (stacked) VERTICAL else HORIZONTAL
            if (orientation != next) orientation = next
            listOf(coffeeGroup, scaleGroup).forEachIndexed { index, group ->
                val old = group.layoutParams as LayoutParams
                val width = if (stacked) -1 else 0
                val weight = if (stacked) 0f else 1f
                val start = if (!stacked && index == 1) style.dp(8) else 0
                val top = if (stacked && index == 1) style.dp(4) else 0
                if (old.width != width || old.weight != weight || old.marginStart != start || old.topMargin != top)
                    group.layoutParams = LayoutParams(width, -2, weight).apply { marginStart = start; topMargin = top }
                val actionCap = (if (stacked) available else (available - style.dp(8)) / 2) / 2
                listOf(sleep, coffeeRetry, reconnect, disconnect).filter { it.parent === group }.forEach { button ->
                    val cap = actionCap.coerceAtLeast(style.dp(48))
                    if (button.maxWidth != cap) button.maxWidth = cap
                }
            }
        } else listOf(sleep, coffeeRetry, reconnect, disconnect).forEach { if (it.maxWidth != Int.MAX_VALUE) it.maxWidth = Int.MAX_VALUE }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
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
            snapshot.coffeeState, idle?.sleepStateRaw, snapshot.coffeeFailure,(snapshot.coffee as? IdleTelemetry)?.sleepStateRaw)
        val status = host.getString(statusId)
        val coffeeLine = "● $status"
        if (coffeeStatus.text.toString() != coffeeLine) {
            val colored = SpannableString(coffeeLine)
            colored.setSpan(ForegroundColorSpan(if (statusId == R.string.task_shell_machine_awake)
                host.getColor(R.color.mobile_success) else style.muted), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            coffeeStatus.text = colored
        }
        val sleepHint=SleepPresentation.hint(owner?.sleepNowState,idle?.sleepStateRaw,(snapshot.coffee as? IdleTelemetry)?.sleepStateRaw)
        val hint=owner?.coffeeConnectionHint ?: sleepHint
        coffeeHint.visibility=if(hint==null)View.GONE else View.VISIBLE
        if(hint!=null)coffeeHint.text=host.getString(hint)
        coffeeRetry.visibility=if(snapshot.coffeeState==DeviceState.READY)View.GONE else View.VISIBLE
        coffeeRetry.label(host.getString(if(TaskShellDeviceStatus.connecting(snapshot.coffeeState))R.string.task_shell_action_connecting else R.string.coffee_connection_retry))
        coffeeRetry.isEnabled=TaskShellDeviceStatus.canReconnect(snapshot.coffeeState,active)
        val asleep = statusId in setOf(R.string.task_shell_machine_asleep,R.string.sleep_status_last_confirmed)
        sleep.visibility=if(snapshot.coffeeState==DeviceState.READY || owner?.sleepNowState in setOf(SleepNowTracker.State.WRITING,SleepNowTracker.State.WAITING_ASLEEP,SleepNowTracker.State.UNKNOWN))View.VISIBLE else View.GONE
        val sleepAction = host.getString(if (asleep) R.string.task_shell_machine_asleep
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
        val scaleLabel = host.getString(if (state != DeviceState.READY && owner?.automaticScaleWaiting == true)
            R.string.device_state_connecting else TaskShellDeviceStatus.scale(state, suppressed))
        val scaleLine = "● $scaleLabel"
        if (scaleStatus.text.toString() != scaleLine) {
            val colored = SpannableString(scaleLine)
            colored.setSpan(ForegroundColorSpan(if (state == DeviceState.READY)
                host.getColor(R.color.mobile_success) else style.muted), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            scaleStatus.text = colored
        }
        coffeeFacts.contentDescription = "${coffeeName.text} · $status · ${host.getString(R.string.home_device_connection)}"
        scaleFacts.contentDescription = "${scaleName.text} · $scaleLabel · ${host.getString(R.string.home_device_connection)}"
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
