package io.openhoyi.mobile

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.WindowInsets
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.View
import java.lang.ref.WeakReference

/** Offline app preferences. Binds only an already-running owner for notification display refresh. */
class AppSettingsActivity : ThemedActivity() {
    internal lateinit var feedbackCard: BrewFeedbackPreferencesCard
        private set
    private lateinit var languageCard: AppLanguagePreferencesCard
    internal lateinit var themeCheckBox: CompoundButton
        private set
    internal lateinit var themeError: TextView
        private set
    internal lateinit var exportStatus: TextView
        private set
    private var exportNormalColor = 0
    private var exportGeneration = 0L
    private var exportStarted = false
    private var pendingExportResult: Pair<String, Boolean>? = null
    private var service: MobileService? = null
    private var bound = false
    private var updatingTheme = false
    internal var appearanceWrite: (Boolean) -> Boolean = { value ->
        getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("dark", value).commit()
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MobileService.LocalBinder).service
            // A language selection can complete before this binding is ready.
            service?.refreshNotificationDisplay()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
        override fun onBindingDied(name: ComponentName) { release() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectRoot(TaskDestination.SETTINGS)) return
        val visual=SettingsVisualStyle(this)
        val style=visual.palette
        val body=visual.page(this,R.string.app_settings_title,R.string.app_settings_subtitle,640,"app")
        val theme = settingsGroup(this, body, getString(R.string.settings_appearance_language), visual)
        val appearance = getSharedPreferences("appearance", MODE_PRIVATE)
        themeCheckBox = Switch(this).apply {
            text = getString(R.string.app_settings_dark)
            textSize = TaskVisualTokens.BODY_SP.toFloat()
            typeface = TaskTypography.face(this@AppSettingsActivity, 400)
            setTextColor(getColor(R.color.mobile_text))
            minimumHeight = HoyiUi.dp(this@AppSettingsActivity, 52)
            isChecked = appearance.getBoolean("dark", false)
            theme.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        visual.switch(themeCheckBox as Switch,52);themeCheckBox.tag="product/settings/app/theme/toggle"
        val themeHint=style.text(getString(R.string.app_settings_subtitle),TaskProductVisualStyle.auxiliary,secondary=true)
        style.add(theme,themeHint,8)
        themeError = beanError(theme).also {error->
            style.apply(error,TaskProductVisualStyle.auxiliary)
            error.setTextColor(getColor(R.color.mobile_danger));error.setPadding(0,style.dp(8),0,0);error.tag="product/settings/app/theme/error"
        }
        themeCheckBox.setOnCheckedChangeListener { _, dark ->
            if (!updatingTheme) {
                val previous = appearance.getBoolean("dark", false)
                val saved = runCatching { appearanceWrite(dark) }.getOrDefault(false)
                if (saved) { themeError.text = "";invalidateExportResult();service?.refreshNotificationDisplay(); recreate() }
                else {
                    // SharedPreferences may update its memory even when disk commit reports failure.
                    runCatching { appearanceWrite(previous) }
                    updatingTheme = true
                    themeCheckBox.isChecked = previous
                    updatingTheme = false
                    themeError.setText(R.string.app_settings_theme_failed)
                }
            }
        }
        settingsDivider(this, theme, visual)
        languageCard = AppLanguagePreferencesCard(this, theme, embedded = true, visual = visual) { invalidateExportResult();service?.refreshNotificationDisplay(); recreate() }
        feedbackCard = BrewFeedbackPreferencesCard(this, body, visual)
        val info = settingsGroup(this, body, getString(R.string.application_info), visual)
        fun infoRow(label: Int, value: String, description: String = value, selectable: Boolean = false) {
            if (info.childCount > 0) settingsDivider(this, info, visual)
            val key=when(label) {R.string.settings_info_name->"name";R.string.settings_info_version->"version";R.string.settings_info_mode->"mode";else->"package"}
            val name=style.text(getString(label),TaskProductVisualStyle.auxiliary,"product/settings/app/info/$key/label",true)
            val content=style.text(value,TaskProductVisualStyle.body,"product/settings/app/info/$key/value").apply {
                contentDescription=description
                if(selectable) {setTextIsSelectable(true);minimumHeight=style.dp(48);minHeight=style.dp(48)}
            }
            style.add(info,visual.pair(name,content,"product/settings/app/info/$key",false))
        }
        infoRow(R.string.settings_info_name, getString(R.string.app_name))
        infoRow(R.string.settings_info_version,
            getString(R.string.settings_info_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            getString(R.string.application_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        infoRow(R.string.settings_info_mode,
            getString(if (BuildConfig.MOCK_MODE) R.string.application_mode_mock else R.string.application_mode_alpha))
        infoRow(R.string.settings_info_package, BuildConfig.APPLICATION_ID,
            getString(R.string.application_package, BuildConfig.APPLICATION_ID), selectable = true)
        val diagnostics = settingsGroup(this, body, getString(R.string.settings_diagnostics_devices), visual)
        style.add(diagnostics,style.text(getString(R.string.app_settings_export_hint),TaskProductVisualStyle.auxiliary,secondary=true))
        HoyiUi.button(this, diagnostics, getString(R.string.app_settings_export)) {
            invalidateExportResult()
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, "openhoyi-diagnostics-${System.currentTimeMillis()}.zip"), EXPORT_LOGS)
        }.apply { asTaskTextAction(); gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visual.action(this,icon="export",atEnd=true);tag="product/settings/app/export"
        }
        exportNormalColor=style.ink
        exportStatus=style.text("",TaskProductVisualStyle.auxiliary,"product/settings/app/export/result").apply {
            visibility=View.GONE;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        style.add(diagnostics,exportStatus,8)
        settingsDivider(this, diagnostics, visual)
        HoyiUi.button(this, diagnostics, getString(R.string.app_settings_machine)) {
            startActivity(Intent(this, MachineSettingsActivity::class.java))
        }.apply { asTaskTextAction(); gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visual.action(this,icon="chevron",atEnd=true);tag="product/settings/app/machine"
        }
    }
    override fun onStart() {
        super.onStart()
        if (redirectedRoot) return
        exportStarted=true
        pendingExportResult?.let {showExportResult(it.first,it.second);pendingExportResult=null}
        feedbackCard.start(); feedbackCard.refresh()
        if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0)
    }
    override fun onPause() {
        if (redirectedRoot) { super.onPause(); return }
        // Stop when leaving the interactive page, not after the next page finishes drawing.
        feedbackCard.pausePreview()
        super.onPause()
    }
    override fun onStop() {
        if (redirectedRoot) { super.onStop(); return }
        exportStarted=false;invalidateExportResult()
        feedbackCard.stop(); languageCard.close(); release()
        super.onStop()
    }
    override fun onDestroy() {
        if (redirectedRoot) { super.onDestroy(); return }
        exportStarted=false;invalidateExportResult()
        if (::feedbackCard.isInitialized) feedbackCard.stop()
        if (::languageCard.isInitialized) languageCard.close()
        release()
        super.onDestroy()
    }
    private fun release() {
        if (bound) { unbindService(connection); bound = false }
        service = null
    }
    private fun clearExportResult() {
        pendingExportResult=null
        if (::exportStatus.isInitialized) {exportStatus.text="";exportStatus.visibility=View.GONE}
    }
    private fun invalidateExportResult() {exportGeneration++;clearExportResult()}
    private fun showExportResult(message:String,failed:Boolean) {
        exportStatus.setTextColor(if(failed)getColor(R.color.mobile_danger) else exportNormalColor)
        exportStatus.text=message;exportStatus.visibility=View.VISIBLE
    }
    private fun deliverExportResult(generation:Long,message:String,failed:Boolean) {
        if (isDestroyed || isFinishing || generation!=exportGeneration) return
        if (exportStarted) showExportResult(message,failed)
        else pendingExportResult=message to failed
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT_LOGS && resultCode == RESULT_OK) data?.data?.let {uri->
            invalidateExportResult()
            val generation=exportGeneration
            val owner=WeakReference(this)
            (application as MobileApplication).export(uri) {message,failed->
                owner.get()?.deliverExportResult(generation,message,failed)
            }
        }
    }
    companion object { private const val EXPORT_LOGS = 64 }
}
