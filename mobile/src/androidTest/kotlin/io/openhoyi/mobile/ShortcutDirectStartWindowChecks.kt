package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.CompoundButton
import java.io.File

/** Actual Mock windows. The out-of-range recipe guarantees these flows cannot start a shot. */
internal class ShortcutDirectStartWindowChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) = BoundedInstrumentationMain.run("shortcut direct", 5000, action)
    private fun await(stage: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false; main { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < end) { "Shortcut direct timeout: $stage" }
            SystemClock.sleep(30)
        }
    }
    fun run() {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val store = app.customCurvesResult.getOrThrow()
        val documents = store.javaClass.getDeclaredField("documents").apply { isAccessible = true }
        val originalDocs = documents.get(store)
        val prefs = context.getSharedPreferences("shortcut_start", Context.MODE_PRIVATE)
        val oldConfigured = prefs.all["configured"] as? Boolean
        val oldDirect = prefs.all["direct_start"] as? Boolean
        val slots = context.getSharedPreferences("presets", Context.MODE_PRIVATE)
        val oldSlot = slots.getString(PresetSlots.key(5), null)
        val curves = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val oldSelected = curves.getString("selected", null)
        val history = app.history.entries
        val curveFile = File(context.filesDir, "custom_curves_v1.json")
        val originalBytes = curveFile.takeIf { it.exists() }?.readBytes()
        val document = CustomCurveDocument(CustomCurveDocument.newId(), "Shortcut direct QA", 105, 3600,
            CustomCurveDocument.ControlMode.PRESSURE,
            listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
        var activity: MainActivity? = null
        var mockRuntime: Any? = null
        var originalMockSettings: Any? = null
        val directory = File(context.getExternalFilesDir(null), "shortcut-direct-checks").apply { mkdirs() }
        fun capture(name: String) {
            SystemClock.sleep(400)
            val image = checkNotNull(test.uiAutomation.takeScreenshot())
            try { File(directory, "$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { image.recycle() }
        }
        try {
            main {
                @Suppress("UNCHECKED_CAST") val old = originalDocs as Map<String, CustomCurveDocument>
                documents.set(store, LinkedHashMap(old).apply { put(document.id, document) })
                check(slots.edit().putString(PresetSlots.key(5), document.id).commit())
                check(prefs.edit().remove("configured").remove("direct_start").commit())
            }
            val page = MainPageTestHost.launch(test, timeoutMs = 15000)
            val host = page.activity; activity = host
            await("service") { host.currentService?.running == true }
            val service = checkNotNull(host.currentService)
            val oldState = service.shotState
            main {
                val runtime = checkNotNull(MobileService::class.java.getDeclaredField("mock").apply { isAccessible = true }.get(service))
                mockRuntime = runtime
                val field = runtime.javaClass.getDeclaredField("settings").apply { isAccessible = true }
                val settings = field.get(runtime) as io.openhoyi.protocol.Settings
                originalMockSettings = settings
                field.set(runtime, settings.copy(flags = settings.flags or 0x04))
            }
            await("studio fixture") { service.snapshot.settings?.flags?.and(0x04) == 0x04 }

            fun noShot() {
                check(service.shotState == oldState && app.history.entries == history)
                check(MobileService::class.java.getDeclaredField("hub").apply { isAccessible = true }.get(service) == null)
            }
            fun dialog(): android.app.AlertDialog? = MainActivity::class.java.getDeclaredField("shortcutPrompt")
                .apply { isAccessible = true }.get(host) as? android.app.AlertDialog
            fun clickSlot() = main {
                val current = service.currentCorrectedBrewTemperature()
                check(current == null || kotlin.math.abs(current - 10500) > 100) { "Unsafe temperature fixture" }
                check(page.active("root:BREW").requireView().findViewWithTag<View>("product/home/direct-preset/5").performClick())
            }
            clickSlot()
            await("first prompt") { dialog()?.isShowing == true }
            capture("first-prompt")
            main { dialog()!!.cancel(); noShot() }
            check(!prefs.contains("configured") && !prefs.getBoolean("direct_start", false))
            clickSlot()
            await("second prompt") { dialog()?.isShowing == true }
            main {
                dialog()!!.findViewById<View>(android.R.id.content)
                    .findViewWithTag<CompoundButton>("product/home/shortcut/direct-start").isChecked = true
                // View-only action commits the future preference but never starts this click.
                dialog()!!.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).performClick()
            }
            await("view") { runCatching { page.active("extraction") }.isSuccess }
            check(prefs.getBoolean("configured", false) && prefs.getBoolean("direct_start", false)); noShot()
            main { host.select(TaskDestination.BREW) }
            clickSlot()
            await("cold direct routes only") { runCatching { page.active("extraction") }.isSuccess }
            noShot(); capture("temperature-gated")
            val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
            try {
                main { host.recreate() }
                val recreated = checkNotNull(monitor.waitForActivityWithTimeout(10000) as? MainActivity)
                activity = recreated
                await("recreated") { recreated.supportFragmentManager.findFragmentByTag("extraction")?.isVisible == true }
                noShot()
                main { recreated.openSettingsSection("shortcuts") }
                await("settings") { recreated.findViewById<View>(android.R.id.content)
                    .findViewWithTag<CompoundButton>("product/settings/shortcuts/direct-start")?.isShown == true }
                // Let the prior preheat toast expire before the settings evidence screenshot.
                SystemClock.sleep(3500)
                main {
                    val toggle = recreated.findViewById<View>(android.R.id.content)
                        .findViewWithTag<CompoundButton>("product/settings/shortcuts/direct-start")
                    val visible = android.graphics.Rect()
                    check(toggle.getGlobalVisibleRect(visible) && visible.height() >= toggle.height) {
                        "Shortcut settings toggle is outside the viewport"
                    }
                }
                capture("settings")
                var manualY = 0
                main {
                    val scroll = recreated.findViewById<android.widget.ScrollView>(R.id.m8_settings_scroll)
                    scroll.scrollBy(0, 200)
                    manualY = scroll.scrollY
                }
                SystemClock.sleep(750)
                main {
                    val scroll = recreated.findViewById<android.widget.ScrollView>(R.id.m8_settings_scroll)
                    check(kotlin.math.abs(scroll.scrollY - manualY) <= 2) { "Settings pulled back after manual scroll" }
                    recreated.openSettingsSection("appearance")
                    recreated.openSettingsSection("shortcuts")
                }
                await("last section wins") {
                    val toggle = recreated.findViewById<View>(android.R.id.content)
                        .findViewWithTag<CompoundButton>("product/settings/shortcuts/direct-start")
                    val visible = android.graphics.Rect()
                    toggle.getGlobalVisibleRect(visible) && visible.height() >= toggle.height
                }
                main {
                    val toggle = recreated.findViewById<View>(android.R.id.content)
                        .findViewWithTag<CompoundButton>("product/settings/shortcuts/direct-start")
                    check(toggle.isChecked); toggle.performClick()
                }
                check(!prefs.getBoolean("direct_start", true)); noShot()
                main { recreated.select(TaskDestination.BREW); recreated.openShortcut(5) }
                await("disabled navigates only") { recreated.supportFragmentManager.findFragmentByTag("extraction")?.isVisible == true }
                noShot()
            } finally { test.removeMonitor(monitor) }
        } finally {
            try { main {
                mockRuntime?.let { runtime -> runtime.javaClass.getDeclaredField("settings").apply { isAccessible = true }.set(runtime, originalMockSettings) }
                documents.set(store, originalDocs); activity?.finish()
            } }
            finally {
                check(prefs.edit().apply {
                    if (oldConfigured == null) remove("configured") else putBoolean("configured", oldConfigured)
                    if (oldDirect == null) remove("direct_start") else putBoolean("direct_start", oldDirect)
                }.commit())
                check(slots.edit().apply { if (oldSlot == null) remove(PresetSlots.key(5)) else putString(PresetSlots.key(5), oldSlot) }.commit())
                check(curves.edit().apply { if (oldSelected == null) remove("selected") else putString("selected", oldSelected) }.commit())
                check(app.history.entries == history)
                check(if (originalBytes == null) !curveFile.exists() else curveFile.readBytes().contentEquals(originalBytes))
            }
        }
    }
}
