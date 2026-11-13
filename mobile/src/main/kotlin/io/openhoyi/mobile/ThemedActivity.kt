package io.openhoyi.mobile

import androidx.fragment.app.FragmentActivity
import android.content.Context
import android.content.res.Configuration

/** App-owned appearance. System density/window geometry stay intact; font size is independent. */
abstract class ThemedActivity : FragmentActivity() {
    internal var redirectedRoot = false
    override fun attachBaseContext(newBase: Context) {
        val dark = newBase.getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark", false)
        val language = (newBase.applicationContext as MobileApplication).languagePreferences.current
        super.attachBaseContext(AppLanguageContext.wrap(newBase, language, dark, (newBase.applicationContext as MobileApplication).fontSizePreferences.current))
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        if (resources.configuration.locales[0].language == "zh") theme.applyStyle(R.style.MobileChineseTypography, true)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val preferredDark = getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark", false)
        val currentDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val language = (application as MobileApplication).languagePreferences.current
        val currentLanguage = resources.configuration.locales[0]
        if (preferredDark != currentDark || currentLanguage != language.locale ||
            resources.configuration.fontScale != (application as MobileApplication).fontSizePreferences.current.scale) recreate()
    }
}
