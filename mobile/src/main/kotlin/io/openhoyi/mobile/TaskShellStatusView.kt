package io.openhoyi.mobile

import android.view.Gravity
import android.view.View
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
    private val sleep = Button(host).apply {
        tag = "task-shell-sleep"; setOnClickListener { onSleep() }; style.button(this, outline = true)
        minHeight = style.dp(48); minimumHeight = style.dp(48)
    }
    private val scale = Button(host).apply {
        tag = "task-shell-scale"; setOnClickListener { onScale() }; style.button(this, outline = true)
        minHeight = style.dp(48); minimumHeight = style.dp(48)
        isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val reconnect = Button(host).apply {
        tag = "task-shell-scale-reconnect"; text = "↻"
        setOnClickListener {
            if (host.currentService?.snapshot?.scaleState == DeviceState.UNSUPPORTED) onScale()
            else onReconnectScale()
        }; style.button(this, outline = true)
        minHeight = style.dp(48); minimumHeight = style.dp(48)
    }
    private val disconnect = Button(host).apply {
        tag = "task-shell-scale-disconnect"; text = "×"
        setOnClickListener { onDisconnectScale() }; style.button(this, outline = true)
        minHeight = style.dp(48); minimumHeight = style.dp(48)
    }

    init { setBackgroundColor(style.surface); renderMode(TaskNavigationMode.BOTTOM) }

    fun renderMode(next: TaskNavigationMode) {
        if (mode == next) return
        mode = next
        removeAllViews()
        orientation = if (next == TaskNavigationMode.RAIL) VERTICAL else HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(style.dp(2), 0, style.dp(2), 0)
        if (next == TaskNavigationMode.RAIL) {
            addView(sleep, LayoutParams(-1, style.dp(48)))
            addView(scale, LayoutParams(-1, style.dp(48)))
            addView(reconnect, LayoutParams(-1, style.dp(48)))
            addView(disconnect, LayoutParams(-1, style.dp(48)))
        } else {
            addView(sleep, LayoutParams(-2, style.dp(48)))
            addView(scale, LayoutParams(0, style.dp(48), 1f).apply { marginStart = style.dp(4) })
            addView(reconnect, LayoutParams(style.dp(48), style.dp(48)))
            addView(disconnect, LayoutParams(style.dp(48), style.dp(48)))
        }
        showScaleLabel()
    }

    private fun showScaleLabel() {
        val label = if (mode == TaskNavigationMode.RAIL) "●  ⚖" else "●  $scaleLabel"
        if (scale.text.toString() != label) {
            val colored = SpannableString(label)
            colored.setSpan(ForegroundColorSpan(host.getColor(if (scaleState == DeviceState.READY)
                R.color.mobile_success else R.color.mobile_muted)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            scale.text = colored
        }
    }

    fun present(owner: MobileService?) {
        val snapshot = owner?.snapshot ?: MobileSnapshot()
        val idle = LiveTelemetry.machine(snapshot.coffee, snapshot.coffeeState, snapshot.coffeeAt,
            MobileMonotonicClock.elapsedRealtime()) as? IdleTelemetry
        val sleepLabel = when (idle?.sleepStateRaw) {
            0 -> host.getString(R.string.home_sleep_awake)
            1 -> host.getString(R.string.home_sleep_asleep)
            else -> host.getString(R.string.home_sleep_stale)
        }
        sleep.text = "☾"
        sleep.contentDescription = host.getString(R.string.home_sleep_button) + " · " + sleepLabel
        val active = owner?.manualShotActive == true || owner?.shotState in setOf(
            ExtractionState.STARTING, ExtractionState.RUNNING, ExtractionState.STOP_REQUESTED)
        val settingBusy = owner?.settingWriteState in setOf(SettingsWriteTracker.State.WRITING,
            SettingsWriteTracker.State.WAITING_READBACK, SettingsWriteTracker.State.UNKNOWN)
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
        reconnect.contentDescription = (if (snapshot.scaleState == DeviceState.UNSUPPORTED)
            host.getString(R.string.extraction_connect_scale) else host.getString(R.string.home_connect)) +
            " · " + host.getString(R.string.home_scale)
        disconnect.visibility = if (disconnected) View.GONE else View.VISIBLE
        disconnect.isEnabled = !active && owner != null
        disconnect.contentDescription = host.getString(R.string.home_scale_disconnect)
    }
}
