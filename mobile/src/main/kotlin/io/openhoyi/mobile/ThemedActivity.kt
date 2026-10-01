package io.openhoyi.mobile

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

/** A per-app theme override; it does not change the user's system theme. */
abstract class ThemedActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        val dark = newBase.getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark", false)
        val language = (newBase.applicationContext as MobileApplication).languagePreferences.current
        super.attachBaseContext(AppLanguageContext.wrap(newBase, language, dark))
    }

    override fun onResume() {
        super.onResume()
        val preferredDark = getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark", false)
        val currentDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val language = (application as MobileApplication).languagePreferences.current
        val currentLanguage = resources.configuration.locales[0]
        if (preferredDark != currentDark || currentLanguage != language.locale) recreate()
    }
}
