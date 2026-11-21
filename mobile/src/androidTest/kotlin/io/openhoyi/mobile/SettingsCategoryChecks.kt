package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.EditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Representative real phone/tablet Settings views; never runs a device or update action. */
internal class SettingsCategoryChecks(private val test: Instrumentation) {
    private fun <T> main(block: () -> T): T = BoundedInstrumentationMain.run("Settings category proof", 4000, block)
    private fun await(label: String, ready: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 4500
        while (!main(ready)) {
            check(SystemClock.elapsedRealtime() < until) { "Expected Settings state: $label" }
            SystemClock.sleep(25)
        }
        SystemClock.sleep(180)
    }

    fun run(wide: Boolean, dark: Boolean) {
        val context = test.targetContext
        val app = context.applicationContext as MobileApplication
        val out = File(context.filesDir, "settings-categories").apply { check(mkdirs() || isDirectory) }
        val receipts = JSONObject()
        try {
            main {
                check(app.appearancePreferences.selectDark(dark) != AppAppearancePreference.Selection.SAVE_FAILED)
                check(app.languagePreferences.select(AppLanguage.CHINESE) != AppLanguagePreference.Selection.SAVE_FAILED)
                check(app.fontSizePreferences.select(AppFontSize.MEDIUM) != AppFontSizePreference.Selection.SAVE_FAILED)
            }
            val page = MainPageTestHost.launch(test, TaskDestination.SETTINGS, timeoutMs = 15_000)
            val activity = page.activity
            try {
                fun settings() = page.active("root:SETTINGS").requireView() as SettingsDocumentLayout
                fun button(category: SettingsCategory): Button =
                    requireNotNull(settings().findViewWithTag("settings/category/${category.key}"))
                fun heading(section: String): View =
                    requireNotNull(settings().findViewWithTag("settings/heading/$section"))
                fun rect(view: View) = Rect().also { check(view.getGlobalVisibleRect(it)) { "Not visible: ${view.tag}" } }
                fun capture(name: String) {
                    val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
                    try {
                        File(out, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                }
                fun select(category: SettingsCategory) {
                    main { check(button(category).performClick()) }
                    await(category.key) { button(category).isSelected && heading(category.sections.first()).getGlobalVisibleRect(Rect()) }
                }
                await("default Devices") { button(SettingsCategory.DEVICE).isSelected && heading("devices").getGlobalVisibleRect(Rect()) }
                val originalIndex = main {
                    val settings = settings()
                    check(settings.findViewWithTag<View>("settings/document/category-sidebar").isShown == wide)
                    check(settings.findViewWithTag<View>("settings/document/category-grid").isShown != wide)
                    SettingsCategory.entries.map { category ->
                        val b = button(category)
                        val r = rect(b)
                        val text = b.layout
                        val unit = activity.resources.displayMetrics.density
                        check(r.width() == b.width && r.height() == b.height && r.width() >= 48 * unit && r.height() >= 48 * unit)
                        check(text != null && (0 until text.lineCount).all { text.getEllipsisCount(it) == 0 })
                        r
                    }.also { rows ->
                        if (wide) check(rows.map { it.left }.distinct().size == 1 && rows.map { it.top }.distinct().size == 6)
                        else check(rows.map { it.left }.distinct().size == 3 && rows.map { it.top }.distinct().size == 2)
                    }
                }
                val savedText = main { settings().findViewsByClass(EditText::class.java).first() }
                val previous = main { savedText.text.toString().also { savedText.setText("94") } }
                val precision = context.getSharedPreferences("scale_tool", android.content.Context.MODE_PRIVATE)
                val originalPrecision = precision.getBoolean("one_decimal", false)
                for (category in SettingsCategory.entries) {
                    select(category)
                    main {
                        check(SettingsCategory.entries.count { button(it).isSelected } == 1)
                        check(SettingsCategory.entries.filter { it != category }.all { other ->
                            other.sections.none { heading(it).getGlobalVisibleRect(Rect()) }
                        })
                        check(SettingsCategory.entries.map { rect(button(it)) } == originalIndex) { "Index moved by changing category" }
                    }
                    capture(category.key)
                }
                main { check(savedText.text.toString() == "94") { "Category change lost unsent editor draft" }; savedText.setText(previous) }
                for (category in SettingsCategory.entries) for (section in category.sections) {
                    main { (page.active("root:SETTINGS") as AppSettingsFragment).openSection(section) }
                    await("exact $section heading") { button(category).isSelected && heading(section).getGlobalVisibleRect(Rect()) && heading(section).hasFocus() }
                }
                // Exact return route, not just a successful navigation to Settings.
                main { activity.select(TaskDestination.BEANS); activity.openSettingsSection("scale") }
                await("external scale link") { button(SettingsCategory.DEVICE).isSelected && heading("scale").getGlobalVisibleRect(Rect()) && heading("scale").hasFocus() }
                main { activity.onBackPressedDispatcher.onBackPressed() }
                await("return to caller") { activity.supportFragmentManager.primaryNavigationFragment is BeanInventoryFragment }
                main { activity.openSettingsSection("about") }
                await("updates anchor") { heading("about").getGlobalVisibleRect(Rect()) && heading("about").hasFocus() }
                main {
                    check(settings().findViewWithTag<View>("product/settings/app/update/check").isEnabled)
                    val details = settings().findViewWithTag<View>("settings/details/maintenance/content")
                    check(!details.isShown)
                    val action = settings().findViewWithTag<Button>("settings/details/maintenance/action")
                    check(action.performClick() && details.isShown)
                    check(action.performClick() && !details.isShown)
                    check(precision.getBoolean("one_decimal", false) == originalPrecision)
                }
                val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
                var restored: MainActivity? = null
                try {
                    main { activity.recreate() }
                    val recreated = test.waitForMonitorWithTimeout(monitor, 12_000) as? MainActivity
                        ?: error("Settings Activity recreation did not return")
                    restored = recreated
                    await("same category after recreation") {
                        val fragment = recreated.supportFragmentManager.primaryNavigationFragment as? AppSettingsFragment
                        val view = fragment?.view
                        view?.findViewWithTag<View>("settings/category/maintenance")?.isSelected == true &&
                            view.findViewWithTag<View>("settings/heading/about")?.getGlobalVisibleRect(Rect()) == true
                    }
                } finally {
                    test.removeMonitor(monitor)
                    restored?.let { main { it.finish() } }
                }
                receipts.put("wide", wide).put("dark", dark).put("sixCategories", true).put("exactRoutes", 11)
                    .put("categoryRailFixed", true).put("recreatedCategory", true).put("draftRetained", true).put("returnedToCaller", true)
                    .put("rects", JSONArray(originalIndex.map { JSONArray(listOf(it.left, it.top, it.right, it.bottom)) }))
                    .put("noBleOrDestructiveActions", true)
            } finally {
                main { if (!activity.isDestroyed) activity.finish() }
            }
        } finally {
            File(out, "receipt.json").writeText(receipts.toString(2) + "\n")
        }
    }

    private fun <T : View> View.findViewsByClass(type: Class<T>): List<T> {
        val found = mutableListOf<T>()
        fun walk(view: View) {
            if (type.isInstance(view)) found.add(type.cast(view)!!)
            if (view is android.view.ViewGroup)
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(this)
        return found
    }
}
