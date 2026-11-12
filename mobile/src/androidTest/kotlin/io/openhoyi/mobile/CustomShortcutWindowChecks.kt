package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import java.io.File

/** Mock-only navigation; temporary in-memory recipes never overwrite the user's stored documents. */
internal class CustomShortcutWindowChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) = BoundedInstrumentationMain.run("custom shortcut", 5000, action)
    private fun await(stage: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false; main { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < until) { "Shortcut timeout: $stage" }
            SystemClock.sleep(30)
        }
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun clickWindowText(text: String) {
        val until = SystemClock.elapsedRealtime() + 10000
        while (true) {
            val window = test.uiAutomation.rootInActiveWindow
            check(window == null || window.packageName?.toString() == test.targetContext.packageName) { "Unexpected foreground window" }
            val nodes = window?.findAccessibilityNodeInfosByText(text).orEmpty()
            val node = nodes.firstOrNull { it.text?.toString() == text && it.isEnabled }
            if (node != null) {
                var click: AccessibilityNodeInfo? = node
                while (click != null && !click.isClickable) click = click.parent
                if (click?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            }
            check(SystemClock.elapsedRealtime() < until) { "Missing window action: $text" }
            SystemClock.sleep(40)
        }
    }
    fun run() {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val store = app.customCurvesResult.getOrThrow()
        val curveFixture = CustomCurveWindowFixture(store)
        val curvePrefs = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val slotPrefs = context.getSharedPreferences("presets", Context.MODE_PRIVATE)
        val shortcutPrefs = context.getSharedPreferences("shortcut_start", Context.MODE_PRIVATE)
        val shortcutDirect = shortcutPrefs.all["direct_start"] as? Boolean
        val selected = curvePrefs.getString("selected", null)
        val stored = slotPrefs.getString(PresetSlots.key(5), null)
        val oldHistory = app.history.entries
        val file = File(context.filesDir, "custom_curves_v1.json")
        val originalBytes = if (file.exists()) file.readBytes() else null
        var activity: MainActivity? = null
        val document = CustomCurveDocument(CustomCurveDocument.newId(), "Shortcut QA", 92, 3600,
            CustomCurveDocument.ControlMode.PRESSURE,
            listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
        fun put(doc: CustomCurveDocument?) {
            curveFixture.replace(doc)
        }
        val directory = File(context.getExternalFilesDir(null), "custom-shortcut-checks").apply { mkdirs() }
        fun capture(name: String) {
            SystemClock.sleep(400)
            val bitmap = checkNotNull(test.uiAutomation.takeScreenshot())
            try { File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
        }
        try {
            check(shortcutPrefs.edit().putBoolean("direct_start", false).commit())
            main { put(document) }
            val page = MainPageTestHost.launch(test, timeoutMs = 15000)
            val host = page.activity; activity = host
            await("service") { host.currentService?.running == true }
            val service = requireNotNull(host.currentService)
            val oldShotState = service.shotState
            fun noShot() {
                check(service.shotState == oldShotState && app.history.entries == oldHistory)
                check(MobileService::class.java.getDeclaredField("hub").apply { isAccessible = true }.get(service) == null)
            }
            page.select(TaskDestination.CURVES)
            main {
                val root = page.active("root:CURVES").requireView()
                views(root).filterIsInstance<EditText>().single().setText(document.name)
                val list = views(root).filterIsInstance<ListView>().single()
                check(list.adapter.count == 1)
                check(list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0)))
            }
            await("direct slot action") { page.active("root:CURVES").requireView().findViewWithTag<View>("curve-assign-slot")?.isShown == true }
            main { check(page.active("root:CURVES").requireView().findViewWithTag<View>("curve-assign-slot").performClick()) }
            clickWindowText(host.getString(R.string.home_slot, "5"))
            await("assignment") { slotPrefs.getString(PresetSlots.key(5), null) == document.id }
            check(curvePrefs.getString("selected", null) == selected)
            noShot()
            page.select(TaskDestination.BREW)
            fun tile() = page.active("root:BREW").requireView().findViewWithTag<View>("product/home/direct-preset/5")
            await("home label") { views(tile().parent as View).filterIsInstance<TextView>().any { it.text.toString() == document.name } }
            capture("assigned-home")
            main { check(tile().performClick()) }
            await("navigation") { runCatching { page.active("extraction") }.isSuccess }
            check(curvePrefs.getString("selected", null) == document.id)
            main {
                val fragment = page.active("extraction")
                check(fragment.javaClass.getDeclaredField("presetSlot").apply { isAccessible = true }.getInt(fragment) == 7)
                noShot()
            }
            capture("custom-extraction")
            val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
            try {
                main { host.recreate() }
                val recreated = checkNotNull(monitor.waitForActivityWithTimeout(10000) as? MainActivity)
                activity = recreated
                await("recreation") { recreated.supportFragmentManager.findFragmentByTag("extraction")?.isVisible == true }
                check(curvePrefs.getString("selected", null) == document.id)
                main {
                    val fragment = checkNotNull(recreated.supportFragmentManager.findFragmentByTag("extraction"))
                    check(fragment.javaClass.getDeclaredField("presetSlot").apply { isAccessible = true }.getInt(fragment) == 7)
                    put(document.copy(name = "Shortcut edited", temperatureC = 93))
                    recreated.select(TaskDestination.BREW)
                }
                await("edited label") { views(recreated.findViewById(android.R.id.content)).filterIsInstance<TextView>().any { it.text.toString() == "Shortcut edited" } }
                capture("edited-home")
                main { put(null); recreated.select(TaskDestination.CURVES); recreated.select(TaskDestination.BREW) }
                await("missing label") { views(recreated.findViewById(android.R.id.content)).filterIsInstance<TextView>().any { it.text.toString() == recreated.getString(R.string.curve_slot_invalid) } }
                main {
                    check(recreated.findViewById<View>(android.R.id.content).findViewWithTag<View>("product/home/direct-preset/5").performClick())
                    check(curvePrefs.getString("selected", null) == document.id)
                    check(recreated.supportFragmentManager.primaryNavigationFragment is HomeFragment)
                    noShot()
                }
                capture("missing-shortcut")
            } finally { test.removeMonitor(monitor) }
        } finally {
            try { main { curveFixture.restore(); activity?.finish() } }
            finally {
                check(shortcutPrefs.edit().apply {
                    if (shortcutDirect == null) remove("direct_start") else putBoolean("direct_start", shortcutDirect)
                }.commit())
                check(curvePrefs.edit().apply { if (selected == null) remove("selected") else putString("selected", selected) }.commit())
                check(slotPrefs.edit().apply { if (stored == null) remove(PresetSlots.key(5)) else putString(PresetSlots.key(5), stored) }.commit())
                check(curvePrefs.getString("selected", null) == selected)
                check(slotPrefs.getString(PresetSlots.key(5), null) == stored)
                check(if (originalBytes == null) !file.exists() else file.readBytes().contentEquals(originalBytes))
                check(app.history.entries == oldHistory)
            }
        }
    }
}
