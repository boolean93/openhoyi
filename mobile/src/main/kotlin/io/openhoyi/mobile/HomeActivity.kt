package io.openhoyi.mobile

import io.openhoyi.session.MachineWriteRecoveryState

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker
import io.openhoyi.session.StandaloneTare
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.*
import io.openhoyi.bluetooth.DiscoveredDevice
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.DeviceRole
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import java.util.Locale

/** First native product screen: BLE connection, live values, and a selected captured curve. */
class HomeActivity : ThemedActivity() {
    internal lateinit var feedbackUi: BrewFeedbackDialog
        private set
    private val machineAlarms by lazy { MachineAlarms(this) }
    private val settingsPresentation by lazy { MachineSettingsPresentation(this) }
    private val firmwarePresentation by lazy { FirmwarePresentation(this) }
    private var service: MobileService? = null
    private var bound = false
    private var visible = false
    private var scanAfterBind = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var safetyWarning: TextView
    private lateinit var prominentAlarm: TextView
    private lateinit var acknowledgeManual: Button
    private lateinit var coffee: TextView
    private lateinit var coffeeDot: View
    private lateinit var brewTemperature: TextView
    private lateinit var brewPressure: TextView
    private lateinit var steamTemperature: TextView
    private lateinit var steamPressure: TextView
    private lateinit var leverStatus: TextView
    private lateinit var leverButton: Button
    private lateinit var sleepStatus: TextView
    private lateinit var sleepButton: Button
    private lateinit var emergencyStop: Button
    private lateinit var firmwareStatus: TextView
    private lateinit var alarmStatus: TextView
    private lateinit var scale: TextView
    private lateinit var scaleDot: View
    private lateinit var scaleWeight: TextView
    private lateinit var tareStatus: TextView
    private lateinit var selection: TextView
    private lateinit var brewButton: Button
    private lateinit var browseCurvesButton: Button
    private lateinit var presetButtons: List<Button>
    private lateinit var candidates: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var coffeeDisconnect: Button
    private lateinit var scaleDisconnect: Button
    private lateinit var tareButton: Button
    private var candidateKeys = emptyList<String>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MobileService.LocalBinder).service
            if (scanAfterBind) { scanAfterBind = false; service?.scan() }
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
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(getColor(R.color.mobile_background)) }
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
        emergencyStop = Button(this).apply {
            text = getString(R.string.home_stop)
            isAllCaps = false
            textSize = 18f
            minHeight = dp(56)
            setTextColor(getColor(android.R.color.white))
            background = HoyiUi.shape(this@HomeActivity, R.color.mobile_stop_button, 12)
            visibility = View.GONE
            setOnClickListener { service?.stopShot(); render() }
        }
        root.addView(emergencyStop, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(24), dp(4), dp(24), dp(12))
        })
        HoyiUi.navigation(this, root, HomeActivity::class.java)
        setContentView(root)
        val header = HoyiUi.header(this, content, "HOYI",
            if (BuildConfig.MOCK_MODE) getString(R.string.home_mock_subtitle) else getString(R.string.ui_brew))
        HoyiUi.button(this, header, getString(R.string.app_settings_title)) {
            startActivity(Intent(this, AppSettingsActivity::class.java))
        }.apply {
            maxWidth=dp(144)
            layoutParams=LinearLayout.LayoutParams(-2,-2).apply {marginStart=dp(12)}
        }
        safetyWarning = text(content, "", 17, true).apply {
            setTextColor(getColor(R.color.mobile_danger)); visibility = View.GONE
        }
        acknowledgeManual = button(content, getString(R.string.home_recovery_button)) {
            val recoveryKind = if (service?.manualSafetyMessage == null)
                service?.machineWriteRecoveryKind else null
            val title = when (recoveryKind) {
                MachineWriteRecoveryState.Kind.CUP_RESET -> getString(R.string.home_recovery_cups_title)
                MachineWriteRecoveryState.Kind.SETTING -> getString(R.string.home_recovery_setting_title)
                MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> getString(R.string.home_recovery_schedule_title)
                MachineWriteRecoveryState.Kind.SLEEP_NOW -> getString(R.string.home_recovery_sleep_title)
                MachineWriteRecoveryState.Kind.BREW_WAIT -> getString(R.string.home_recovery_preheat_title)
                else -> getString(R.string.home_recovery_shot_title)
            }
            val message = when (recoveryKind) {
                MachineWriteRecoveryState.Kind.CUP_RESET ->
                    getString(R.string.home_recovery_cups_message)
                MachineWriteRecoveryState.Kind.SETTING ->
                    getString(R.string.home_recovery_setting_message)
                MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE ->
                    getString(R.string.home_recovery_schedule_message)
                MachineWriteRecoveryState.Kind.SLEEP_NOW ->
                    getString(R.string.home_recovery_sleep_message)
                MachineWriteRecoveryState.Kind.BREW_WAIT ->
                    getString(R.string.home_recovery_preheat_message)
                else -> getString(R.string.home_recovery_shot_message)
            }
            AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(getString(R.string.home_recovery_clear)) { _, _ -> service?.acknowledgeManualSafety(); render() }
                .setNegativeButton(getString(R.string.machine_settings_cancel), null).show()
        }.apply { visibility = View.GONE }
        prominentAlarm = TextView(this).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = HoyiUi.shape(this@HomeActivity, R.color.mobile_accent_soft, 12, R.color.mobile_border)
            visibility = View.GONE
        }
        content.addView(prominentAlarm, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        val deviceCard = card(content, getString(R.string.home_device_connection))
        status = text(deviceCard, getString(R.string.home_not_connected_initial), 14)
        val deviceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        deviceCard.addView(deviceRow)
        val machineChip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val scaleChip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        deviceRow.addView(machineChip, LinearLayout.LayoutParams(0, -2, 1f))
        deviceRow.addView(scaleChip, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        coffeeDot = statusDot(machineChip)
        coffee = text(machineChip, getString(R.string.home_coffee_initial), 15, true)
        scaleDot = statusDot(scaleChip)
        scale = text(scaleChip, getString(R.string.home_scale_initial), 15, true)
        scanButton = button(deviceCard, getString(R.string.home_scan)) { enableAndScan() }
        candidates = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        deviceCard.addView(candidates)

        val wide = HoyiUi.wide(this)
        val workArea = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        content.addView(workArea)
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (wide) {
            workArea.addView(left, LinearLayout.LayoutParams(0, -2, .9f))
            workArea.addView(right, LinearLayout.LayoutParams(0, -2, 1.1f).apply { marginStart = dp(16) })
        } else {
            workArea.addView(right, LinearLayout.LayoutParams(-1, -2))
            workArea.addView(left, LinearLayout.LayoutParams(-1, -2))
        }
        val machineCard = card(left, getString(R.string.home_coffee))
        machineCard.addView(ImageView(this).apply {
            setImageResource(R.drawable.hoyi_machine)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.home_machine_description)
        }, LinearLayout.LayoutParams(-1, dp(if (wide) 190 else 150)))
        firmwareStatus = text(machineCard, getString(R.string.home_firmware_initial), 14)
        alarmStatus = text(machineCard, getString(R.string.home_alarm_initial), 14)
        leverStatus = text(machineCard, getString(R.string.settings_lever_missing), 14)
        sleepStatus = text(machineCard, getString(R.string.home_sleep_initial), 14)
        val machineControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val controlsToggle = button(machineCard, getString(R.string.home_controls_expand)) {}
        controlsToggle.setOnClickListener {
            machineControls.visibility = if (machineControls.visibility == View.GONE) View.VISIBLE else View.GONE
            controlsToggle.text = if (machineControls.visibility == View.VISIBLE) getString(R.string.home_controls_collapse) else getString(R.string.home_controls_expand)
        }
        machineCard.addView(machineControls)
        leverButton = button(machineControls, getString(R.string.home_lever_button)) { chooseLeverMode() }
        sleepButton = button(machineControls, getString(R.string.home_sleep_button)) { confirmSleepNow() }
        button(machineControls, getString(R.string.home_settings_button)) { startActivity(Intent(this, MachineSettingsActivity::class.java)) }
        coffeeDisconnect = button(machineControls, getString(R.string.home_coffee_disconnect)) { service?.disconnect(DeviceRole.COFFEE) }
        val scaleCard = card(left, getString(R.string.home_scale))
        scaleWeight = text(scaleCard, "— g", 27, true)
        tareStatus = text(scaleCard, getString(R.string.home_tare_initial), 14)
        tareButton = button(scaleCard, getString(R.string.home_tare_button)) { service?.tareScale()?.let(::toast); render() }
        scaleDisconnect = button(scaleCard, getString(R.string.home_scale_disconnect)) { service?.disconnect(DeviceRole.BOOKOO) }

        val metrics = card(right, getString(R.string.home_live_status))
        fun metricRow(first: String, second: String): Pair<TextView, TextView> {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            metrics.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            fun item(label: String): TextView {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = HoyiUi.shape(this@HomeActivity, R.color.mobile_accent_soft, 12)
                }
                row.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                HoyiUi.label(this, box, label, 13, muted = true)
                return HoyiUi.label(this, box, "—", 25, true).apply { setPadding(0, dp(8), 0, 0) }
            }
            return item(first) to item(second)
        }
        metricRow(getString(R.string.home_brew_temperature), getString(R.string.home_brew_pressure)).also { brewTemperature = it.first; brewPressure = it.second }
        metricRow(getString(R.string.home_steam_temperature), getString(R.string.home_steam_pressure)).also { steamTemperature = it.first; steamPressure = it.second }
        val curveCard = card(right, getString(R.string.home_current_curve))
        val curveActionRow = if (wide) LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            curveCard.addView(this)
        } else curveCard
        selection = text(curveActionRow, getString(R.string.home_curve_missing), 19, true)
        if (wide) selection.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        brewButton = button(curveActionRow, getString(R.string.home_curve_select), primary = true) {
            val library = (application as MobileApplication).curves
            val item = getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)?.let(library::find)
            startActivity(Intent(this, if (item != null && library.canStart(item))
                ExtractionActivity::class.java else CurveActivity::class.java))
        }
        if (wide) brewButton.layoutParams = LinearLayout.LayoutParams(dp(236), -2).apply {
            marginStart = dp(16)
        }
        browseCurvesButton = button(curveCard, getString(R.string.home_curve_browse)) { startActivity(Intent(this, CurveActivity::class.java)) }
        val presets = card(right, getString(R.string.home_quick_curves))
        HoyiUi.label(this, presets, getString(R.string.home_quick_curves_hint), 13, muted = true)
        val buttons = mutableListOf<Button>()
        val columns = if (resources.configuration.screenWidthDp >= 1000) 2 else 1
        (1..5).chunked(columns).forEach { slots ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            presets.addView(row)
            slots.forEach { slot ->
                val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (slot != slots.last()) marginEnd = dp(10)
                })
                buttons += button(cell, getString(R.string.home_slot, slot.toString())) {
                    startActivity(Intent(this, ExtractionActivity::class.java).putExtra(PresetSlots.EXTRA_SLOT, slot))
                }
            }
            if (slots.size < columns) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        }
        presetButtons = buttons
        right.removeView(curveCard)
        right.addView(curveCard, 0)
        val tools = card(content, getString(R.string.home_records))
        button(tools, getString(R.string.extraction_title)) { startActivity(Intent(this, ExtractionActivity::class.java)) }
        button(tools, getString(R.string.dose_title)) { startActivity(Intent(this, BeanPreparationActivity::class.java)) }
        button(tools, getString(R.string.scale_title)) { startActivity(Intent(this, ScaleActivity::class.java)) }
        button(tools, getString(R.string.home_shutdown)) {
            val owner = service
            owner?.shutdown()
            if (owner?.running == true) toast(getString(R.string.home_shutdown_busy)) else release()
            render()
        }
        render()
    }
    override fun onStart() {
        feedbackUi.start()
        super.onStart()
        visible = true
        if (!startRememberedScaleService()) bindExisting()
        handler.post(refresh)
    }
    override fun onStop() {
        feedbackUi.leave(isChangingConfigurations)
        visible = false; handler.removeCallbacks(refresh)
        release(); super.onStop()
    }
    private fun bindExisting() { if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0) }
    private fun startRememberedScaleService(): Boolean {
        if (BuildConfig.MOCK_MODE) {
            startService(Intent(this, MobileService::class.java))
            if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, Context.BIND_AUTO_CREATE)
            return bound
        }
        val prefs = getSharedPreferences("devices", MODE_PRIVATE)
        val selection = io.openhoyi.session.ScaleSelectionPolicy.remembered(prefs.getString("scale", null), prefs.getString("scaleProtocol", null)) ?: return false
        val address = selection.address
        if (!BluetoothAdapter.checkBluetoothAddress(address) || missingBle().isNotEmpty())
            return false
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        return try {
            if (!adapter.isEnabled) return false
            startForegroundService(Intent(this, MobileService::class.java).setAction(MobileService.AUTO_SCALE))
            if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, Context.BIND_AUTO_CREATE)
            bound
        } catch (_: SecurityException) { false }
        catch (_: RuntimeException) { false }
    }
    private fun release() { if (bound) { unbindService(connection); bound = false }; service = null }
    private fun missingBle(): List<String> {
        val required = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }
    private fun enableAndScan() {
        if (BuildConfig.MOCK_MODE) {
            if (service?.running == true) service?.scan()
            else {
                startService(Intent(this, MobileService::class.java))
                scanAfterBind = true
                if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, Context.BIND_AUTO_CREATE)
            }
            return
        }
        val missing = missingBle()
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), PERMISSIONS); return }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) { toast(getString(R.string.home_bluetooth_unsupported)); return }
        try {
            if (!adapter.isEnabled) { startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE); return }
            if (service?.running == true) { service?.scan(); return }
            release()
            startForegroundService(Intent(this, MobileService::class.java))
            scanAfterBind = true
            bound = bindService(Intent(this, MobileService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) { scanAfterBind = false; toast(getString(R.string.home_bind_failed)) }
        } catch (_: SecurityException) { toast(getString(R.string.home_permission_expired)) }
        catch (error: RuntimeException) { toast(getString(R.string.home_start_failed, error.javaClass.simpleName)) }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS) {
            if (missingBle().isEmpty()) enableAndScan() else toast(getString(R.string.home_permission_required))
        }
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ENABLE && resultCode == RESULT_OK) enableAndScan()
        if (requestCode == EXPORT && resultCode == RESULT_OK) data?.data?.let { (application as MobileApplication).export(it) }
    }
    private fun choose(device: DiscoveredDevice) {
        if (BuildConfig.MOCK_MODE) {
            if (device.candidateRole == DeviceRole.BOOKOO) device.scaleProtocolId?.let { service?.connectScale(device.address, it) }
            else service?.connectCoffee(device.address, "000000")
            render()
            return
        }
        if (device.candidateRole == DeviceRole.BOOKOO) { device.scaleProtocolId?.let { service?.connectScale(device.address, it) }; return }
        if (service?.connectRememberedCoffee(device.address) == true) return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6)); hint = getString(R.string.home_password_hint)
            isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.home_connect_coffee_title))
            .setMessage(getString(R.string.home_password_message))
            .setView(input).setPositiveButton(getString(R.string.home_connect), null).setNegativeButton(getString(R.string.machine_settings_cancel), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = input.text.toString()
                if (!password.matches(Regex("[0-9]{6}"))) input.error = getString(R.string.home_password_error)
                else { service?.connectCoffee(device.address, password); input.text.clear(); dialog.dismiss() }
            }
        }
        dialog.setOnDismissListener { input.text.clear() }
        dialog.show()
    }
    private fun chooseLeverMode() {
        if (BuildConfig.MOCK_MODE) { toast(getString(R.string.home_lever_mock)); return }
        val modes = listOf(
            MachineSettingChange.LeverMode(false, false),
            MachineSettingChange.LeverMode(true, false),
            MachineSettingChange.LeverMode(true, true),
        )
        AlertDialog.Builder(this).setTitle(getString(R.string.home_lever_choose_title))
            .setItems(arrayOf(getString(R.string.home_lever_manual),
                getString(R.string.home_lever_pressure), getString(R.string.home_lever_flow))) { _, index ->
                val change = modes[index]
                AlertDialog.Builder(this).setTitle(getString(R.string.home_lever_confirm_title))
                    .setMessage(getString(R.string.home_lever_confirmation_message, settingsPresentation.change(change)))
                    .setPositiveButton(getString(R.string.machine_settings_send)) { _, _ -> service?.changeMachineSetting(change)?.let(::toast); render() }
                    .setNegativeButton(getString(R.string.machine_settings_cancel), null).show()
            }.show()
    }
    private fun confirmSleepNow() {
        if (BuildConfig.MOCK_MODE) { toast(getString(R.string.home_sleep_mock)); return }
        AlertDialog.Builder(this).setTitle(getString(R.string.home_sleep_confirm_title))
            .setMessage(getString(R.string.home_sleep_confirm_message))
            .setPositiveButton(getString(R.string.home_sleep_send)) { _, _ -> service?.enterSleepNow()?.let(::toast); render() }
            .setNegativeButton(getString(R.string.machine_settings_cancel), null).show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        feedbackUi.save(outState)
        super.onSaveInstanceState(outState)
    }
    private fun render() {
        if (!::status.isInitialized) return
        feedbackUi.update(service, visible)
        val owner = service
        val s = owner?.snapshot ?: MobileSnapshot()
        val now = SystemClock.elapsedRealtime()
        val running = owner?.running == true
        val warning = ShotSafetyAlert.resource(owner?.shotState ?: ExtractionState.IDLE, s.coffeeState)?.let { getString(it) }
            ?: owner?.manualSafetyMessage ?: owner?.machineWriteSafetyMessage
        safetyWarning.visibility = if (warning == null) View.GONE else View.VISIBLE
        safetyWarning.show(warning.orEmpty())
        acknowledgeManual.visibility = if (owner?.manualSafetyMessage == null &&
            owner?.machineWriteAcknowledgementAvailable != true) View.GONE else View.VISIBLE
        acknowledgeManual.isEnabled = if (owner?.manualSafetyMessage != null)
            owner.shotRecoveryClearBlock == null else owner?.machineWriteAcknowledgementAvailable == true
        val alarmBanner = machineAlarms.banner(s.alarmBits, s.alarmAt, now)
        prominentAlarm.visibility = if (alarmBanner == null) View.GONE else View.VISIBLE
        prominentAlarm.show(alarmBanner?.message.orEmpty())
        prominentAlarm.setTextColor(getColor(if (alarmBanner?.blocking == true)
            R.color.mobile_danger else R.color.mobile_accent))
        status.show(if (owner?.manualShotActive == true)
            getString(R.string.home_manual_shot_status) else if (running) s.messageForDisplay { id, args -> getString(id, *args) } else getString(R.string.home_scan_service_hint))
        val bothReady = s.coffeeState == DeviceState.READY && s.scaleState == DeviceState.READY
        status.visibility = if (bothReady && !s.scanning && owner?.manualShotActive != true)
            View.GONE else View.VISIBLE
        scanButton.visibility = if (bothReady && !s.scanning) View.GONE else View.VISIBLE
        scanButton.isEnabled = !s.scanning
        val shotActive = owner?.shotState?.let(ShotGate::active) == true
        val stopAction = StopActionPresentation.describe(owner?.shotState ?: ExtractionState.IDLE,
            s.coffeeState, running)
        emergencyStop.visibility = if (stopAction.visible) View.VISIBLE else View.GONE
        emergencyStop.isEnabled = stopAction.enabled
        emergencyStop.show(if (owner?.scalePreflight == true) getString(R.string.home_start_cancel) else getString(stopAction.labelResource))
        val settingBusy = owner?.settingWriteState in setOf(
            SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
            SettingsWriteTracker.State.UNKNOWN)
        val preparationIdle = owner?.brewPreparationState == BrewPreparation.State.IDLE
        val freshAwakeIdle = s.coffee is IdleTelemetry && s.coffee.sleepStateRaw == 0 &&
            s.coffeeAt?.let { now >= it && now - it <= 1500 } == true
        leverButton.isEnabled = running && !shotActive && owner?.manualShotActive != true &&
            owner?.machineControlSafetyMessage == null && !settingBusy &&
            owner?.sleepNowState != SleepNowTracker.State.UNKNOWN && preparationIdle &&
            s.coffeeState == DeviceState.READY && owner?.machineSettingsFresh == true && freshAwakeIdle
        leverStatus.show(settingsPresentation.leverMode(s.settings) +
            when (owner?.pendingSetting) {
                is MachineSettingChange.LeverMode -> " · " + when (owner?.settingWriteState) {
                    SettingsWriteTracker.State.WRITING -> getString(R.string.home_setting_writing)
                    SettingsWriteTracker.State.WAITING_READBACK -> getString(R.string.home_setting_waiting)
                    SettingsWriteTracker.State.CONFIRMED -> getString(R.string.home_setting_confirmed)
                    SettingsWriteTracker.State.FAILED -> getString(R.string.home_setting_failed)
                    SettingsWriteTracker.State.UNKNOWN -> getString(R.string.home_setting_unknown)
                    SettingsWriteTracker.State.RECONCILED -> getString(R.string.home_setting_reconciled)
                    SettingsWriteTracker.State.IDLE, null -> ""
                }
                else -> ""
            })
        firmwareStatus.show(firmwarePresentation.describe(owner?.coffeeFirmware,s.coffeeState,BuildConfig.MOCK_MODE))
        coffeeDisconnect.isEnabled = running && !shotActive && owner?.manualShotActive != true && s.coffeeState != DeviceState.DISCONNECTED
        scaleDisconnect.isEnabled = running && !shotActive && owner?.manualShotActive != true && s.scaleState != DeviceState.DISCONNECTED
        tareButton.isEnabled = running && !shotActive && owner?.manualShotActive != true && s.scaleState == DeviceState.READY &&
            owner?.tareState !in setOf(StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO)
        tareStatus.show(getString(R.string.home_tare_prefix) + when (owner?.tareState ?: StandaloneTare.State.IDLE) {
            StandaloneTare.State.IDLE -> getString(R.string.home_tare_idle)
            StandaloneTare.State.WRITING -> getString(R.string.home_tare_writing)
            StandaloneTare.State.WAITING_ZERO -> getString(R.string.home_tare_waiting)
            StandaloneTare.State.CONFIRMED -> getString(R.string.home_tare_confirmed)
            StandaloneTare.State.FAILED -> getString(R.string.home_setting_failed)
            StandaloneTare.State.UNKNOWN -> getString(R.string.home_tare_unknown)
        })
        val liveMachine = LiveTelemetry.machine(s.coffee, s.coffeeState, s.coffeeAt, now)
        val coffeeFresh = liveMachine != null
        val idle = liveMachine as? IdleTelemetry
        val sleepBusy = owner?.sleepNowState in setOf(SleepNowTracker.State.WRITING,
            SleepNowTracker.State.WAITING_ASLEEP, SleepNowTracker.State.UNKNOWN)
        sleepButton.isEnabled = running && !shotActive && owner?.manualShotActive != true &&
            owner?.machineControlSafetyMessage == null && !settingBusy && !sleepBusy && preparationIdle && coffeeFresh &&
            idle?.sleepStateRaw == 0
        val reportedSleep = if (!coffeeFresh) getString(R.string.home_sleep_stale) else when (idle?.sleepStateRaw) {
            0 -> getString(R.string.home_sleep_awake)
            1 -> getString(R.string.home_sleep_asleep)
            else -> getString(R.string.settings_unknown)
        }
        val sleepProgress = when (owner?.sleepNowState) {
            SleepNowTracker.State.WRITING -> getString(R.string.home_sleep_writing_suffix)
            SleepNowTracker.State.WAITING_ASLEEP -> getString(R.string.home_sleep_waiting_suffix)
            SleepNowTracker.State.CONFIRMED -> if (idle?.sleepStateRaw == 1) getString(R.string.home_sleep_confirmed_suffix) else ""
            SleepNowTracker.State.FAILED -> getString(R.string.home_sleep_failed_suffix)
            SleepNowTracker.State.UNKNOWN -> getString(R.string.home_sleep_unknown_suffix)
            SleepNowTracker.State.RECONCILED -> getString(R.string.home_sleep_reconciled_suffix)
            else -> ""
        }
        sleepStatus.show(getString(R.string.home_sleep_summary, reportedSleep, sleepProgress))
        alarmStatus.show(machineAlarms.describe(s.alarmBits, s.alarmAt, now))
        val compactStatus = resources.configuration.screenWidthDp < 400
        coffee.show(if (compactStatus) getString(R.string.home_coffee_compact, if (coffeeFresh) getString(R.string.home_live) else label(s.coffeeState))
            else getString(R.string.home_coffee_status, label(s.coffeeState), if (coffeeFresh) getString(R.string.home_live_suffix) else ""))
        coffeeDot.showDot(when (s.coffeeState) {
            DeviceState.READY -> R.color.mobile_success
            DeviceState.FAILED, DeviceState.UNSUPPORTED -> R.color.mobile_danger
            else -> R.color.mobile_muted
        })
        when (val frame = liveMachine) {
            is IdleTelemetry -> {
                brewTemperature.show("${number(frame.brewTemperatureHundredthsC)} °C")
                brewPressure.show("${frame.brewPressureTenthsBar / 10.0} bar")
                steamTemperature.show("${number(frame.steamTemperatureHundredthsC)} °C")
                steamPressure.show("${frame.steamPressureTenthsBar / 10.0} bar")
            }
            is ExtractionTelemetry -> {
                brewTemperature.show("${number(frame.brewTemperatureHundredthsC)} °C")
                brewPressure.show("${frame.pressureTenthsBar / 10.0} bar")
                steamTemperature.show("—")
                steamPressure.show("—")
            }
            else -> listOf(brewTemperature, brewPressure, steamTemperature, steamPressure).forEach { it.show("—") }
        }
        val liveScale = LiveTelemetry.scale(s.scaleObservation, s.scaleState, now)?.hundredthsGram
            ?: LiveTelemetry.scale(s.weight, s.scaleState, s.weightAt, now)?.weightHundredthsGram
        scale.show(if (compactStatus) getString(R.string.home_scale_compact, if (liveScale != null) getString(R.string.home_live) else label(s.scaleState))
            else getString(R.string.home_scale_status, label(s.scaleState), if (liveScale != null) getString(R.string.home_live_suffix) else ""))
        scaleDot.showDot(when (s.scaleState) {
            DeviceState.READY -> R.color.mobile_success
            DeviceState.FAILED, DeviceState.UNSUPPORTED -> R.color.mobile_danger
            else -> R.color.mobile_muted
        })
        val liveDeviceFlow = LiveTelemetry.scale(s.scaleObservation, s.scaleState, now)?.let {
            it.deviceFlowHundredths.takeIf { _ -> it.capabilities.deviceFlow }
        } ?: LiveTelemetry.scale(s.weight, s.scaleState, s.weightAt, now)?.deviceFlowHundredths
        scaleWeight.show("${liveScale?.let(::number) ?: "—"} g" +
            "  ·  ${liveDeviceFlow?.let(::number) ?: "—"} g/s")
        val library = (application as MobileApplication).curves
        val selected = getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)?.let(library::find)
        selection.show(selected?.let { getString(R.string.home_selected_curve, it.name, getString(if (!library.canStart(it)) R.string.home_curve_readonly else R.string.home_curve_startable)) } ?: getString(R.string.home_curve_missing))
        brewButton.show(when {
            selected == null -> getString(R.string.home_curve_select)
            !library.canStart(selected) -> getString(R.string.home_curve_replace)
            else -> getString(R.string.home_curve_prepare)
        })
        browseCurvesButton.visibility = if (selected == null) View.GONE else View.VISIBLE
        val presetPrefs = getSharedPreferences("presets", MODE_PRIVATE)
        presetButtons.forEachIndexed { index, button ->
            val slot = index + 1
            val id = PresetSlots.curveId(slot, presetPrefs.getString(PresetSlots.key(slot), null))
            val item = library.find(id)
            val label = getString(R.string.home_slot_curve, slot.toString(), item?.name ?: id)
            if (button.text.toString() != label) button.text = label
        }
        val keys = s.candidates.map { "${it.address}:${it.advertisedName}:${it.scaleProtocolId}" }
        if (keys != candidateKeys) {
            candidateKeys = keys; candidates.removeAllViews()
            s.candidates.forEach { device ->
                if (device.scaleProtocolId == io.openhoyi.session.FelicitaReadOnlyScaleProtocolAdapter.id)
                    HoyiUi.label(this, candidates, getString(R.string.scale_read_only_live), 14, muted = true)
                button(candidates, getString(R.string.home_candidate, device.advertisedName, getString(if (device.candidateRole == DeviceRole.COFFEE) R.string.home_coffee else R.string.home_scale))) { choose(device) }
            }
        }
    }
    private fun label(state: DeviceState): String = when (state) {
        DeviceState.READY -> getString(R.string.home_connected); DeviceState.DISCONNECTED -> getString(R.string.device_state_disconnected); DeviceState.FAILED -> getString(R.string.device_state_failed)
        DeviceState.UNSUPPORTED -> getString(R.string.home_firmware_readonly); else -> getString(R.string.device_state_connecting)
    }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun number(hundredths: Int) = String.format(Locale.ROOT, "%.2f", hundredths / 100.0)
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun dotShape(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(getColor(color))
    }
    private fun View.showDot(colorResource: Int) {
        val color = getColor(colorResource)
        val dot = background as? GradientDrawable
        // Replacing an unchanged background emits native accessibility subtree events.
        if (dot == null) background = dotShape(colorResource)
        else if (dot.color?.defaultColor != color) dot.setColor(color)
    }
    private fun statusDot(parent: LinearLayout): View = View(this).apply {
        background = dotShape(R.color.mobile_muted)
        parent.addView(this, LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(8) })
    }
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
            setPadding(0, dp(6), 0, dp(6)); if (bold) setTypeface(null, Typeface.BOLD)
            parent.addView(this)
        }
    private fun card(parent: LinearLayout, title: String) = HoyiUi.card(this, parent, title)
    private fun button(parent: LinearLayout, value: String, primary: Boolean = false, action: () -> Unit) =
        HoyiUi.button(this, parent, value, primary = primary, action = action)
    companion object { private const val PERMISSIONS = 12; private const val ENABLE = 13; private const val EXPORT = 14 }
}
