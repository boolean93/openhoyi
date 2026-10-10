package io.openhoyi.mobile

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.WindowInsets
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Offline app preferences. Binds only an already-running owner for notification display refresh. */
class AppSettingsActivity : ThemedActivity() {
    internal lateinit var feedbackCard: BrewFeedbackPreferencesCard
        private set
    private lateinit var languageCard: AppLanguagePreferencesCard
    internal lateinit var themeCheckBox: CheckBox
        private set
    internal lateinit var themeError: TextView
        private set
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val area = if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(area[0], area[1], area[2], area[3]); insets
            }
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        scroll.addView(container)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(HoyiUi.dp(this@AppSettingsActivity, 20), HoyiUi.dp(this@AppSettingsActivity, 20), HoyiUi.dp(this@AppSettingsActivity, 20), HoyiUi.dp(this@AppSettingsActivity, 28))
        }
        container.addView(body, LinearLayout.LayoutParams(if (HoyiUi.wide(this)) HoyiUi.dp(this, 640) else -1, -2))
        HoyiUi.navigation(this, root, AppSettingsActivity::class.java)
        setContentView(root)
        HoyiUi.header(this, body, getString(R.string.app_settings_title), getString(R.string.app_settings_subtitle), back = true)
        val theme = HoyiUi.card(this, body, getString(R.string.app_settings_appearance))
        val appearance = getSharedPreferences("appearance", MODE_PRIVATE)
        themeCheckBox = CheckBox(this).apply {
            text = getString(R.string.app_settings_dark)
            textSize = 17f
            setTextColor(getColor(R.color.mobile_text))
            minimumHeight = HoyiUi.dp(this@AppSettingsActivity, 52)
            isChecked = appearance.getBoolean("dark", false)
            theme.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        themeError = beanError(theme)
        themeCheckBox.setOnCheckedChangeListener { _, dark ->
            if (!updatingTheme) {
                val previous = appearance.getBoolean("dark", false)
                val saved = runCatching { appearanceWrite(dark) }.getOrDefault(false)
                if (saved) { themeError.text = ""; service?.refreshNotificationDisplay(); recreate() }
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
        languageCard = AppLanguagePreferencesCard(this, body) { service?.refreshNotificationDisplay(); recreate() }
        feedbackCard = BrewFeedbackPreferencesCard(this, body)
        val info = HoyiUi.card(this, body, getString(R.string.application_info))
        HoyiUi.label(this, info, getString(R.string.app_name), 17, true)
        HoyiUi.label(this, info, getString(R.string.application_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), 14)
        HoyiUi.label(this, info, getString(if (BuildConfig.MOCK_MODE) R.string.application_mode_mock else R.string.application_mode_alpha), 14)
        HoyiUi.label(this, info, getString(R.string.application_package, BuildConfig.APPLICATION_ID), 13, muted = true).setTextIsSelectable(true)
        HoyiUi.label(this, body, getString(R.string.app_settings_export_hint), 14, muted = true).setPadding(0, HoyiUi.dp(this, 18), 0, 0)
        HoyiUi.button(this, body, getString(R.string.app_settings_export)) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, "openhoyi-diagnostics-${System.currentTimeMillis()}.zip"), EXPORT_LOGS)
        }
        HoyiUi.button(this, body, getString(R.string.app_settings_machine)) { startActivity(Intent(this, MachineSettingsActivity::class.java)) }
    }
    override fun onStart() {
        super.onStart()
        feedbackCard.start(); feedbackCard.refresh()
        if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0)
    }
    override fun onStop() {
        feedbackCard.stop(); languageCard.close(); release()
        super.onStop()
    }
    override fun onDestroy() {
        if (::feedbackCard.isInitialized) feedbackCard.stop()
        if (::languageCard.isInitialized) languageCard.close()
        release()
        super.onDestroy()
    }
    private fun release() {
        if (bound) { unbindService(connection); bound = false }
        service = null
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT_LOGS && resultCode == RESULT_OK) data?.data?.let { (application as MobileApplication).export(it) }
    }
    companion object { private const val EXPORT_LOGS = 64 }
}
