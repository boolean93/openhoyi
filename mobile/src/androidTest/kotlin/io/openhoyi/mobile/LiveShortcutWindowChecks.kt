package io.openhoyi.mobile

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.Button
import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.ExtractionState

/** Disposable Mock app only; never connect, select or operate an appliance. */
internal class LiveShortcutWindowChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) = BoundedInstrumentationMain.run("live shortcuts", 5000, action)

    private fun screenshot(path: java.io.File) {
        // A live chart may keep the main queue drawing indefinitely, even though the frame is ready.
        SystemClock.sleep(300)
        val image = checkNotNull(test.uiAutomation.takeScreenshot())
        try {
            path.outputStream().use { check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            image.recycle()
        }
    }

    fun run(appFont: AppFontSize) {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val originalFont = app.fontSizePreferences.current
        val presets = context.getSharedPreferences("presets", Context.MODE_PRIVATE)
        val selection = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val options = context.getSharedPreferences(ShortcutStartPolicy.PREFERENCES, Context.MODE_PRIVATE)
        val original = (1..4).associateWith { presets.getString(PresetSlots.key(it), null) }
        val oldSelection = selection.getString("selected", null)
        val oldOption = options.all[ShortcutStartPolicy.DIRECT_START] as? Boolean
        val existingShots = app.history.entries.map { it.id }.toSet()
        val draft = CustomCurveDocument(CustomCurveDocument.newId(),
            "超长的自定义曲线名称：浅焙豆低压浸润｜慢升压到峰值｜流量收束用于不同粉量对照与手动记录",
            93, 3600, CustomCurveDocument.ControlMode.PRESSURE,
            listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
        val curveFixture = CustomCurveWindowFixture(app.customCurvesResult.getOrThrow())
        var activity: MainActivity? = null
        var ownedShot: String? = null
        var service: MobileService? = null
        var mock: MockDeviceRuntime? = null
        var originalSettings: io.openhoyi.protocol.Settings? = null
        var originalTemperature: Int? = null
        var ownsPreparation = false
        val runtimeField = MobileService::class.java.getDeclaredField("mock").apply { isAccessible = true }
        val settingsField = MockDeviceRuntime::class.java.getDeclaredField("settings").apply { isAccessible = true }
        val tempField = MockDeviceRuntime::class.java.getDeclaredField("brewTemperatureHundredths").apply { isAccessible = true }
        val refreshMock = MobileService::class.java.getDeclaredMethod("refreshMock", MockDeviceRuntime::class.java,
            java.lang.Long.TYPE).apply { isAccessible = true }
        val prepDialogs = ExtractionFragment::class.java.getDeclaredField("transientDialogs").apply { isAccessible = true }
        val dialogField = ExtractionFragment::class.java.getDeclaredField("liveShortcutsDialog").apply { isAccessible = true }
        fun await(label: String, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 12000
            while (true) {
                var passed = false
                main { passed = condition() }
                if (passed) return
                check(SystemClock.elapsedRealtime() < deadline) { "Timed out: $label" }
                SystemClock.sleep(40)
            }
        }
        fun direct(enabled: Boolean) {
            check(options.edit().putBoolean(ShortcutStartPolicy.DIRECT_START, enabled).commit())
        }
        fun bind(slot: Int, value: String) {
            check(presets.edit().putString(PresetSlots.key(slot), value).commit())
        }
        fun noBle(owner: MobileService) = check(
            MobileService::class.java.getDeclaredField("hub").apply { isAccessible = true }.get(owner) == null)
        try {
            main {
                check(app.beanPreparationResult.getOrThrow().current() == null)
                check(app.fontSizePreferences.select(appFont) != AppFontSizePreference.Selection.SAVE_FAILED)
                check(selection.edit().putString("selected", "factory-v3-001").commit())
                direct(false)
                bind(1, "factory-v3-001")
                bind(2, PresetSlots.EMPTY)
                bind(3, "draft-test-missing")
                bind(4, "factory-v3-002")
                curveFixture.replace(draft)
            }
            val page = MainPageTestHost.launch(test, extraction = true, timeoutMs = 15000)
            val host = page.activity
            activity = host
            await("Mock ready") { host.currentService?.running == true &&
                host.currentService?.snapshot?.coffeeState == io.openhoyi.session.DeviceState.READY &&
                host.supportFragmentManager.primaryNavigationFragment is ExtractionFragment }
            val owner = checkNotNull(host.currentService)
            service = owner
            val fragment = page.active("extraction") as ExtractionFragment
            val configuration = host.resources.configuration
            val evidence = java.io.File(context.filesDir, "live-shortcut-checks").apply { mkdirs() }
            java.io.File(evidence, "viewport.json").writeText(org.json.JSONObject()
                .put("widthDp", configuration.screenWidthDp).put("heightDp", configuration.screenHeightDp)
                .put("densityDpi", configuration.densityDpi).put("fontScale", configuration.fontScale.toDouble())
                .put("appFont", app.fontSizePreferences.current.key)
                .put("portrait", configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT)
                .toString())
            fun shortcutButton() = fragment.requireView().findViewWithTag<Button>("product/extraction/shortcuts")
            fun identity() = fragment.requireView().findViewWithTag<android.widget.TextView>("product/extraction/identity").text.toString()
            fun open() = checkNotNull(dialogField.get(fragment)) as AlertDialog
            fun row(slot: Int) = checkNotNull(open().window?.decorView)
                .findViewWithTag<Button>("product/extraction/shortcut/$slot")
            fun noShot() {
                check(!ShotGate.active(owner.shotState))
                check(app.history.entries.map { it.id }.toSet() == existingShots)
                noBle(owner)
            }
            await("visible Quick slots action") {
                val bounds = android.graphics.Rect()
                val shortcut = shortcutButton()
                shortcut.isEnabled && shortcut.getGlobalVisibleRect(bounds) &&
                    bounds.width() >= shortcut.width && bounds.height() >= shortcut.height
            }
            screenshot(java.io.File(evidence, "idle.png"))
            // Collect the clean menu before deliberately triggering rejection toasts.
            main {
                bind(1, PresetSlots.bind(draft.id))
                check(shortcutButton().performClick())
                val long = row(1)
                check(long.text.toString().endsWith(draft.name) && long.ellipsize == null)
            }
            screenshot(java.io.File(evidence, "expanded-long-name.png"))
            main {
                check(row(1).performClick())
                check(selection.getString("selected", null) == draft.id)
                check((ExtractionFragment::class.java.getDeclaredField("presetSlot").apply { isAccessible = true }
                    .get(fragment) as Int) == 7)
                noShot()

                bind(1, "factory-v3-001")
            }
            main {
                noShot()
                check(shortcutButton().performClick())
                check(row(1).isEnabled && !row(2).isEnabled && !row(3).isEnabled && row(4).isEnabled)
                val displayed = checkNotNull(app.curves.find("factory-v3-002")).name
                check(row(4).text.toString().endsWith(displayed) && row(4).ellipsize == null)
                val initial = identity()
                // A row that was shown before the preset changed must never select the replacement.
                val stale = row(4)
                bind(4, "factory-v3-003")
                check(stale.performClick())
                check(identity() == initial)
                noShot()
                bind(4, "factory-v3-002")
                check(shortcutButton().performClick())
                open().dismiss()
                noShot()
                check(shortcutButton().performClick())
                check(row(4).performClick())
                check(identity().contains(displayed))
                noShot()
                val activeConnection = MainActivity::class.java.getDeclaredField("connection").apply { isAccessible = true }
                    .get(host) as android.content.ServiceConnection
                val component = android.content.ComponentName(host, MobileService::class.java)
                check(shortcutButton().performClick())
                val staleSession = checkNotNull(host.liveShortcutSession(fragment))
                val unchanged = identity()
                val oldChoice = host.liveShortcutChoices().first()
                activeConnection.onServiceDisconnected(component)
                check(dialogField.get(fragment) == null)
                activeConnection.onServiceConnected(component, owner.LocalBinder())
                host.openLiveShortcut(fragment, staleSession, oldChoice)
                check(identity() == unchanged)
                noShot()

            }
            fun requestPreheat(): AlertDialog {
                check(shortcutButton().performClick())
                check(row(4).performClick())
                val dialog = (prepDialogs.get(fragment) as List<*>).lastOrNull() as? AlertDialog
                return checkNotNull(dialog).also { check(it.isShowing) }
            }
            var cancelledDialog: AlertDialog? = null
            var approvedDialog: AlertDialog? = null
            main {
                // Synthetic manual detection on the Mock owner; no telemetry/device injected externally.
                check(shortcutButton().performClick())
                val passive = MobileService::class.java.getDeclaredField("passiveShot").apply { isAccessible = true }.get(owner)
                val manualActive = passive.javaClass.getDeclaredField("active").apply { isAccessible = true }
                val render = ExtractionFragment::class.java.getDeclaredMethod("render").apply { isAccessible = true }
                try {
                    manualActive.setBoolean(passive, true)
                    render.invoke(fragment)
                    check(dialogField.get(fragment) == null && !shortcutButton().isEnabled)
                    noShot()
                } finally {
                    manualActive.setBoolean(passive, false)
                    render.invoke(fragment)
                }

                direct(true)
                val runtime = checkNotNull(runtimeField.get(owner) as? MockDeviceRuntime)
                mock = runtime
                val settings = settingsField.get(runtime) as io.openhoyi.protocol.Settings
                originalSettings = settings
                originalTemperature = tempField.getInt(runtime)
                settingsField.set(runtime, settings.copy(flags = settings.flags or 0x04))
                tempField.setInt(runtime, 7500)
                refreshMock.invoke(owner, runtime, MobileMonotonicClock.elapsedRealtime())
                val dialog = requestPreheat()
                cancelledDialog = dialog
                check(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick())
            }
            // AlertController posts listener and dismissal messages to main. Return from the
            // click block before checking the actual product response or opening another dialog.
            await("cancel dialog dismissed") { cancelledDialog?.isShowing == false }
            main {
                check(owner.brewPreparationState == BrewPreparation.State.IDLE)
                noShot()
                val dialog = requestPreheat()
                approvedDialog = dialog
                check(dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick())
            }
            await("confirmed preheat while the shot remains idle") {
                val state = owner.brewPreparationState
                ownsPreparation = state != BrewPreparation.State.IDLE && owner.brewPreparationProfileId == "factory-v3-002"
                noShot()
                check(state !in setOf(BrewPreparation.State.FAILED, BrewPreparation.State.UNKNOWN)) {
                    "Preheat transition failed: $state"
                }
                approvedDialog?.isShowing == false && ownsPreparation &&
                    state in setOf(BrewPreparation.State.WRITING, BrewPreparation.State.WAITING_TEMP, BrewPreparation.State.READY) &&
                    !shortcutButton().isEnabled
            }
            await("target reached without a deferred start") {
                owner.brewPreparationState == BrewPreparation.State.READY
            }
            main {
                noShot()
                check(owner.cancelBrewPreparation() == null)
                ownsPreparation = false
                val runtime = checkNotNull(mock)
                settingsField.set(runtime, checkNotNull(originalSettings))
                tempField.setInt(runtime, checkNotNull(originalTemperature))
                refreshMock.invoke(owner, runtime, MobileMonotonicClock.elapsedRealtime())
            }
            await("explicit selection available again") { shortcutButton().isEnabled }
            var secondRow: LiveShortcutChoice? = null
            var secondSession: LiveShortcutSession<MobileService>? = null
            main {
                check(shortcutButton().performClick())
                secondRow = host.liveShortcutChoices().first()
                secondSession = checkNotNull(host.liveShortcutSession(fragment))
                check(row(4).performClick())
                ownedShot = owner.chartTraceIdentity
                check(ownedShot != null && ownedShot !in existingShots) { "One explicit tap did not start a Mock shot" }
                check(!shortcutButton().isEnabled)
            }
            screenshot(java.io.File(evidence, "running-stop-visible.png"))
            main {
                // Preserve the explicit second-start rejection check without capturing its toast.
                host.openLiveShortcut(fragment, checkNotNull(secondSession), checkNotNull(secondRow))
                check(owner.chartTraceIdentity == ownedShot)
                check(!shortcutButton().isEnabled)
                val stop = fragment.requireView().findViewWithTag<Button>("product/extraction/stop")
                val bounds = android.graphics.Rect()
                check(stop.isShown && stop.isEnabled && stop.getGlobalVisibleRect(bounds) &&
                    bounds.width() >= stop.width && bounds.height() >= stop.height && stop.performClick())
            }
            await("Mock stop; no queued next cup") { owner.shotState == ExtractionState.ENDED_OBSERVED &&
                owner.chartTraceIdentity == ownedShot }
            main {
                noBle(owner)
                val shot = app.history.entries.filter { it.id !in existingShots }.single()
                check(shot.curveId == "factory-v3-002" && shot.slot == 4)
            }
        } finally {
            main {
                service?.let { owner ->
                    if (ownsPreparation) owner.cancelBrewPreparation()
                    mock?.let { runtime ->
                        originalSettings?.let { settingsField.set(runtime, it) }
                        originalTemperature?.let { tempField.setInt(runtime, it) }
                    }
                    if (ownedShot != null && owner.chartTraceIdentity == ownedShot && ShotGate.active(owner.shotState))
                        owner.stopShot()
                }
                activity?.finish()
                check(app.fontSizePreferences.select(originalFont) != AppFontSizePreference.Selection.SAVE_FAILED)
                curveFixture.restore()
                check(presets.edit().also { edit -> original.forEach { (slot, value) ->
                    edit.putString(PresetSlots.key(slot), value)
                } }.commit())
                check(selection.edit().putString("selected", oldSelection).commit())
                check(options.edit().also { edit ->
                    if (oldOption == null) edit.remove(ShortcutStartPolicy.DIRECT_START)
                    else edit.putBoolean(ShortcutStartPolicy.DIRECT_START, oldOption)
                }.commit())
            }
        }
    }
}
