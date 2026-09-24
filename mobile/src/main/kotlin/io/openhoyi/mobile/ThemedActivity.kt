package io.openhoyi.mobile

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

/** A per-app theme override; it does not change the user's system theme. */
abstract class ThemedActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        val dark = newBase.getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark", false)
        val config = Configuration(newBase.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
}
