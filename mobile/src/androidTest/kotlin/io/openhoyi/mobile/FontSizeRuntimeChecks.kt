package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.session.ExtractionState

/** Real application contexts and recreation, guarded Mock only. Does not access BLE. */
internal class FontSizeRuntimeChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun await(label: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < end) { label }
            Thread.sleep(40)
        }
    }
    private fun <T : Activity> recreate(screen: T, type: Class<T>): T {
        val monitor = test.addMonitor(type.name, null, false)
        return try {
            main { screen.recreate() }
            type.cast(test.waitForMonitorWithTimeout(monitor, 10000)) ?: error("Missing replacement ${type.name}")
        } finally { test.removeMonitor(monitor) }
    }
    private fun select(size: AppFontSize) {
        check(app.fontSizePreferences.select(size) != AppFontSizePreference.Selection.SAVE_FAILED)
    }
    fun run() {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val original = app.fontSizePreferences.current
        val preferences = context.getSharedPreferences("app_typography", Context.MODE_PRIVATE)
        val stored = preferences.all.toMap()
        try {
            configurationConversions()
            previewAndApply()
            preheatRecreation()
            activeCupRecreation()
            editorDraft()
        } finally {
            main { select(original); restoreMockPreferences(preferences, stored) }
        }
    }
    private fun configurationConversions() = main {
        for (systemScale in listOf(.85f, 2f)) for (size in AppFontSize.entries) {
            val base = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = systemScale })
            val result = AppLanguageContext.wrap(base, AppLanguage.ENGLISH, true, size)
            val config = result.resources.configuration
            check(config.fontScale == size.scale)
            check(config.densityDpi == base.resources.configuration.densityDpi)
            check(config.screenWidthDp == base.resources.configuration.screenWidthDp)
            check(config.locales[0] == AppLanguage.ENGLISH.locale)
            check(config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            for (sp in listOf(12f, 16f, 24f, 48f)) {
                val view = TextView(result).apply { textSize = sp }
                check(kotlin.math.abs(view.textSize - AppTextUnits.pixels(result.resources, sp)) < .01f)
                check(kotlin.math.abs(AppTextUnits.sp(result.resources, view.textSize) - sp) < .05f)
            }
        }
    }
    private fun previewAndApply() {
        val home = MainPageTestHost.launch(test, timeoutMs = 10000).activity
        await("Mock owner not ready") { home.currentService != null }
        var card: AppFontSizePreferencesCard? = null
        var parent: LinearLayout? = null
        var applies = 0
        val initial = app.fontSizePreferences.current
        val other = AppFontSize.entries.first { it != initial }
        fun bounds(view: View): android.graphics.Rect {
            val location = IntArray(2); view.getLocationOnScreen(location)
            return android.graphics.Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        fun assertWindow(dialog: AlertDialog) {
            val decor = checkNotNull(dialog.window).decorView
            val hostBounds = bounds(home.window.decorView)
            val dialogBounds = bounds(decor)
            check(hostBounds.contains(dialogBounds)) { "Font dialog escapes host: dialog=$dialogBounds host=$hostBounds" }
            for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) {
                val button = dialog.getButton(which)
                val visible = android.graphics.Rect()
                check(button.isShown && button.getGlobalVisibleRect(visible))
                check(visible.width() == button.width && visible.height() == button.height && dialogBounds.contains(bounds(button)))
                val minimum = HoyiUi.dp(home, 48)
                check(button.width >= minimum && button.height >= minimum) { "Font action smaller than 48dp" }
            }
            val preview = decor.findViewWithTag<LinearLayout>("product/settings/app/font-size/preview")
            val previewBounds = bounds(preview)
            check(previewBounds.left >= dialogBounds.left && previewBounds.right <= dialogBounds.right) { "Preview clipped horizontally" }
            for (index in 0 until preview.childCount) {
                val text = preview.getChildAt(index) as? TextView ?: continue
                val layout = checkNotNull(text.layout)
                val available = text.width - text.compoundPaddingLeft - text.compoundPaddingRight
                for (line in 0 until layout.lineCount) {
                    check(layout.getEllipsisCount(line) == 0)
                    check(layout.getLineRight(line) - layout.getLineLeft(line) <= available + 1f) { "Preview text clipped: ${text.text}" }
                }
            }
        }
        fun open(): AlertDialog {
            var dialog: AlertDialog? = null
            main {
                check(checkNotNull(parent).findViewWithTag<View>("product/settings/app/font-size/current").performClick())
                dialog = AppFontSizePreferencesCard::class.java.getDeclaredField("dialog").apply { isAccessible = true }
                    .get(card) as AlertDialog
            }
            val result = checkNotNull(dialog)
            val frames = java.util.concurrent.CountDownLatch(1)
            main { result.window!!.decorView.postOnAnimation {
                result.window!!.decorView.postOnAnimation { frames.countDown() }
            } }
            check(frames.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "Font dialog did not draw two layout frames" }
            await("Font dialog Apply listener/layout not ready") {
                val action = result.getButton(AlertDialog.BUTTON_POSITIVE)
                result.isShowing && action?.tag == "product/settings/app/font-size/apply" && action.isLaidOut && action.isShown &&
                    !action.isLayoutRequested && !result.window!!.decorView.isLayoutRequested &&
                    result.window!!.decorView.findViewWithTag<View>("product/settings/app/font-size/preview").isLaidOut
            }
            main { assertWindow(result) }
            return result
        }
        fun choose(dialog: AlertDialog) {
            main {
                val choices = dialog.window!!.decorView.findViewWithTag<RadioGroup>("product/settings/app/font-size/choices")
                val choice = choices.getChildAt(other.ordinal) as RadioButton
                // CompoundButton toggles before View.performClick; without an OnClickListener
                // that return value may be false even though the real radio action succeeded.
                choice.performClick()
                check(choice.isChecked && choices.checkedRadioButtonId == choice.id)
                for (index in 0 until choices.childCount) {
                    check((choices.getChildAt(index) as RadioButton).isChecked == (index == other.ordinal))
                }
            }
            await("Preview selection layout pending") {
                val preview = dialog.window!!.decorView.findViewWithTag<LinearLayout>("product/settings/app/font-size/preview")
                preview.getChildAt(0).isLaidOut && !preview.isLayoutRequested
            }
            main { assertWindow(dialog) }
        }
        try {
            main {
                parent = LinearLayout(home)
                card = AppFontSizePreferencesCard(home, checkNotNull(parent), SettingsVisualStyle(home)) { applies++ }
            }
            val cancelled = open()
            choose(cancelled)
            main {
                val preview = cancelled.window!!.decorView.findViewWithTag<LinearLayout>("product/settings/app/font-size/preview")
                check(preview.getChildAt(0).resources.configuration.fontScale == other.scale)
                check(app.fontSizePreferences.current == initial && home.resources.configuration.fontScale == initial.scale)
                check(applies == 0)
                check(cancelled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick())
            }
            await("Cancelled font preview still showing") { !cancelled.isShowing }
            main { check(app.fontSizePreferences.current == initial && applies == 0) }
            val unchanged = open()
            main { check(unchanged.getButton(AlertDialog.BUTTON_POSITIVE).performClick()) }
            await("Unchanged font dialog still showing") { !unchanged.isShowing }
            main { check(applies == 0 && app.fontSizePreferences.current == initial) }
            val changed = open()
            choose(changed)
            main { check(changed.getButton(AlertDialog.BUTTON_POSITIVE).performClick()) }
            await("Font Apply did not publish exactly once") { !changed.isShowing && applies == 1 }
            main { check(app.fontSizePreferences.current == other && applies == 1) }
        } finally { main { card?.close(); home.finish() } }
    }
    private fun preheatRecreation() {
        var screen = MainPageTestHost.launch(test, timeoutMs = 10000).activity
        await("Mock owner not ready for preheat") { screen.currentService?.machineSettingsFresh == true }
        val owner = checkNotNull(screen.currentService)
        fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
        val runtime = field(owner, "mock").get(owner) as MockDeviceRuntime
        val oldSettings = field(runtime, "settings").get(runtime) as io.openhoyi.protocol.Settings
        val oldTemperature = field(runtime, "brewTemperatureHundredths").getInt(runtime)
        val curves = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val oldCurves = curves.all.toMap()
        val originalHistory = app.history.entries
        val initialShotState = owner.shotState
        val profile = app.curves.items.asSequence().filter { it.factoryCurve != null }
            .mapNotNull { app.curves.resolve(it.id, true, 7) }.first { it.temperatureC >= 85 }
        try {
            main {
                check(field(owner, "hub").get(owner) == null)
                field(runtime, "settings").set(runtime, oldSettings.copy(flags = oldSettings.flags or 0x04))
                field(runtime, "brewTemperatureHundredths").setInt(runtime, 7500)
            }
            await("Cold studio readback missing") {
                owner.snapshot.settings?.flags?.and(0x04) == 0x04 &&
                    (owner.currentCorrectedBrewTemperature() ?: Int.MAX_VALUE) < 8000
            }
            var intent: Any? = null
            var request: Any? = null
            val preparation = field(owner, "brewPreparation").get(owner) as io.openhoyi.session.BrewPreparation
            main {
                screen.useCurve(profile.id)
                check(owner.brewPreparationState == io.openhoyi.session.BrewPreparation.State.WAITING_TEMP)
                intent = checkNotNull(field(owner, "autoPreheatIntent").get(owner))
                request = checkNotNull(preparation.requestId)
                check(runtime.preheatActive)
                // Freeze only the Mock temperature fixture, not the application clock or real protocol.
                field(runtime, "preheatStartedAtMs").setLong(runtime, SystemClock.elapsedRealtime() + 30000)
                select(if (app.fontSizePreferences.current == AppFontSize.LARGE) AppFontSize.SMALL else AppFontSize.LARGE)
            }
            screen = recreate(screen, MainActivity::class.java)
            await("Preheat recreation lost its service") { screen.currentService === owner }
            main {
                check(field(owner, "autoPreheatIntent").get(owner) === intent)
                check(preparation.requestId == request && runtime.preheatActive)
                check(preparation.state == io.openhoyi.session.BrewPreparation.State.WAITING_TEMP)
                check(owner.shotState == initialShotState && app.history.entries == originalHistory)
                check(field(checkNotNull(intent), "leftAt").get(intent) == null)
                // Navigation is a separate action and intentionally retains the product cancellation rule.
                screen.select(TaskDestination.BREW)
                check(!runtime.preheatActive && preparation.state == io.openhoyi.session.BrewPreparation.State.IDLE)
            }
        } finally {
            main {
                owner.leaveAutoPreheat()
                field(runtime, "settings").set(runtime, oldSettings)
                field(runtime, "brewTemperatureHundredths").setInt(runtime, oldTemperature)
                restoreMockPreferences(curves, oldCurves)
                screen.finish()
            }
        }
        await("Mock restored settings not observed") { owner.snapshot.settings?.flags == oldSettings.flags }
    }
    private fun activeCupRecreation() {
        var screen = MainPageTestHost.launch(test, extraction = true, timeoutMs = 10000).activity
        await("Mock settings not fresh") { screen.currentService?.machineSettingsFresh == true }
        val owner = checkNotNull(screen.currentService)
        try {
            main { check(owner.startShot("factory-v3-002", owner.snapshot.scaleState == io.openhoyi.session.DeviceState.READY, 7) == null) }
            await("Mock extraction did not begin") { owner.snapshot.coffee is ExtractionTelemetry && owner.chartPoints.isNotEmpty() && owner.shotState == ExtractionState.RUNNING }
            val identity = owner.chartTraceIdentity
            val recipe = owner.activeShotRecipe
            val first = owner.chartPoints.first()
            for (size in AppFontSize.entries) {
                main { select(size) }
                screen = recreate(screen, MainActivity::class.java)
                await("Recreated host lost service") { screen.currentService === owner }
                main {
                    check(screen.resources.configuration.fontScale == size.scale)
                    check(app.resources.configuration.fontScale == size.scale)
                    check(owner.resources.configuration.fontScale == size.scale)
                    check(owner.shotState == ExtractionState.RUNNING)
                    check(owner.chartTraceIdentity == identity && owner.activeShotRecipe === recipe)
                    check(owner.chartPoints.first() == first)
                    val fragment = screen.supportFragmentManager.findFragmentByTag("extraction") as ExtractionFragment
                    val stop = fragment.requireView().findViewWithTag<View>("product/extraction/stop")
                    check(stop.isEnabled && stop.isShown)
                    check(stop.minimumHeight >= HoyiUi.dp(screen, 48))
                }
            }
        } finally {
            main { owner.stopShot(); screen.finish() }
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun editorDraft() {
        var screen = test.startActivitySync(Intent(context, CurveEditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CurveEditorActivity
        fun fields() = CurveEditorActivity::class.java.getDeclaredField("fields").apply { isAccessible = true }.get(screen) as Map<String, EditText>
        var key = ""
        try {
            main {
                val inputs = fields()
                check(inputs.isNotEmpty())
                key = inputs.keys.first { it != "name" }
                inputs.getValue("name").setText("font-size-unsaved")
                inputs.getValue(key).apply { setText("12."); requestFocus(); setSelection(1, 2) }
                select(if (app.fontSizePreferences.current == AppFontSize.SMALL) AppFontSize.LARGE else AppFontSize.SMALL)
            }
            screen = recreate(screen, CurveEditorActivity::class.java)
            await("Editor draft/focus restoration pending") {
                fields()[key]?.let { it.text.toString() == "12." && it.hasFocus() && it.selectionStart == 1 && it.selectionEnd == 2 } == true
            }
            main {
                check(fields().getValue("name").text.toString() == "font-size-unsaved")
                val input = fields().getValue(key)
                check(input.text.toString() == "12.")
                check(screen.resources.configuration.fontScale == app.fontSizePreferences.current.scale)
            }
        } finally { main { screen.finish() } }
    }
}
