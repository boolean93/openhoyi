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
    private val expanded = mutableMapOf<String, Boolean>()
    private var machineState: Bundle? = null
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        expanded.forEach { (key, value) -> outState.putBoolean("expanded:$key", value) }
        machineControls?.let { controls -> machineState = Bundle().also { controls.save(it) } }
        outState.putBundle("machineControls", machineState)
    }
    private fun group(activity: MainActivity, body: LinearLayout, title: String, visual: SettingsVisualStyle, key: String, open: Boolean = false): LinearLayout {
        val shell = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(shell, LinearLayout.LayoutParams(-1, -2))
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val toggle = HoyiUi.button(activity, shell, title) {}.apply {
            minimumHeight = visual.palette.dp(56)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            tag = "settings-section:$key"
        }
        shell.addView(content, LinearLayout.LayoutParams(-1, -2))
        fun refresh() {
            val showing = expanded[key] ?: open
            content.visibility = if (showing) View.VISIBLE else View.GONE
            toggle.text = "$title  ${if (showing) "⌃" else "⌄"}"
            toggle.isSelected = showing
        }
        toggle.setOnClickListener { expanded[key] = !(expanded[key] ?: open); refresh() }
        refresh()
        return content
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
        val (root, body) = visual.fragmentPage(activity, R.string.app_settings_title, R.string.app_settings_subtitle, 960, "app")
        // Keep the shared tools host while the navigation tab names this root page.
        val header = body.getChildAt(0) as LinearLayout
        header.removeViewAt(0)
        header.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        header.minimumHeight = 0
        header.setPadding(0, 0, 0, 0)
        (header.layoutParams as LinearLayout.LayoutParams).apply { topMargin = 0; bottomMargin = 0 }
        listOf("machine", "scale", "appearance", "feedback", "info", "diagnostics").forEach { key ->
            if (state?.containsKey("expanded:$key") == true) expanded[key] = state.getBoolean("expanded:$key")
        }
        machineState = state?.getBundle("machineControls") ?: machineState
        HoyiUi.button(activity, body, getString(R.string.home_device_connection)) {
            startActivity(Intent(activity, DeviceConnectionActivity::class.java))
        }.apply { minimumHeight = style.dp(48); asTaskTextAction(); gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        val machine = group(activity, body, getString(R.string.machine_settings_title), visual, "machine", true)
        val machineSummary = style.text("", TaskProductVisualStyle.auxiliary, "settings-machine-summary", true)
        (machine.parent as LinearLayout).addView(machineSummary, 1)
        machineControls = MachineSettingsControls(activity).also {
            it.statusChanged = { value -> machineSummary.text = value }
            it.create(machine, machineState, embedded = true)
        }
        val scale = group(activity, body, getString(R.string.scale_title), visual, "scale", true)
        scaleCapabilities = style.text(getString(R.string.scale_capability_no_device), TaskProductVisualStyle.auxiliary, secondary = true)
            .also { (scale.parent as LinearLayout).addView(it, 1) }
        val prefs = activity.getSharedPreferences("scale_tool", android.content.Context.MODE_PRIVATE)
        scale.addView(Switch(activity).apply {
            text = getString(R.string.scale_precision_short)
            isChecked = prefs.getBoolean("one_decimal", false)
            visual.switch(this, 48)
            tag = "product/settings/scale/precision"
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("one_decimal", checked).apply() }
        })
        style.add(scale, style.text(getString(R.string.scale_precision_off_hint), TaskProductVisualStyle.auxiliary, secondary = true))
        HoyiUi.button(activity, scale, getString(R.string.scale_title)) { activity.openScaleFromSettings() }
        HoyiUi.button(activity, scale, getString(R.string.scale_capabilities_title)) {
            startActivity(Intent(activity, ScaleSettingsActivity::class.java))
        }
        val theme = group(activity, body, getString(R.string.settings_appearance_language), visual, "appearance")
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
        feedbackCard = BrewFeedbackPreferencesCard(activity, group(activity, body, getString(R.string.feedback_enabled), visual, "feedback"), visual)
        val info = group(activity, body, getString(R.string.application_info), visual, "info")
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
        val diagnostics = group(activity, body, getString(R.string.settings_diagnostics_devices), visual, "diagnostics")
        style.add(diagnostics, style.text(getString(R.string.app_settings_export_hint), TaskProductVisualStyle.auxiliary, secondary = true))
        HoyiUi.button(activity, diagnostics, getString(R.string.app_settings_export)) {
            exportGeneration++
            exportStatus?.visibility = View.GONE
            export.launch("openhoyi-diagnostics-${System.currentTimeMillis()}.zip")
        }.apply { asTaskTextAction(); gravity = Gravity.START or Gravity.CENTER_VERTICAL; visual.action(this, icon = "export", atEnd = true) }
        exportStatus = style.text("", TaskProductVisualStyle.auxiliary, "product/settings/app/export/result").apply {
            visibility = View.GONE; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }.also { style.add(diagnostics, it, 8) }
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
        machineControls = null
        feedbackCard = null; languageCard = null; exportStatus = null
        super.onDestroyView()
    }
}
