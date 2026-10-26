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
    private var feedbackCard: BrewFeedbackPreferencesCard? = null
    private var languageCard: AppLanguagePreferencesCard? = null
    private var exportStatus: TextView? = null
    private var exportGeneration = 0L
    private var service: MobileService? = null
    private val serviceObserver: (MobileService?) -> Unit = { service = it }
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
        val (root, body) = visual.fragmentPage(activity, R.string.app_settings_title, R.string.app_settings_subtitle, 640, "app")
        val machine = settingsGroup(activity, body, getString(R.string.machine_settings_title), visual)
        HoyiUi.button(activity, machine, getString(R.string.app_settings_machine)) {
            startActivity(Intent(activity, MachineSettingsActivity::class.java))
        }.apply { asTaskTextAction(); gravity = Gravity.START or Gravity.CENTER_VERTICAL; visual.action(this, icon = "chevron", atEnd = true) }
        val scale = settingsGroup(activity, body, getString(R.string.scale_title), visual)
        HoyiUi.button(activity, scale, getString(R.string.scale_title)) {
            activity.select(TaskDestination.SCALE)
        }.apply { asTaskTextAction(); gravity = Gravity.START or Gravity.CENTER_VERTICAL; visual.action(this, icon = "chevron", atEnd = true) }
        settingsDivider(activity, scale, visual)
        HoyiUi.button(activity, scale, getString(R.string.ui_settings)) {
            startActivity(Intent(activity, ScaleSettingsActivity::class.java))
        }.apply { asTaskTextAction(); gravity = Gravity.START or Gravity.CENTER_VERTICAL; visual.action(this, icon = "chevron", atEnd = true) }
        val theme = settingsGroup(activity, body, getString(R.string.settings_appearance_language), visual)
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
        feedbackCard = BrewFeedbackPreferencesCard(activity, body, visual)
        val info = settingsGroup(activity, body, getString(R.string.application_info), visual)
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
        val diagnostics = settingsGroup(activity, body, getString(R.string.settings_diagnostics_devices), visual)
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
        feedbackCard?.start(); feedbackCard?.refresh()
    }
    override fun onPause() {
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
        feedbackCard = null; languageCard = null; exportStatus = null
        super.onDestroyView()
    }
}
