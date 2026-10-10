package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Mock-only runtime prerequisites. No language UI or device control is introduced. */
internal class LanguageContextChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false
            test.runOnMainSync { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < deadline) { "Language context condition timed out" }
            Thread.sleep(50)
        }
    }
    private fun service(home: HomeActivity): MobileService? = HomeActivity::class.java
        .getDeclaredField("service").apply { isAccessible = true }.get(home) as? MobileService
    private fun containsText(view: View, text: String): Boolean {
        if (view is TextView && view.text.toString() == text && view.isShown &&
            view.getGlobalVisibleRect(android.graphics.Rect())) return true
        return view is ViewGroup && (0 until view.childCount).any { containsText(view.getChildAt(it), text) }
    }
    fun run(): HomeActivity {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val oldLanguage = app.languagePreferences.current
        val languagePrefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldLanguageStorage = languagePrefs.all.toMap()
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldAppearance = appearance.all.toMap()
        var home = test.startActivitySync(Intent(context, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
        test.waitForIdleSync()
        awaitMain { service(home)?.running == true }
        var owner: MobileService? = null
        var initialMessage: SnapshotMessage? = null
        test.runOnMainSync { owner = service(home); initialMessage = owner!!.snapshot.message }
        val preserved = listOf("devices", "curves", "presets", "shot_safety", "machine_write_safety", "shot_history")
            .associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        fun rebuild() {
            val monitor = test.addMonitor(HomeActivity::class.java.name, null, false)
            try {
                test.runOnMainSync { home.recreate() }
                home = test.waitForMonitorWithTimeout(monitor, 10000) as? HomeActivity
                    ?: error("Language Home recreation not observed")
                test.waitForIdleSync()
                awaitMain { service(home) != null }
            } finally { test.removeMonitor(monitor) }
        }
        try {
            for (language in AppLanguage.entries) for (dark in listOf(false, true)) {
                test.runOnMainSync {
                    check(app.languagePreferences.select(language) != AppLanguagePreference.Selection.SAVE_FAILED)
                    check(appearance.edit().putBoolean("dark", dark).commit())
                }
                rebuild()
                test.runOnMainSync {
                    // Resolve the expected Android resource independently from the production context wrapper.
                    val config = Configuration(context.resources.configuration).apply { setLocale(language.locale) }
                    val expected = context.createConfigurationContext(config)
                    check(home.resources.configuration.locales[0] == language.locale)
                    check(app.resources.configuration.locales[0] == language.locale)
                    check(owner!!.resources.configuration.locales[0] == language.locale)
                    // Settings is now a secondary entry, not a bottom-navigation destination.
                    // Independently verify every actual destination in all three resource contexts.
                    for (id in listOf(R.string.ui_brew, R.string.ui_curves, R.string.ui_history, R.string.ui_beans)) {
                        val label = expected.getString(id)
                        check(home.getString(id) == label && app.getString(id) == label && owner!!.getString(id) == label)
                        check(containsText(home.window.decorView, label)) {
                            "Visible Home navigation did not use ${language.tag}: $label"
                        }
                    }
                    val expectedDirection = if (language.rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                    check(home.window.decorView.layoutDirection == expectedDirection)
                    check((home.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                    LanguageUiLayoutChecks.components(home, language, dark)
                    check(service(home) === owner) { "Language recreation replaced the Mock service" }
                    check(owner!!.snapshot.message === initialMessage) { "Language recreation replayed an event" }
                    if (initialMessage != null) {
                        check(owner!!.snapshot.messageForDisplay { id, args -> owner!!.getString(id, *args) } ==
                            initialMessage!!.render { id, args -> expected.getString(id, *args) })
                    }
                    check(languagePrefs.all["tag"] ==
                        language.tag || (language == oldLanguage && language == AppLanguage.CHINESE))
                    preserved.forEach { (name, values) ->
                        check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) { "Display change mutated $name" }
                    }
                    // This method is intentionally a no-op for Mock: it does not prove real notification updates.
                    owner!!.refreshNotificationDisplay()
                }
            }
        } finally {
            test.runOnMainSync {
                check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED)
                restoreMockPreferences(languagePrefs, oldLanguageStorage)
                restoreMockPreferences(appearance, oldAppearance)
                check(app.languagePreferences.current == oldLanguage)
            }
            rebuild()
        }
        return home
    }
}

internal fun restoreMockPreferences(prefs: android.content.SharedPreferences, values: Map<String, *>) {
    val edit = prefs.edit().clear()
    values.forEach { (key, value) ->
        when (value) {
            is Boolean -> edit.putBoolean(key, value)
            is String -> edit.putString(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            else -> error("Unsupported preference: $key")
        }
    }
    check(edit.commit() && prefs.all == values)
}
