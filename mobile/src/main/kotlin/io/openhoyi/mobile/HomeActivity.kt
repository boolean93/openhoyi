package io.openhoyi.mobile

import io.openhoyi.session.MachineWriteRecoveryState

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker
import io.openhoyi.session.StandaloneTare
import android.Manifest
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
    private lateinit var devicePanel: LinearLayout
    private var deviceDialog: AlertDialog? = null
    private lateinit var presetPanel: LinearLayout
    private var presetDialog: AlertDialog? = null
    private lateinit var stagePreview: CurveStageView
    private lateinit var previewNote: TextView
    private lateinit var previewUnavailable: TextView
    private lateinit var stageFallback: ScrollView
    private lateinit var stageFallbackText: TextView
    private var textStagePreview = false
    private lateinit var beanSummary: TextView
    private lateinit var temperatureSummary: TextView
    private lateinit var usageSummary: TextView
    private lateinit var recentCurves: HomeRecentCurves
    private var selectionDetails = ""
    private lateinit var quickPresets: Button
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
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
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
        val header = HoyiUi.header(this, content, getString(R.string.task_brew_title),
            if (BuildConfig.MOCK_MODE) getString(R.string.home_mock_subtitle) else getString(R.string.ui_brew))
        HoyiUi.button(this, header, getString(R.string.home_device_connection)) {
            showDevicePanel()
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
            typeface = TaskTypography.face(context, 700)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = HoyiUi.shape(this@HomeActivity, R.color.mobile_accent_soft, 12, R.color.mobile_border)
            visibility = View.GONE
        }
        content.addView(prominentAlarm, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // Device controls retain their live views and write gates in an explicit panel.
        // Only this auxiliary panel scrolls; the main task surface remains fixed.
        devicePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val deviceCard = devicePanel
        button(deviceCard, getString(R.string.app_settings_title)) {
            startActivity(Intent(this, AppSettingsActivity::class.java))
        }
        button(deviceCard, getString(R.string.extraction_title)) {
            startActivity(Intent(this, ExtractionActivity::class.java))
        }
        status = text(content, getString(R.string.home_not_connected_initial), 13)
        val deviceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(deviceRow)
        val machineChip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val scaleChip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        deviceRow.addView(machineChip, LinearLayout.LayoutParams(0, -2, 1f))
        deviceRow.addView(scaleChip, LinearLayout.LayoutParams(0, -2, 1f))
        coffeeDot = statusDot(machineChip)
        coffee = text(machineChip, getString(R.string.home_coffee_initial), 14, true)
        scaleDot = statusDot(scaleChip)
        scale = text(scaleChip, getString(R.string.home_scale_initial), 14, true)
        scanButton = button(deviceCard, getString(R.string.home_scan)) { enableAndScan() }
        candidates = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        deviceCard.addView(candidates)
        firmwareStatus = text(deviceCard, getString(R.string.home_firmware_initial), 14)
        alarmStatus = text(deviceCard, getString(R.string.home_alarm_initial), 14)
        leverStatus = text(deviceCard, getString(R.string.settings_lever_missing), 14)
        sleepStatus = text(deviceCard, getString(R.string.home_sleep_initial), 14)
        leverButton = button(deviceCard, getString(R.string.home_lever_button)) { chooseLeverMode() }
        sleepButton = button(deviceCard, getString(R.string.home_sleep_button)) { confirmSleepNow() }
        button(deviceCard, getString(R.string.home_settings_button)) { startActivity(Intent(this, MachineSettingsActivity::class.java)) }
        coffeeDisconnect = button(deviceCard, getString(R.string.home_coffee_disconnect)) { service?.disconnect(DeviceRole.COFFEE) }
        scaleWeight = text(deviceCard, "— g", 27, true)
        tareStatus = text(deviceCard, getString(R.string.home_tare_initial), 14)
        tareButton = button(deviceCard, getString(R.string.home_tare_button)) { service?.tareScale()?.let(::toast); render() }
        scaleDisconnect = button(deviceCard, getString(R.string.home_scale_disconnect)) { service?.disconnect(DeviceRole.BOOKOO) }
        button(deviceCard, getString(R.string.home_shutdown)) {
            val owner = service
            owner?.shutdown()
            if (owner?.running == true) toast(getString(R.string.home_shutdown_busy)) else release()
            render()
        }

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text(deviceCard, getString(R.string.task_brew_machine_readings), 16, true)
        deviceCard.addView(metrics, LinearLayout.LayoutParams(-1, -2))
        var metricRow: LinearLayout? = null
        var metricIndex = 0
        val metricColumns = if (HoyiUi.wide(this)) 4 else 2
        fun metric(label: String): TextView {
            if (metricIndex++ % metricColumns == 0) {
                metricRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                metrics.addView(metricRow, LinearLayout.LayoutParams(-1, -2))
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(6), dp(4), dp(6))
            }
            metricRow!!.addView(box, LinearLayout.LayoutParams(0, -2, 1f))
            HoyiUi.label(this, box, label, 12, muted = true)
            return HoyiUi.label(this, box, "—", 16, true)
        }
        brewTemperature = metric(getString(R.string.home_brew_temperature))
        brewPressure = metric(getString(R.string.home_brew_pressure))
        steamTemperature = metric(getString(R.string.home_steam_temperature))
        steamPressure = metric(getString(R.string.home_steam_pressure))

        val workspace = TaskWorkspaceLayout(this)
        content.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        val primary = workspace.primaryBody
        val secondary = workspace.secondaryBody
        selection = text(primary, getString(R.string.home_curve_missing), 24, true).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            minimumHeight = dp(48)
            setOnClickListener {
                AlertDialog.Builder(this@HomeActivity).setTitle(getString(R.string.home_current_curve))
                    .setMessage(selectionDetails).setPositiveButton(android.R.string.ok, null).show()
            }
        }
        stagePreview = CurveStageView(this).apply { minimumHeight = dp(96); tag = "stage-preview" }
        primary.addView(stagePreview, LinearLayout.LayoutParams(-1, 0, 1f))
        previewUnavailable = text(primary, "", 14).apply { visibility = View.GONE }
        previewNote = text(primary, getString(R.string.task_brew_preview_note), 14)
        val fallbackBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stageFallbackText = text(fallbackBody, "", 14)
        stageFallback = ScrollView(this).apply {
            tag = "stage-fallback"; isFillViewport = false; visibility = View.GONE
            addView(fallbackBody)
        }
        primary.addView(stageFallback, LinearLayout.LayoutParams(-1, 0, 1f))
        recentCurves = HomeRecentCurves(this, primary, secondary,
            active = { service?.let { ShotGate.active(it.shotState) || it.manualShotActive } == true }) { item ->
            getSharedPreferences("curves", MODE_PRIVATE).edit().putString("selected", item.id).apply()
            render()
        }
        recentCurves.refresh()
        workspace.onPrimarySpaceAvailable = { width, height ->
            val lines = if (height < dp(240)) 1 else 2
            if (selection.maxLines != lines) selection.maxLines = lines
            val exactWidth = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val naturalHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            // Measure actual localized text, including font scale and padding. Do not infer its height.
            selection.measure(exactWidth, naturalHeight)
            previewNote.measure(exactWidth, naturalHeight)
            previewUnavailable.measure(exactWidth, naturalHeight)
            val detailHeight = if (stagePreview.targets.isEmpty()) previewUnavailable.measuredHeight else dp(96)
            val required = selection.measuredHeight + previewNote.measuredHeight + detailHeight
            val recentHeight = recentCurves.fit(width, height - required)
            textStagePreview = height < required
            updateStagePreviewVisibility()
            // This budget already excludes the supporting viewport; only stage details scroll here.
            if (textStagePreview) height else required + recentHeight
        }
        text(secondary, getString(R.string.task_brew_cup_summary), 16, true)
        beanSummary = text(secondary, "", 16).apply {
            minimumHeight = dp(48)
            setOnClickListener { startActivity(Intent(this@HomeActivity, BeanPreparationActivity::class.java)) }
        }
        temperatureSummary = text(secondary, "", 14)
        usageSummary = text(secondary, "", 14).apply {
            minimumHeight = dp(48)
            setOnClickListener { startActivity(Intent(this@HomeActivity, HistoryActivity::class.java)) }
        }
        browseCurvesButton = button(secondary, getString(R.string.home_curve_browse)) {
            startActivity(Intent(this, CurveActivity::class.java))
        }
        presetPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        presetButtons = (1..5).map { slot ->
            button(presetPanel, getString(R.string.home_slot, slot.toString())) {
                presetDialog?.dismiss()
                startActivity(Intent(this, ExtractionActivity::class.java).putExtra(PresetSlots.EXTRA_SLOT, slot))
            }
        }
        quickPresets = button(secondary, getString(R.string.home_quick_curves)) { showQuickPresets() }
        button(secondary, getString(R.string.dose_title)) { startActivity(Intent(this, BeanPreparationActivity::class.java)) }
        button(secondary, getString(R.string.scale_title)) { startActivity(Intent(this, ScaleActivity::class.java)) }
        val actionDock = TaskActionDock(this, edgeInsetDp = 0)
        content.addView(actionDock, LinearLayout.LayoutParams(-1, -2))
        brewButton = button(actionDock, getString(R.string.home_curve_select), primary = true) {
            val library = (application as MobileApplication).curves
            val item = getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)?.let(library::find)
            val active = service?.let { ShotGate.active(it.shotState) || it.manualShotActive } == true
            startActivity(Intent(this, if (active || (item != null && library.canStart(item)))
                ExtractionActivity::class.java else CurveActivity::class.java))
        }
        render()
    }
    private fun showQuickPresets() {
        if (presetDialog?.isShowing == true) return
        (presetPanel.parent as? android.view.ViewGroup)?.removeView(presetPanel)
        presetDialog = AlertDialog.Builder(this).setTitle(getString(R.string.home_quick_curves))
            .setMessage(getString(R.string.home_quick_curves_hint))
            .setView(ScrollView(this).apply { addView(presetPanel) })
            .setNegativeButton(android.R.string.cancel, null).create().also { dialog ->
                dialog.setOnDismissListener {
                    (presetPanel.parent as? android.view.ViewGroup)?.removeView(presetPanel)
                    presetDialog = null
                }
                dialog.show()
            }
    }
    private fun showDevicePanel() {
        if (deviceDialog?.isShowing == true) return
        (devicePanel.parent as? android.view.ViewGroup)?.removeView(devicePanel)
        val scroll = ScrollView(this).apply { addView(devicePanel) }
        deviceDialog = AlertDialog.Builder(this).setTitle(getString(R.string.home_device_connection))
            .setView(scroll).setPositiveButton(android.R.string.ok, null).create().also { dialog ->
                dialog.setOnDismissListener { (devicePanel.parent as? android.view.ViewGroup)?.removeView(devicePanel); deviceDialog = null }
                dialog.show()
            }
        render()
    }
    override fun onStart() {
        feedbackUi.start()
        super.onStart()
        recentCurves.refresh()
        visible = true
        if (!startRememberedScaleService()) bindExisting()
        handler.post(refresh)
    }
    override fun onStop() {
        feedbackUi.leave(isChangingConfigurations)
        visible = false; handler.removeCallbacks(refresh)
        deviceDialog?.dismiss()
        presetDialog?.dismiss()
        recentCurves.dismiss()
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
        val app = application as MobileApplication
        val library = app.curves
        val selected = getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)?.let(library::find)
        val cupActive = shotActive || owner?.manualShotActive == true
        recentCurves.updateAvailability()
        val displayed = if (cupActive) owner?.chartCurveId?.let(library::find) else selected
        val title = displayed?.name ?: getString(if (cupActive && owner?.chartCurveId == "manual") R.string.extraction_manual_label
            else if (cupActive) R.string.task_brew_curve_unknown else R.string.home_curve_missing)
        selection.show(title)
        selectionDetails = title + "\n\n" + (displayed?.let { getString(if (library.canStart(it))
            R.string.home_curve_startable else R.string.home_curve_readonly) + "\n" + it.details } ?: "") + "\n\n" + getString(R.string.task_brew_preview_note)
        val targets = displayed?.let(CurveStageParameters::pressureTargets).orEmpty()
        if (stagePreview.targets != targets) stagePreview.targets = targets
        val previewDescription = getString(R.string.task_brew_preview_note) + " " +
            targets.mapIndexed { index, target -> getString(R.string.chart_stage, (index + 1).toString()) +
                " " + String.format(Locale.ROOT, "%.1f bar", target / 10.0) }.joinToString(", ")
        if (stagePreview.contentDescription?.toString() != previewDescription)
            stagePreview.contentDescription = previewDescription
        previewUnavailable.show(getString(R.string.task_brew_no_pressure_preview))
        stageFallbackText.show(getString(R.string.task_brew_compact_preview) + "\n" +
            getString(R.string.task_brew_preview_note) + "\n" +
            if (targets.isEmpty()) getString(R.string.task_brew_no_pressure_preview) else
                targets.mapIndexed { index, target -> getString(R.string.chart_stage, (index + 1).toString()) +
                    " · " + String.format(Locale.ROOT, "%.1f bar", target / 10.0) }.joinToString("\n"))
        updateStagePreviewVisibility()
        brewButton.show(when {
            cupActive -> getString(R.string.task_brew_live_state)
            selected == null -> getString(R.string.home_curve_select)
            !library.canStart(selected) -> getString(R.string.home_curve_replace)
            else -> getString(R.string.home_curve_prepare)
        })
        browseCurvesButton.visibility = View.VISIBLE
        val profile = displayed?.let { library.resolve(it.id, s.scaleState == DeviceState.READY) }
        temperatureSummary.show(getString(R.string.task_brew_temperature,
            owner?.currentCorrectedBrewTemperature()?.let(::number) ?: "—", profile?.temperatureC?.toString() ?: "—"))
        val doseResult = app.beanPreparationResult
        val dose = doseResult.getOrNull()?.current()
        val matches = dose?.let { value -> app.beanInventoryResult.getOrNull()?.events()?.any {
            it.eventId == value.eventId && it.batchId == value.batchId &&
                it.kind == io.openhoyi.bean.InventoryEventKind.CONSUME && it.deltaMg == -value.amountMg
        } }
        val doseState = when {
            doseResult.isFailure -> R.string.task_brew_dose_unavailable
            dose == null -> R.string.task_brew_dose_missing
            dose.shotId != null && !dose.associated -> R.string.task_brew_dose_association
            dose.shotId != null -> R.string.task_brew_dose_previous
            dose.attempted && !dose.confirmed -> R.string.task_brew_dose_pending
            dose.confirmed && matches != true -> R.string.task_brew_dose_conflict
            dose.confirmed -> R.string.task_brew_dose_confirmed
            else -> R.string.task_brew_dose_selected
        }
        beanSummary.show((dose?.let { it.beanName + " · " + BeanQuantity.formatGrams(it.amountMg) + " g\n" } ?: "") +
            getString(doseState) + "\n" + getString(R.string.task_brew_dose_entry))
        val stats = displayed?.let { app.curveUsageResult.getOrNull()?.stats(it.id) }
        usageSummary.show(when {
            app.curveUsageResult.isFailure -> getString(R.string.task_brew_usage_unavailable)
            stats == null || (stats.count == 0L && stats.lastUsedAtMs == null) -> getString(R.string.task_brew_usage_empty)
            else -> getString(R.string.task_brew_usage, stats.count.toString(), stats.lastUsedAtMs?.let {
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT,
                    app.languagePreferences.current.locale).format(java.util.Date(it))
            } ?: "—")
        } + "\n" + getString(R.string.task_brew_history_entry))
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
    private fun updateStagePreviewVisibility() {
        fun View.visibleWhen(show: Boolean) {
            val next = if (show) View.VISIBLE else View.GONE
            if (visibility != next) visibility = next
        }
        stagePreview.visibleWhen(!textStagePreview && stagePreview.targets.isNotEmpty())
        previewUnavailable.visibleWhen(!textStagePreview && stagePreview.targets.isEmpty())
        previewNote.visibleWhen(!textStagePreview)
        stageFallback.visibleWhen(textStagePreview)
    }

    private fun statusDot(parent: LinearLayout): View = View(this).apply {
        background = dotShape(R.color.mobile_muted)
        parent.addView(this, LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(8) })
    }
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
            includeFontPadding = false
            typeface = TaskTypography.face(context, if (bold) 700 else 400)
            val relatedInset = dp(TaskVisualTokens.RELATED_GAP_DP / 2)
            setPadding(0, relatedInset, 0, relatedInset)
            parent.addView(this)
        }
    private fun card(parent: LinearLayout, title: String) = HoyiUi.card(this, parent, title)
    private fun button(parent: LinearLayout, value: String, primary: Boolean = false, action: () -> Unit) =
        HoyiUi.button(this, parent, value, primary = primary, action = action)
    companion object { private const val PERMISSIONS = 12; private const val ENABLE = 13; private const val EXPORT = 14 }
}
