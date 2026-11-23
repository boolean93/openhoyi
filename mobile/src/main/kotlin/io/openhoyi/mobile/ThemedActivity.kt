package io.openhoyi.mobile

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/** App-owned appearance. System density/window geometry stay intact; font size is independent. */
abstract class ThemedActivity : FragmentActivity() {
    internal var redirectedRoot = false
    private lateinit var appliedAppearance: AppAppearance
    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as MobileApplication
        appliedAppearance = app.appearancePreferences.current
        val configured = AppLanguageContext.wrap(newBase, app.languagePreferences.current,
            appliedAppearance.dark, app.fontSizePreferences.current)
        super.attachBaseContext(AppAppearanceContext(configured, appliedAppearance))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(appliedAppearance.palette.nativeOverlay, true)
        if (resources.configuration.locales[0].language == "zh") theme.applyStyle(R.style.MobileChineseTypography, true)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val app = application as MobileApplication
        if (appliedAppearance != app.appearancePreferences.current ||
            resources.configuration.locales[0] != app.languagePreferences.current.locale ||
            resources.configuration.fontScale != app.fontSizePreferences.current.scale) recreate()
    }
}
