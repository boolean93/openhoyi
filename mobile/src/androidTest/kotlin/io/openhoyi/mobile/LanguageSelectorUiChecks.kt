package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.os.SystemClock
import android.widget.LinearLayout

/** Actual local preference widget; no machine command or positive extraction confirmation. */
internal class LanguageSelectorUiChecks(private val test: Instrumentation) {
    private fun onMain(action: () -> Unit) {
        var error: Throwable? = null
        test.runOnMainSync { try { action() } catch (failure: Throwable) { error = failure } }
        error?.let { throw it }
    }
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var done = false
            onMain { done = condition() }
            if (done) return
            check(SystemClock.elapsedRealtime() < deadline) { "Language selector condition timed out" }
            Thread.sleep(25)
        }
    }
    private fun owner(page: MachineSettingsActivity) = MachineSettingsActivity::class.java.getDeclaredField("service")
        .apply { isAccessible = true }.get(page) as? MobileService
    private fun card(page: MachineSettingsActivity) = MachineSettingsActivity::class.java.getDeclaredField("languageCard")
        .apply { isAccessible = true }.get(page) as AppLanguagePreferencesCard
    fun run(initial: MachineSettingsActivity, onPageChanged: (MachineSettingsActivity) -> Unit): MachineSettingsActivity {
        check(BuildConfig.MOCK_MODE && initial.packageName == "io.openhoyi.mobile.mock")
        var current = initial
        val app = initial.application as MobileApplication
        val originalLanguage = app.languagePreferences.current
        val prefs = initial.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val originalStorage = prefs.all.toMap()
        val service = requireNotNull(owner(initial))
        val message = service.snapshot.message
        val alternate = if (originalLanguage == AppLanguage.ENGLISH) AppLanguage.ARABIC else AppLanguage.ENGLISH
        fun open(): android.app.AlertDialog {
            var dialog: android.app.AlertDialog? = null
            onMain {
                check(card(current).button.performClick())
                dialog = requireNotNull(card(current).dialog)
                check(dialog!!.isShowing && dialog!!.listView.adapter.count == 8)
                for (index in AppLanguage.entries.indices)
                    check(dialog!!.listView.adapter.getItem(index).toString() == AppLanguage.entries[index].nativeName)
                check(dialog!!.listView.checkedItemPosition == AppLanguage.entries.indexOf(app.languagePreferences.current))
            }
            return requireNotNull(dialog)
        }
        fun choose(language: AppLanguage) {
            val dialog = open()
            val monitor = test.addMonitor(MachineSettingsActivity::class.java.name, null, false)
            try {
                onMain {
                    val index = AppLanguage.entries.indexOf(language)
                    check(dialog.listView.performItemClick(null, index, dialog.listView.adapter.getItemId(index)))
                }
                current = test.waitForMonitorWithTimeout(monitor, 10000) as? MachineSettingsActivity
                    ?: error("Language selector did not recreate settings")
                onPageChanged(current)
                test.waitForIdleSync()
                awaitMain { owner(current) === service && current.hasWindowFocus() }
                onMain {
                    check(app.languagePreferences.current == language && prefs.getString("tag", null) == language.tag)
                    check(current.resources.configuration.locales[0] == language.locale && service.resources.configuration.locales[0] == language.locale)
                    check(card(current).button.text.toString() == current.getString(R.string.language_current, language.nativeName))
                    check(service.snapshot.message === message)
                }
            } finally { test.removeMonitor(monitor) }
        }
        var failure: Throwable? = null
        try {
            val unchanged = open()
            onMain {
                val index = AppLanguage.entries.indexOf(originalLanguage)
                check(unchanged.listView.performItemClick(null, index, unchanged.listView.adapter.getItemId(index)))
                check(!unchanged.isShowing && !current.isDestroyed && prefs.all == originalStorage)
            }
            choose(alternate)
            choose(originalLanguage)
            onMain {
                var callbacks = 0
                val failed = AppLanguagePreference(object : AppLanguagePreference.Storage {
                    override fun read() = originalLanguage.tag
                    override fun write(tag: String) = false
                })
                val fixture = AppLanguagePreferencesCard(current, LinearLayout(current), failed) { callbacks++ }
                try {
                    check(fixture.button.performClick())
                    val dialog = requireNotNull(fixture.dialog)
                    val index = AppLanguage.entries.indexOf(alternate)
                    check(dialog.listView.performItemClick(null, index, dialog.listView.adapter.getItemId(index)))
                    check(failed.current == originalLanguage && callbacks == 0 && dialog.isShowing)
                    check(dialog.listView.checkedItemPosition == AppLanguage.entries.indexOf(originalLanguage))
                    check(fixture.button.text.toString() == current.getString(R.string.language_current, originalLanguage.nativeName))
                } finally { fixture.close() }
            }
        } catch (error: Throwable) { failure = error }
        finally {
            fun cleanup(action: () -> Unit) {
                try { action() } catch (error: Throwable) {
                    if (failure == null) failure = error else failure!!.addSuppressed(error)
                }
            }
            cleanup { onMain { check(app.languagePreferences.select(originalLanguage) != AppLanguagePreference.Selection.SAVE_FAILED) } }
            cleanup { onMain { restoreMockPreferences(prefs, originalStorage) } }
            cleanup { onMain { if (failure != null && current !== initial) current.finish() } }
        }
        failure?.let { throw it }
        return current
    }
}
