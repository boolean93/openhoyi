package io.openhoyi.mobile

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

/** The app settings tab owns its controls and listeners while the host owns its service binding. */
class AppSettingsFragment : Fragment() {
    internal lateinit var themeCheckBox: CompoundButton
        private set
    internal lateinit var themeError: TextView
        private set
    internal var appearanceWrite: (Boolean) -> Boolean = { value ->
        requireContext().getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("dark", value).commit()
    }
    private var updatingTheme = false
    private var machineControls: MachineSettingsControls? = null
    private var document: SettingsDocumentLayout? = null
    private var documentState: Bundle? = null
    private var pendingSection: String? = null
    private var machineState: Bundle? = null
    private var deviceControls: SettingsDeviceControls? = null
    private var diagnosticState: TextView? = null
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.isNotEmpty() && result.values.all { it }) deviceControls?.scan()
        else deviceControls?.error(getString(R.string.home_permission_required))
    }
    private val bluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) deviceControls?.scan()
        else deviceControls?.error(getString(R.string.home_permission_required))
    }
    fun openSection(key: String) { val current=document;if(current==null)pendingSection=key else current.navigate(key) }
    override fun onSaveInstanceState(outState: Bundle) {
        document?.save(outState)
        machineControls?.let { controls -> machineState = Bundle().also { controls.save(it) } }
        outState.putBundle("machineControls", machineState)
        super.onSaveInstanceState(outState)
    }
    private var feedbackCard: BrewFeedbackPreferencesCard? = null
    private var languageCard: AppLanguagePreferencesCard? = null
    private var exportStatus: TextView? = null
    private var exportGeneration = 0L
    private var service: MobileService? = null
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshCapabilities = object : Runnable {
        override fun run() { serviceObserver(service); refreshHandler.postDelayed(this, 500) }
    }
    private var scaleCapabilities: TextView? = null
    private val serviceObserver: (MobileService?) -> Unit = {
        service = it
        deviceControls?.render(it)
        fun badge(state:String?):String?=when(state) {
            "UNKNOWN"->getString(R.string.task_shell_machine_unknown)
            "FAILED"->getString(R.string.home_setting_failed)
            "WRITING"->getString(R.string.home_setting_writing)
            "WAITING_READBACK","WAITING_ZERO"->getString(R.string.home_setting_waiting)
            else->null
        }
        document?.setStatus("machine",badge(it?.settingWriteState?.name))
        document?.setStatus("schedule",badge(it?.scheduleWriteState?.name))
        document?.setStatus("maintenance",badge(it?.cupResetState?.name))
        val current = it?.snapshot
        diagnosticState?.text = listOf(
            FirmwarePresentation(requireContext()).describe(it?.coffeeFirmware, current?.coffeeState ?: io.openhoyi.session.DeviceState.DISCONNECTED, BuildConfig.MOCK_MODE),
            MachineAlarms(requireContext()).describe(current?.alarmBits, current?.alarmAt, android.os.SystemClock.elapsedRealtime())
        ).joinToString("\n")
        val snapshot = it?.snapshot
        val actual = snapshot?.scaleCapabilities?.takeIf { snapshot.scaleState == io.openhoyi.session.DeviceState.READY && !BuildConfig.MOCK_MODE }
        scaleCapabilities?.text = if (actual == null) getString(R.string.scale_capability_no_device) else
            listOf(R.string.scale_capability_weight to actual.weight, R.string.scale_capability_tare to actual.tare,
                R.string.scale_capability_flow to actual.deviceFlow, R.string.scale_capability_battery to actual.battery,
                R.string.scale_capability_timer to actual.deviceTimer).joinToString("\n") { (label, available) ->
                getString(R.string.scale_capability_format, getString(label), getString(if (!available) R.string.scale_capability_no
                    else if (snapshot.scaleObservation?.evidence == io.openhoyi.protocol.ScaleEvidence.LIVE_READ_ONLY) R.string.scale_capability_read_only else R.string.scale_capability_yes))
            }
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val generation = ++exportGeneration
            (requireActivity().application as MobileApplication).export(uri) { message, failed ->
                if (generation == exportGeneration && isAdded) {
                    exportStatus?.apply {
                        text = message
                        setTextColor(requireContext().getColor(if (failed) R.color.mobile_danger else R.color.mobile_text))
                        visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val activity = requireActivity() as MainActivity
        val visual = SettingsVisualStyle(activity)
        val style = visual.palette
        val root = SettingsDocumentLayout(activity, state ?: documentState).also { document=it }
        val sections = linkedMapOf(
            "devices" to root.section("devices", getString(R.string.home_device_connection)),
            "machine" to root.section("machine", getString(R.string.settings_section_machine)),
            "schedule" to root.section("schedule", getString(R.string.settings_section_schedule)),
            "scale" to root.section("scale", getString(R.string.scale_title)),
            "appearance" to root.section("appearance", getString(R.string.settings_appearance_language)),
            "feedback" to root.section("feedback", getString(R.string.settings_section_feedback)),
            "maintenance" to root.section("maintenance", getString(R.string.settings_section_maintenance)),
            "about" to root.section("about", getString(R.string.settings_section_about)))
        deviceControls = SettingsDeviceControls(activity, visual, { permissions.launch(it) }, {
            bluetooth.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }).also { controls ->
            controls.errorChanged={ failed->document?.setStatus("devices",if(failed)getString(R.string.settings_state_unknown) else null) }
            controls.create(sections.getValue("devices"))
        }
        machineState = state?.getBundle("machineControls") ?: machineState
        val machine = sections.getValue("machine")
        val machineSummary=style.text("",TaskProductVisualStyle.auxiliary,"settings-machine-summary",true).also {style.add(machine,it,8)}
        machineControls=MachineSettingsControls(activity).also {
            it.statusChanged={ value->machineSummary.text=value }
            it.create(machine,machineState,embedded=true,documentSections=sections)
        }
        val scale=sections.getValue("scale")
        scaleCapabilities=style.text(getString(R.string.scale_capability_no_device),TaskProductVisualStyle.auxiliary,secondary=true)
        val prefs = activity.getSharedPreferences("scale_tool", android.content.Context.MODE_PRIVATE)
        scale.addView(Switch(activity).apply {
            text = getString(R.string.scale_precision_short)
            isChecked = prefs.getBoolean("one_decimal", false)
            visual.switch(this, 48)
            tag = "product/settings/scale/precision"
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("one_decimal", checked).apply() }
        })
        scaleCapabilities?.let { scale.addView(it) }
        style.add(scale, style.text(getString(R.string.scale_precision_off_hint), TaskProductVisualStyle.auxiliary, secondary = true))
        HoyiUi.button(activity, scale, getString(R.string.scale_title)) { activity.openScaleFromSettings() }
        listOf(R.string.scale_timer_title to R.string.scale_timer_help, R.string.scale_operation_limits to R.string.scale_active_guard,
            R.string.scale_protocol_support to R.string.scale_offline_evidence).forEach { (title, detail) ->
            style.add(scale,style.text(getString(title),TaskProductVisualStyle.action),16)
            style.add(scale,style.text(getString(detail),TaskProductVisualStyle.auxiliary,secondary=true),8)
        }
        val theme = sections.getValue("appearance")
        val appearance = activity.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
        themeError = style.text("", TaskProductVisualStyle.auxiliary, "product/settings/app/theme/error").apply {
            setTextColor(activity.getColor(R.color.mobile_danger)); visibility = View.GONE
        }
        val toggle = Switch(activity).apply {
            text = getString(R.string.app_settings_dark)
            isChecked = appearance.getBoolean("dark", false)
            minimumHeight = style.dp(52)
            tag = "product/settings/app/theme/toggle"
        }
        visual.switch(toggle, 52)
        themeCheckBox = toggle
        theme.addView(toggle, LinearLayout.LayoutParams(-1, -2))
        style.add(theme, style.text(getString(R.string.app_settings_subtitle), TaskProductVisualStyle.auxiliary, secondary = true), 8)
        style.add(theme, themeError, 8)
        toggle.setOnCheckedChangeListener { button: CompoundButton, dark: Boolean ->
            if (updatingTheme) return@setOnCheckedChangeListener
            val previous = appearance.getBoolean("dark", false)
            if (dark == previous) return@setOnCheckedChangeListener
            if (runCatching { appearanceWrite(dark) }.getOrDefault(false)) {
                themeError.text = ""
                themeError.visibility = View.GONE
                service?.refreshNotificationDisplay()
                activity.recreate()
            } else {
                runCatching { appearanceWrite(previous) }
                updatingTheme = true
                button.isChecked = previous
                updatingTheme = false
                themeError.setText(R.string.app_settings_theme_failed)
                themeError.visibility = View.VISIBLE
            }
        }
        settingsDivider(activity, theme, visual)
        languageCard = AppLanguagePreferencesCard(activity, theme, embedded = true, visual = visual) {
            service?.refreshNotificationDisplay(); activity.recreate()
        }
        feedbackCard = BrewFeedbackPreferencesCard(activity, sections.getValue("feedback"), visual)
        val info = sections.getValue("about")
        fun infoRow(key: String, label: Int, value: String) {
            if (info.childCount > 0) settingsDivider(activity, info, visual)
            val pair = visual.pair(
                style.text(getString(label), TaskProductVisualStyle.auxiliary, "product/settings/app/info/$key/label", true),
                style.text(value, TaskProductVisualStyle.body, "product/settings/app/info/$key/value"),
                "product/settings/app/info/$key", false)
            style.add(info, pair)
        }
        infoRow("name", R.string.settings_info_name, getString(R.string.app_name))
        infoRow("version", R.string.settings_info_version, getString(R.string.settings_info_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        infoRow("mode", R.string.settings_info_mode, getString(if (BuildConfig.MOCK_MODE) R.string.application_mode_mock else R.string.application_mode_alpha))
        infoRow("package", R.string.settings_info_package, BuildConfig.APPLICATION_ID)
        val diagnostics = sections.getValue("maintenance")
        diagnosticState=style.text("",TaskProductVisualStyle.auxiliary,secondary=true).also {style.add(diagnostics,it,16)}
        style.add(diagnostics, style.text(getString(R.string.app_settings_export_hint), TaskProductVisualStyle.auxiliary, secondary = true))
        HoyiUi.button(activity, diagnostics, getString(R.string.app_settings_export)) {
            exportGeneration++
            exportStatus?.visibility = View.GONE
            export.launch("openhoyi-diagnostics-${System.currentTimeMillis()}.zip")
        }.apply { asTaskTextAction(); gravity = Gravity.START or Gravity.CENTER_VERTICAL; visual.action(this, icon = "export", atEnd = true) }
        exportStatus = style.text("", TaskProductVisualStyle.auxiliary, "product/settings/app/export/result").apply {
            visibility = View.GONE; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }.also { style.add(diagnostics, it, 8) }
        pendingSection?.let { root.navigate(it); pendingSection=null }
        return root
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).observeService(serviceObserver)
        refreshHandler.post(refreshCapabilities)
        machineControls?.start()
        feedbackCard?.start(); feedbackCard?.refresh()
    }
    override fun onPause() {
        refreshHandler.removeCallbacks(refreshCapabilities)
        deviceControls?.close()
        machineControls?.stop()
        feedbackCard?.pausePreview(); feedbackCard?.stop(); languageCard?.close()
        (requireActivity() as MainActivity).removeServiceObserver(serviceObserver)
        service = null
        exportGeneration++
        super.onPause()
    }
    override fun onStop() {
        super.onStop()
    }
    override fun onDestroyView() {
        feedbackCard?.stop(); languageCard?.close()
        machineControls?.let { controls -> machineState = Bundle().also { controls.save(it) } }
        documentState=Bundle().also { document?.save(it) }
        document=null; deviceControls?.close(); deviceControls=null
        machineControls = null
        feedbackCard = null; languageCard = null; exportStatus = null
        super.onDestroyView()
    }
}
