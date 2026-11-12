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
    fun run(darkTheme: Boolean? = null) {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldDark = appearance.all["dark"] as? Boolean
        val app = context.applicationContext as MobileApplication
        val store = app.customCurvesResult.getOrThrow()
        val documents = store.javaClass.getDeclaredField("documents").apply { isAccessible = true }
        val originalDocs = documents.get(store)
        val prefs = context.getSharedPreferences("shortcut_start", Context.MODE_PRIVATE)
        val oldDirect = prefs.all["direct_start"] as? Boolean
        val slots = context.getSharedPreferences("presets", Context.MODE_PRIVATE)
        val oldSlot = slots.getString(PresetSlots.key(5), null)
        val curves = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val oldSelected = curves.getString("selected", null)
        val history = app.history.entries
        val ledger = app.curveUsageResult.getOrThrow()
        val usesField = ledger.javaClass.getDeclaredField("uses").apply { isAccessible = true }
        val originalUses = usesField.get(ledger)
        val usageFile = File(context.filesDir, "curve_usage_v1.tsv")
        val usageBytes = usageFile.takeIf { it.exists() }?.readBytes()
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
                darkTheme?.let { check(appearance.edit().putBoolean("dark", it).commit()) }
                @Suppress("UNCHECKED_CAST") val old = originalDocs as Map<String, CustomCurveDocument>
                documents.set(store, LinkedHashMap(old).apply { put(document.id, document) })
                check(slots.edit().putString(PresetSlots.key(5), document.id).commit())
                check(prefs.edit().remove("direct_start").commit())
            }
            val page = MainPageTestHost.launch(test, timeoutMs = 15000)
            val host = page.activity; activity = host
            darkTheme?.let { expected -> main {
                check((host.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES) == expected)
            } }
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
            fun homeToggle(host: MainActivity) = host.findViewById<View>(android.R.id.content)
                .findViewWithTag<CompoundButton>("product/home/shortcuts/direct-start")
            fun visible(view: View): Boolean {
                val rect = android.graphics.Rect()
                return view.getGlobalVisibleRect(rect) && rect.height() >= view.height
            }
            fun clickSlot() = main {
                val current = service.currentCorrectedBrewTemperature()
                check(current == null || kotlin.math.abs(current - 10500) > 100) { "Unsafe temperature fixture" }
                check(page.active("root:BREW").requireView().findViewWithTag<View>("product/home/direct-preset/5").performClick())
            }
            main {
                check(!homeToggle(host).isChecked)
                homeToggle(host).requestRectangleOnScreen(android.graphics.Rect(0, 0, homeToggle(host).width, homeToggle(host).height), true)
            }
            await("home toggle visible") { visible(homeToggle(host)) }
            capture("home-off")
            clickSlot()
            await("off navigates without dialog") { runCatching { page.active("extraction") }.isSuccess }
            check(!prefs.getBoolean("direct_start", false)); noShot()
            main { host.select(TaskDestination.BREW) }
            main {
                check(!homeToggle(host).isChecked)
                homeToggle(host).performClick()
                homeToggle(host).requestRectangleOnScreen(android.graphics.Rect(0, 0, homeToggle(host).width, homeToggle(host).height), true)
                noShot()
            }
            check(prefs.getBoolean("direct_start", false))
            await("home toggle enabled") { homeToggle(host).isChecked && visible(homeToggle(host)) }
            capture("home-on")
            clickSlot()
            await("cold direct routes only") { runCatching { page.active("extraction") }.isSuccess }
            noShot(); capture("temperature-gated")
            // The previous-shot entry is the same explicit tap path, never a preheat request.
            main {
                val at = maxOf(System.currentTimeMillis(), (ledger.latest()?.startedAtMs ?: 0) + 1)
                ledger.record("last-shot-direct-window", document.id, at)
                ledger.attachFingerprint("last-shot-direct-window", CustomCurveFingerprint.of(document))
                host.select(TaskDestination.BREW)
                check(prefs.edit().putBoolean("direct_start", false).commit())
            }
            fun lastUse() = page.active("root:BREW").requireView()
                .findViewWithTag<View>("product/home/quick-launch")
            await("previous curve shown") {
                host.resolveLastCurve() is HomeLastCurve.Ready &&
                    page.active("root:BREW").requireView().findViewWithTag<android.widget.TextView>("product/home/quick-recipe").text.toString() == document.name
            }
            main { check(lastUse().performClick()) }
            await("previous off navigates only") { runCatching { page.active("extraction") }.isSuccess }
            main {
                noShot()
                check(curves.getString("selected", null) == document.id)
                host.select(TaskDestination.BREW)
                check(prefs.edit().putBoolean("direct_start", true).commit())
            }
            await("previous direct preference") { homeToggle(host).isChecked }
            main {
                check(lastUse().performClick())
            }
            await("previous cold direct routes only") { runCatching { page.active("extraction") }.isSuccess }
            main { noShot() }
            capture("previous-temperature-gated")
            // A stale history identity cannot navigate or create a permit for another shot.
            main {
                host.select(TaskDestination.BREW)
                val reference = checkNotNull(host.lastCurveReference())
                val ready = host.resolveLastCurve(reference) as HomeLastCurve.Ready
                ledger.record("last-shot-direct-window-new", document.id, checkNotNull(reference.latest).startedAtMs + 1)
                ledger.attachFingerprint("last-shot-direct-window-new", CustomCurveFingerprint.of(document))
                host.openLastCurve(ready, reference)
                check(host.supportFragmentManager.findFragmentByTag("extraction")?.isVisible != true)
                noShot()
                // Restore a gated extraction page for the existing recreation checks.
                host.openShortcut(5)
            }
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
                main { recreated.select(TaskDestination.BREW) }
                await("settings off reflected at Home") { !homeToggle(recreated).isChecked }
                main { recreated.openShortcut(5) }
                await("disabled navigates only") { recreated.supportFragmentManager.findFragmentByTag("extraction")?.isVisible == true }
                noShot()
            } finally { test.removeMonitor(monitor) }
        } finally {
            // Finish first: live switches can write their rendered choice back during restoration.
            main { activity?.finish() }
            await("fixture Activity destroyed before restoring preferences") { activity?.isDestroyed != false }
            val idle = java.util.concurrent.CountDownLatch(1)
            val queue = android.os.Looper.getMainLooper().queue
            val idleHandler = android.os.MessageQueue.IdleHandler { idle.countDown(); false }
            main { queue.addIdleHandler(idleHandler) }
            try {
                check(idle.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "Fixture main queue did not become idle" }
            } finally { main { queue.removeIdleHandler(idleHandler) } }
            main {
                mockRuntime?.let { runtime -> runtime.javaClass.getDeclaredField("settings").apply { isAccessible = true }.set(runtime, originalMockSettings) }
                documents.set(store, originalDocs)
                usesField.set(ledger, originalUses)
                if (usageBytes == null) usageFile.delete() else usageFile.writeBytes(usageBytes)
                if (darkTheme != null) check(appearance.edit().apply {
                    if (oldDark == null) remove("dark") else putBoolean("dark", oldDark)
                }.commit())
                check(prefs.edit().apply {
                    if (oldDirect == null) remove("direct_start") else putBoolean("direct_start", oldDirect)
                }.commit())
                check(slots.edit().apply { if (oldSlot == null) remove(PresetSlots.key(5)) else putString(PresetSlots.key(5), oldSlot) }.commit())
                check(curves.edit().apply { if (oldSelected == null) remove("selected") else putString("selected", oldSelected) }.commit())
            }
            // Cross another main-queue barrier and verify the exact original key/value survived.
            main { check(prefs.all["direct_start"] == oldDirect) { "Fixture changed the saved direct-start preference" } }
            check(app.history.entries == history)
            check(if (originalBytes == null) !curveFile.exists() else curveFile.readBytes().contentEquals(originalBytes))
        }
    }
}
