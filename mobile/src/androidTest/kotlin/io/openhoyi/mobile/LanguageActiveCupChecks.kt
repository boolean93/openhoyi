package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Button
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.session.ExtractionState

/** Runs real product start and translated stop affordances, only against the guarded Mock runtime. */
internal class LanguageActiveCupChecks(private val test: Instrumentation) {
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false
            test.runOnMainSync { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < deadline) { "Active language condition timed out" }
            Thread.sleep(50)
        }
    }
    private fun service(screen: ExtractionActivity): MobileService? = ExtractionActivity::class.java
        .getDeclaredField("service").apply { isAccessible = true }.get(screen) as? MobileService
    private fun stop(screen: ExtractionActivity): Button = ExtractionActivity::class.java
        .getDeclaredField("stop").apply { isAccessible = true }.get(screen) as Button
    private fun observedState(owner: MobileService): ExtractionState = MobileService::class.java
        .getDeclaredField("lastShotState").apply { isAccessible = true }.get(owner) as ExtractionState
    private fun cupId(owner: MobileService): String? {
        val series = MobileService::class.java.getDeclaredField("series").apply { isAccessible = true }.get(owner)
        return ShotSeries::class.java.getDeclaredField("id").apply { isAccessible = true }.get(series) as? String
    }
    fun run() {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val oldLanguage = app.languagePreferences.current
        val languagePrefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldLanguageStorage = languagePrefs.all.toMap()
        val curves = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val oldCurves = curves.all.toMap()
        var screen = test.startActivitySync(Intent(context, ExtractionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ExtractionActivity
        test.waitForIdleSync()
        awaitMain { service(screen)?.machineSettingsFresh == true }
        var owner: MobileService? = null
        test.runOnMainSync { owner = service(screen) }
        val original = requireNotNull(owner)
        val safety = listOf("devices", "presets", "shot_safety", "machine_write_safety")
            .associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        try {
            test.runOnMainSync { check(curves.edit().putString("selected", "capture-2").commit()) }
            for (language in AppLanguage.entries) {
                test.runOnMainSync { check(original.startShot("capture-2", null, 7) == null) }
                awaitMain { original.snapshot.coffee is ExtractionTelemetry && original.chartPoints.isNotEmpty() &&
                    observedState(original) == ExtractionState.RUNNING }
                var id: String? = null
                var first: ShotPoint? = null
                var message: SnapshotMessage? = null
                test.runOnMainSync {
                    id = requireNotNull(cupId(original)); first = original.chartPoints.first()
                    message = original.snapshot.message
                    check(app.languagePreferences.select(language) != AppLanguagePreference.Selection.SAVE_FAILED)
                }
                val monitor = test.addMonitor(ExtractionActivity::class.java.name, null, false)
                try {
                    test.runOnMainSync { screen.recreate() }
                    screen = test.waitForMonitorWithTimeout(monitor, 10000) as? ExtractionActivity
                        ?: error("Active Extraction recreation not observed")
                    test.waitForIdleSync()
                    awaitMain { service(screen) === original && stop(screen).isEnabled && stop(screen).isShown }
                } finally { test.removeMonitor(monitor) }
                test.runOnMainSync {
                    check(screen.resources.configuration.locales[0] == language.locale)
                    check(original.shotState == ExtractionState.RUNNING && cupId(original) == id)
                    check(original.chartPoints.first() == first && original.snapshot.message === message)
                    val action = stop(screen)
                    check(action.text.toString() == screen.getString(R.string.home_stop))
                    check(action.getGlobalVisibleRect(android.graphics.Rect()))
                    LanguageUiLayoutChecks.textFits(action, "${language.tag} active cup fixed stop")
                    safety.forEach { (name, values) ->
                        check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all == values)
                    }
                    check(action.performClick())
                    // Mock stop is synchronous. Do not let a later natural ending satisfy the button check.
                    check(original.shotState == ExtractionState.ENDED_OBSERVED)
                }
                awaitMain { original.shotState == ExtractionState.ENDED_OBSERVED && original.snapshot.coffee !is ExtractionTelemetry &&
                    observedState(original) == ExtractionState.ENDED_OBSERVED && cupId(original) == null }
            }
        } finally {
            test.runOnMainSync {
                original.stopShot()
                check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED)
                restoreMockPreferences(languagePrefs, oldLanguageStorage)
                restoreMockPreferences(curves, oldCurves)
                screen.finish()
            }
            test.waitForIdleSync()
        }
    }
}
