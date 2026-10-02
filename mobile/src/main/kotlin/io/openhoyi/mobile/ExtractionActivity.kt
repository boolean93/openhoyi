package io.openhoyi.mobile

import io.openhoyi.session.MachineWriteRecoveryState

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.util.TypedValue
import android.view.WindowInsets
import android.view.View
import android.widget.*
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.DeviceState
import io.openhoyi.session.StopReason
import java.util.Locale

/** A screen never owns BLE. Start requires an explicit confirmation; Stop is one tap. */
class ExtractionActivity : ThemedActivity() {
    internal lateinit var feedbackUi: BrewFeedbackDialog
        private set
    private val machineAlarms by lazy { MachineAlarms(this) }
    private val shotGate by lazy { ShotGate(this) }
    private val presetSlot: Int by lazy { intent.getIntExtra(PresetSlots.EXTRA_SLOT, 7).takeIf { it in 1..5 } ?: 7 }
    private var service: MobileService? = null
    private var bound = false
    private var visible = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var curveSummary: TextView
    private lateinit var deviceSummary: TextView
    private lateinit var shotSummary: TextView
    private lateinit var readiness: TextView
    private lateinit var chooseCurve: Button
    private lateinit var connectDevices: Button
    private lateinit var live: TextView
    private lateinit var elapsedValue: TextView
    private lateinit var pressureValue: TextView
    private lateinit var weightValue: TextView
    private lateinit var flowValue: TextView
    private lateinit var weightTarget: TextView
    private lateinit var preparationStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var alarmStatus: TextView
    private lateinit var chart: ShotChartView
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var prepare: Button
    private lateinit var cancelPrepare: Button
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MobileService.LocalBinder).service
            render()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; render() }
        override fun onBindingDied(name: ComponentName) { release(); render() }
    }
    private val refresh = object : Runnable {
        override fun run() { render(); if (visible) handler.postDelayed(this, 250) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        feedbackUi = BrewFeedbackDialog(this, savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.mobile_background)) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(28)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        stop = Button(this).apply {
            text = getString(R.string.extraction_stop)
            isAllCaps = false
            textSize = 18f
            minHeight = dp(56)
            setTextColor(getColor(android.R.color.white))
            background = HoyiUi.shape(this@ExtractionActivity, R.color.mobile_stop_button, 12)
            visibility = View.GONE
            setOnClickListener { service?.stopShot(); render() }
        }
        start = HoyiUi.button(this, root, getString(R.string.extraction_start), primary = true) { confirmStart() }
        (start.layoutParams as LinearLayout.LayoutParams).setMargins(dp(24), dp(4), dp(24), dp(8))
        root.addView(stop, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(24), dp(4), dp(24), dp(12))
        })
        HoyiUi.navigation(this, root, ExtractionActivity::class.java)
        setContentView(root)
        HoyiUi.header(this, content, getString(R.string.extraction_title),
            if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_subtitle) else getString(R.string.extraction_subtitle))
        val wide = HoyiUi.wide(this)
        val columns = if (wide) LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            content.addView(this, LinearLayout.LayoutParams(-1, -2))
        } else null
        val left = if (wide) LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            columns!!.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
        } else content
        val right = if (wide) LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            columns!!.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        } else content
        val stateCard = card(left)
        HoyiUi.label(this, stateCard, getString(R.string.extraction_overview), 19, true)
        curveSummary = text(stateCard, getString(R.string.extraction_curve_initial), 18, true)
        deviceSummary = text(stateCard, getString(R.string.extraction_devices_initial), 14)
        shotSummary = text(stateCard, getString(R.string.extraction_shot_initial), 14)
        readiness = text(stateCard, getString(R.string.extraction_waiting_service), 16, true).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = HoyiUi.shape(this@ExtractionActivity, R.color.mobile_accent_soft, 12)
        }
        chooseCurve = HoyiUi.button(this, stateCard, getString(R.string.extraction_choose_curve)) {
            startActivity(Intent(this, CurveActivity::class.java))
        }.apply { visibility = View.GONE }
        connectDevices = HoyiUi.button(this, stateCard, getString(R.string.extraction_connect_devices)) {
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }.apply { visibility = View.GONE }
        preparationStatus = text(stateCard, getString(R.string.extraction_prepare_initial), 14)
        notificationStatus = text(stateCard, "", 14)
        alarmStatus = text(stateCard, getString(R.string.home_alarm_initial), 14)
        val metrics = card(left)
        HoyiUi.label(this, metrics, getString(R.string.extraction_live_data), 19, true)
        fun metricRow(a: String, b: String): Pair<TextView, TextView> {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            metrics.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            fun cell(title: String): TextView {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    background = HoyiUi.shape(this@ExtractionActivity, R.color.mobile_accent_soft, 12)
                }
                row.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                HoyiUi.label(this, box, title, 13, muted = true)
                return HoyiUi.label(this, box, "—", 27, true).apply {
                    setPadding(0, dp(8), 0, 0)
                    setSingleLine(true)
                    setAutoSizeTextTypeUniformWithConfiguration(16, 27, 1, TypedValue.COMPLEX_UNIT_SP)
                }
            }
            return cell(a) to cell(b)
        }
        metricRow(getString(R.string.extraction_elapsed), getString(R.string.extraction_pressure)).also { elapsedValue = it.first; pressureValue = it.second }
        metricRow(getString(R.string.extraction_weight), getString(R.string.extraction_scale_flow)).also { weightValue = it.first; flowValue = it.second }
        live = text(metrics, "", 13)
        weightTarget = text(metrics, "", 15, true)
        val chartCard = card(right)
        HoyiUi.label(this, chartCard, getString(R.string.extraction_chart), 19, true)
        chart = ShotChartView(this)
        chartCard.addView(chart, LinearLayout.LayoutParams(-1, dp(if (HoyiUi.wide(this)) 260 else 220)).apply {
            topMargin = dp(12)
        })
        val actions = card(right)
        HoyiUi.label(this, actions, getString(R.string.extraction_prepare_heading), 19, true)
        prepare = button(actions, getString(R.string.extraction_prepare_button)) { confirmPrepare() }
        cancelPrepare = button(actions, getString(R.string.extraction_cancel_prepare)) { service?.cancelBrewPreparation()?.let(::toast); render() }
        render()
    }
    override fun onStart() {
        feedbackUi.start()
        super.onStart(); visible = true
        if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0)
        handler.post(refresh)
        if (!BuildConfig.MOCK_MODE) requestSafetyNotificationsOnce()
    }
    override fun onStop() {
        feedbackUi.leave(isChangingConfigurations)
        visible = false; handler.removeCallbacks(refresh)
        release(); super.onStop()
    }
    private fun release() { if (bound) { unbindService(connection); bound = false }; service = null }
    private fun notificationsAllowed(): Boolean = BuildConfig.MOCK_MODE || Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun requestSafetyNotificationsOnce() {
        if (notificationsAllowed()) return
        val prefs = getSharedPreferences("safety", MODE_PRIVATE)
        if (prefs.getBoolean("asked_for_notifications", false)) return
        prefs.edit().putBoolean("asked_for_notifications", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATIONS)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATIONS) render()
    }
    private fun selected(): CurveLibraryItem? {
        val library = (application as MobileApplication).curves
        val id = if (presetSlot == 7) getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)
        else PresetSlots.curveId(presetSlot, getSharedPreferences("presets", MODE_PRIVATE)
            .getString(PresetSlots.key(presetSlot), null))
        return id?.let(library::find)
    }
    private fun confirmStart() {
        val owner = service ?: return
        val library = (application as MobileApplication).curves
        val profile = selected()?.let { library.resolve(it.id, owner.snapshot.scaleState == DeviceState.READY, presetSlot) }
        val blocked = owner.tareStartBlock ?: shotGate.startBlock(profile, owner.snapshot.coffeeState, owner.snapshot.coffee, owner.snapshot.coffeeAt, owner.snapshot.scaleState,
            owner.snapshot.weightAt, SystemClock.elapsedRealtime(), owner.shotState,
            validated = profile?.let(library::validated) == true)
        if (blocked != null) { toast(blocked); render(); return }
        requireNotNull(profile)
        owner.studioStartBlock(profile)?.let { toast(it); render(); return }
        val effect = if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_effect)
            else getString(R.string.extraction_real_effect)
        AlertDialog.Builder(this).setTitle(if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_start_title) else getString(R.string.extraction_real_start_title))
            .setMessage(getString(R.string.extraction_start_message, profile.name, if (profile.parameters.slot == 7) getString(R.string.home_current_curve) else getString(R.string.extraction_shortcut_slot, profile.parameters.slot.toString()), profile.temperatureC.toString(), profile.maximumWaterMl.toString(), if (profile.targetHundredthsGram > 0) getString(R.string.extraction_target_weight_format, number(profile.targetHundredthsGram)) else getString(R.string.extraction_no_weight_target), effect) +
                if (notificationsAllowed()) "" else getString(R.string.extraction_notification_warning_suffix))
            .setPositiveButton(if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_start) else getString(R.string.extraction_real_start)) { _, _ ->
                owner.startShot(profile.id, profile.scaleMode, presetSlot)?.let(::toast)
                render()
            }
            .setNegativeButton(getString(R.string.machine_settings_cancel), null).show()
    }
    private fun confirmPrepare() {
        val owner = service ?: return
        val profile = selected()?.let {
            (application as MobileApplication).curves.resolve(it.id,
                owner.snapshot.scaleState == DeviceState.READY, presetSlot)
        } ?: run { toast(getString(R.string.extraction_choose_startable)); return }
        val corrected = owner.currentCorrectedBrewTemperature()
        val current = corrected?.let { number(it) + " °C" } ?: getString(R.string.settings_unknown)
        AlertDialog.Builder(this).setTitle(if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_prepare_title) else getString(R.string.extraction_real_prepare_title))
            .setMessage(getString(R.string.extraction_prepare_message, current, profile.temperatureC.toString()) +
                if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_prepare_effect)
                else getString(R.string.extraction_real_prepare_effect))
            .setPositiveButton(if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_prepare_start) else getString(R.string.extraction_real_prepare_start)) { _, _ ->
                owner.prepareBrew(profile.id, profile.scaleMode, presetSlot)?.let(::toast)
                render()
            }
            .setNegativeButton(getString(R.string.machine_settings_cancel), null).show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        feedbackUi.save(outState)
        super.onSaveInstanceState(outState)
    }
    private fun render() {
        if (!::readiness.isInitialized) return
        feedbackUi.update(service, visible)
        val owner = service
        val snapshot = owner?.snapshot ?: MobileSnapshot()
        val state = owner?.shotState ?: ExtractionState.IDLE
        val item = selected()
        val library = (application as MobileApplication).curves
        val profile = item?.let { library.resolve(it.id, snapshot.scaleState == DeviceState.READY, presetSlot) }
        val blocked = if (owner?.manualShotActive == true) getString(R.string.extraction_manual_block)
            else if (item != null && profile == null) getString(R.string.extraction_invalid_curve_block)
            else owner?.tareStartBlock ?: shotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt, snapshot.scaleState,
                snapshot.weightAt, SystemClock.elapsedRealtime(), state,
                validated = profile?.let(library::validated) == true)
        val unknownAdvice = if (state == ExtractionState.OUTCOME_UNKNOWN && snapshot.coffeeState != io.openhoyi.session.DeviceState.READY)
            getString(R.string.extraction_disconnect_advice_suffix) else
            owner?.manualSafetyMessage?.let { "\n$it" }.orEmpty()
        val studio = snapshot.settings?.flags?.and(0x04) == 0x04
        val corrected = owner?.currentCorrectedBrewTemperature()
        val temperatureReady = profile != null && corrected != null &&
            BrewPreparation.isAtTarget(corrected, profile.temperatureC)
        val preparation = owner?.brewPreparationState ?: BrewPreparation.State.IDLE
        val studioBlocked = if (owner != null && profile != null) owner.studioStartBlock(profile) else null
        val settingBusy = owner?.settingWriteState in setOf(
            SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
            SettingsWriteTracker.State.UNKNOWN)
        val sleepBusy = owner?.sleepNowState in setOf(SleepNowTracker.State.WRITING,
            SleepNowTracker.State.WAITING_ASLEEP, SleepNowTracker.State.UNKNOWN)
        val slotLabel = if (presetSlot == 7) "" else getString(R.string.extraction_slot_suffix, presetSlot.toString())
        curveSummary.show(getString(R.string.extraction_curve_summary, item?.name ?: getString(R.string.extraction_unselected), slotLabel))
        deviceSummary.show(getString(R.string.extraction_device_summary, DeviceStatusText.label(this, snapshot.coffeeState), DeviceStatusText.label(this, snapshot.scaleState)))
        shotSummary.show(getString(R.string.extraction_shot_summary, if (owner?.scalePreflight == true) getString(R.string.extraction_preflight_label) else if (owner?.manualShotActive == true) getString(R.string.extraction_manual_label) else shotLabel(state), owner?.stopReason?.let { getString(R.string.extraction_stop_reason_suffix, stopLabel(it)) } ?: ""))
        val guidance = when {
            owner?.manualShotActive == true -> getString(R.string.extraction_manual_guidance)
            owner?.scalePreflight == true -> getString(R.string.extraction_preflight_guidance)
            state == ExtractionState.STARTING -> getString(R.string.extraction_starting_guidance)
            state == ExtractionState.RUNNING -> getString(R.string.extraction_running_guidance)
            state == ExtractionState.STOP_REQUESTED -> getString(R.string.extraction_stopping_guidance)
            state == ExtractionState.OUTCOME_UNKNOWN -> getString(R.string.extraction_unknown_guidance)
            state == ExtractionState.ENDED_OBSERVED -> getString(R.string.extraction_ended_guidance)
            else -> owner?.machineControlSafetyMessage ?: blocked ?: studioBlocked ?: getString(R.string.extraction_ready_guidance)
        }
        readiness.show(guidance + unknownAdvice)
        readiness.setTextColor(getColor(if (state == ExtractionState.OUTCOME_UNKNOWN ||
            (state == ExtractionState.IDLE && (blocked != null || studioBlocked != null)))
            R.color.mobile_danger else R.color.mobile_text))
        chooseCurve.visibility = if (presetSlot == 7 && (item == null || profile == null || !library.canStart(item)) &&
            !ShotGate.active(state) && owner?.manualShotActive != true) View.VISIBLE else View.GONE
        chooseCurve.show(if (item == null) getString(R.string.extraction_choose_curve) else getString(R.string.home_curve_replace))
        val coffeeMissing = snapshot.coffeeState != DeviceState.READY
        val requiredScaleMissing = profile?.targetHundredthsGram?.let { it > 0 } == true &&
            snapshot.scaleState != DeviceState.READY
        connectDevices.visibility = if (!ShotGate.active(state) && owner?.manualShotActive != true &&
            (coffeeMissing || requiredScaleMissing))
            View.VISIBLE else View.GONE
        connectDevices.show(when {
            coffeeMissing && requiredScaleMissing -> getString(R.string.extraction_connect_both)
            coffeeMissing -> getString(R.string.extraction_connect_coffee)
            else -> getString(R.string.extraction_connect_scale)
        })
        preparationStatus.show(if (snapshot.settings == null) getString(R.string.extraction_mode_missing) else if (!studio) getString(R.string.extraction_cafe_mode) else
            getString(R.string.extraction_studio_summary, corrected?.let(::number) ?: "—", (profile?.temperatureC ?: "—").toString()) +
                when (preparation) {
                    BrewPreparation.State.IDLE -> if (temperatureReady) getString(R.string.extraction_temperature_ready) else getString(R.string.extraction_temperature_not_ready)
                    BrewPreparation.State.WRITING -> getString(R.string.extraction_prepare_writing)
                    BrewPreparation.State.WAITING_TEMP -> getString(R.string.extraction_prepare_waiting)
                    BrewPreparation.State.READY -> if (temperatureReady) getString(R.string.extraction_prepare_ready) else getString(R.string.extraction_prepare_off_target)
                    BrewPreparation.State.CANCELLING -> getString(R.string.extraction_prepare_cancelling)
                    BrewPreparation.State.CANCEL_WRITTEN -> getString(R.string.extraction_prepare_cancel_written)
                    BrewPreparation.State.FAILED -> getString(R.string.extraction_prepare_failed)
                    BrewPreparation.State.UNKNOWN -> getString(R.string.extraction_prepare_unknown)
                })
        notificationStatus.show(if (BuildConfig.MOCK_MODE || notificationsAllowed()) "" else
            getString(R.string.extraction_notification_warning))
        notificationStatus.visibility = if (notificationStatus.text.isEmpty()) View.GONE else View.VISIBLE
        alarmStatus.show(machineAlarms.describe(snapshot.alarmBits, snapshot.alarmAt,
            SystemClock.elapsedRealtime()))
        val now = SystemClock.elapsedRealtime()
        val frame = LiveTelemetry.machine(snapshot.coffee, snapshot.coffeeState, snapshot.coffeeAt, now)
        elapsedValue.show(if (frame is ExtractionTelemetry) "${frame.elapsedSeconds} s" else "—")
        pressureValue.show(when (frame) {
            is ExtractionTelemetry -> "${frame.pressureTenthsBar / 10.0} bar"
            is IdleTelemetry -> "${frame.brewPressureTenthsBar / 10.0} bar"
            else -> "—"
        })
        val scale = LiveTelemetry.scale(snapshot.weight, snapshot.scaleState, snapshot.weightAt, now)
        weightValue.show("${scale?.let { number(it.weightHundredthsGram) } ?: "—"} g")
        flowValue.show("${scale?.let { number(it.deviceFlowHundredths) } ?: "—"} g/s")
        live.show(getString(R.string.extraction_live_temperature, when (frame) {
            is ExtractionTelemetry -> number(frame.brewTemperatureHundredthsC)
            is IdleTelemetry -> number(frame.brewTemperatureHundredthsC)
            else -> "—"
        }))
        val target = if (state != ExtractionState.IDLE) owner?.activeShotTargetHundredthsGram
            else profile?.targetHundredthsGram
        weightTarget.show(if (owner?.manualShotActive != true && target != null && target > 0) {
            val progress = getString(R.string.extraction_weight_progress, scale?.let { number(it.weightHundredthsGram) } ?: "—", number(target))
            val advice = when {
                owner?.scalePreflight == true -> getString(R.string.extraction_preflight_weight_advice)
                owner?.stopReason == StopReason.TARGET_WEIGHT.name -> getString(R.string.extraction_target_stop_advice)
                owner?.stopReason == StopReason.MANUAL.name -> getString(R.string.extraction_manual_stop_advice)
                owner?.stopReason == StopReason.SCALE_UNAVAILABLE.name -> getString(R.string.extraction_scale_stop_advice)
                state == ExtractionState.RUNNING -> getString(R.string.extraction_auto_stop_advice)
                state == ExtractionState.STOP_REQUESTED -> getString(R.string.extraction_pending_stop_advice)
                else -> getString(R.string.extraction_weight_control_advice)
            }
            "$progress\n$advice"
        } else if (target == 0 && profile != null) getString(R.string.extraction_no_weight_advice) else "")
        val points = owner?.chartPoints ?: emptyList()
        if (chart.points != points) chart.points = points
        prepare.isEnabled = owner?.running == true && owner.machineControlSafetyMessage == null &&
            blocked == null && owner.machineSettingsFresh && studio && !temperatureReady &&
            !settingBusy && !sleepBusy &&
            preparation == BrewPreparation.State.IDLE
        cancelPrepare.isEnabled = owner?.running == true && owner?.manualShotActive != true &&
            (preparation != BrewPreparation.State.IDLE ||
                owner?.machineWriteRecoveryKind == MachineWriteRecoveryState.Kind.BREW_WAIT && preparation == BrewPreparation.State.IDLE) &&
            owner?.brewWaitCancelBlock == null
        cancelPrepare.text = if (preparation == BrewPreparation.State.CANCEL_WRITTEN)
            getString(R.string.extraction_cancel_prepare_again) else getString(R.string.extraction_cancel_prepare)
        start.isEnabled = owner?.running == true && owner.machineControlSafetyMessage == null &&
            blocked == null && studioBlocked == null &&
            !settingBusy && !sleepBusy
        val stopAction = StopActionPresentation.describe(state, snapshot.coffeeState, owner?.running == true)
        stop.isEnabled = stopAction.enabled
        stop.visibility = if (stopAction.visible) View.VISIBLE else View.GONE
        start.visibility = if (stopAction.visible) View.GONE else View.VISIBLE
        stop.text = if (owner?.scalePreflight == true) getString(R.string.home_start_cancel) else getString(stopAction.labelResource)
    }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun shotLabel(state: ExtractionState) = when (state) {
        ExtractionState.IDLE -> getString(R.string.machine_settings_tab_standby)
        ExtractionState.STARTING -> getString(R.string.extraction_state_starting)
        ExtractionState.RUNNING -> getString(R.string.extraction_state_running)
        ExtractionState.STOP_REQUESTED -> getString(R.string.extraction_state_stopping)
        ExtractionState.ENDED_OBSERVED -> getString(R.string.extraction_state_ended)
        ExtractionState.OUTCOME_UNKNOWN -> getString(R.string.extraction_state_unknown)
    }
    private fun stopLabel(reason: String) = when (reason) {
        StopReason.TARGET_WEIGHT.name -> getString(R.string.extraction_stop_target)
        StopReason.SCALE_UNAVAILABLE.name -> getString(R.string.extraction_stop_scale)
        StopReason.TARE_UNCONFIRMED.name -> getString(R.string.extraction_stop_tare)
        StopReason.START_CONDITIONS_CHANGED.name -> getString(R.string.extraction_stop_changed)
        StopReason.MANUAL.name -> getString(R.string.extraction_stop_manual)
        else -> getString(R.string.extraction_stop_unknown)
    }
    private fun number(value: Int): String = String.format(Locale.ROOT, "%.2f", value / 100.0)
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private companion object { const val NOTIFICATIONS = 31 }
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
        setPadding(0, dp(6), 0, dp(6)); if (bold) setTypeface(null, Typeface.BOLD); parent.addView(this)
    }
    private fun card(parent: LinearLayout) = HoyiUi.card(this, parent)
    private fun button(parent: LinearLayout, value: String, action: () -> Unit) =
        HoyiUi.button(this, parent, value, action = action)
}
