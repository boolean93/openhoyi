package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import io.openhoyi.bean.Bean
import io.openhoyi.bean.BeanBatch
import java.io.File
import java.time.LocalDate

/** Real isolated Mock windows. Scrolls only: no click, clipboard, SAF or machine command. */
internal class AdvancedPageChecks(private val test: Instrumentation) {
    data class Result(val fixtures: Int, val screenshots: Int, val directory: File)
    private val context get() = test.targetContext
    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < deadline) { "Advanced page condition timed out" }
            Thread.sleep(25)
        }
    }
    private fun homeOwner(activity: Activity): MobileService? = HomeActivity::class.java.getDeclaredField("service")
        .apply { isAccessible = true }.get(activity) as? MobileService
    private fun scaleOwner(activity: Activity): MobileService? = ScaleOwnerActivity::class.java.getDeclaredField("owner")
        .apply { isAccessible = true }.get(activity) as? MobileService
    private fun root(activity: Activity): ViewGroup =
        activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    fun run(profile: String): Result {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock") { "Only the isolated Mock package is allowed" }
        check(profile == "compact" || profile == "wideFont")
        val expectedWidth = if (profile == "compact") 360 else 1280
        val expectedScale = if (profile == "compact") 1f else 1.3f
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "advanced-page-screenshots/$profile")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create screenshot directory" }
        val app = context.applicationContext as MobileApplication
        val oldLanguage = app.languagePreferences.current
        val languagePrefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldLanguageStorage = languagePrefs.all.toMap()
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldAppearance = appearance.all.toMap()
        var home = test.startActivitySync(Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
        test.waitForIdleSync()
        awaitMain { homeOwner(home)?.running == true && home.window.decorView.isLaidOut }
        val inventory = app.beanInventoryResult.getOrThrow()
        val journal = app.journalResult.getOrThrow()
        val preparation = app.beanPreparationResult.getOrThrow()
        val custom = app.customCurvesResult.getOrThrow()
        // A nonempty preparation may represent an unresolved deduction. Never reset it for a test.
        check(preparation.current() == null) { "Advanced page fixtures require an isolated Mock preparation store" }
        val batch = BeanBatch("ui-batch", Bean("ui-bean", "UI coffee"), 250000,
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3))
        val observations = listOf(
            BrewJournal.Observation("ui-shot-1", "capture-1", 1700000000000L, 1700000030000L, 30000L, "ENDED", null, 3600),
            BrewJournal.Observation("ui-shot-2", "capture-1", 1700000100000L, null, null, "UNKNOWN", null, null),
        )
        val notes = listOf(
            BrewJournal.Notes(batch.bean.id, batch.bean.name, 18000, "Fine", "As expected", "Sweet", "Same grind"),
            BrewJournal.Notes(batch.bean.id, batch.bean.name, 18500, "Finer", "Uncertain", "Not recorded", "Review next cup"),
        )
        onMain {
            inventory.addBatch("ui-batch-add", batch)
            observations.forEachIndexed { index, observation ->
                val existing = journal.find(observation.id)
                if (existing == null) { journal.observe(observation); journal.edit(observation.id, notes[index]) }
                else check(existing == BrewJournal.Entry(observation, notes[index])) { "Existing journal fixture differs; not overwritten" }
            }
        }
        val balances = inventory.batches()
        val events = inventory.events()
        val journalState = journal.exportJson()
        val preparationState = preparation.current()
        val curves = custom.list()
        val documents = listOf("bean_inventory_v1.bin", "bean_preparation_v1.json", "brew_journal_v1.json", "custom_curves_v1.json")
        fun readDocument(name: String): List<Byte>? = File(context.filesDir, name).let { if (it.exists()) it.readBytes().toList() else null }
        val disk = documents.associateWith(::readDocument)
        val preserved = listOf("devices", "curves", "presets", "shot_safety", "machine_write_safety", "shot_history", "brew_feedback", "scale_tool")
            .associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        var service: MobileService? = null
        var message: SnapshotMessage? = null
        onMain { service = homeOwner(home); message = service!!.snapshot.message; negativeFixture(home) }
        val shareSource = app.curves.items.firstOrNull { it.controlProfile != null && runCatching { CustomCurveDocument.fromLibraryItem(it) }.isSuccess }
            ?: error("No valid captured curve for the share fixture")
        val preview = CustomCurveDocument("draft-00000000-0000-0000-0000-000000000099", "UI recipe", 93, 3600,
            CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400))).encode()
        fun page(type: Class<out Activity>) = Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pages = listOf(
            page(BeanInventoryActivity::class.java),
            page(BeanBatchActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, batch.id),
            page(BeanEntryActivity::class.java),
            page(BeanLedgerActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, batch.id),
            page(BeanPreparationActivity::class.java),
            page(ScaleActivity::class.java), page(ScaleConnectionActivity::class.java), page(ScaleSettingsActivity::class.java),
            page(BrewReviewActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, observations[0].id),
            page(BrewComparisonActivity::class.java).putExtra(BrewComparisonActivity.FIRST_SHOT_ID, observations[0].id)
                .putExtra(BrewComparisonActivity.SECOND_SHOT_ID, observations[1].id),
            page(CurveEditorActivity::class.java),
            page(CurveShareActivity::class.java).putExtra(CurveShareActivity.CURVE_ID, shareSource.id),
            page(CurveImportActivity::class.java),
            page(CurveImportPreviewActivity::class.java).putExtra(CurveImportPreviewActivity.DOCUMENT, preview),
        )
        check(pages.size == 14 && AppLanguage.entries.size == 8)
        var currentPage: Activity? = null
        var completed = 0
        var screenshots = 0
        var failure: Throwable? = null
        fun rebuildHome() {
            val monitor = test.addMonitor(HomeActivity::class.java.name, null, false)
            try {
                onMain { home.recreate() }
                home = test.waitForMonitorWithTimeout(monitor, 10000) as? HomeActivity ?: error("Advanced Home recreation missing")
                test.waitForIdleSync()
                awaitMain { homeOwner(home) === service && home.window.decorView.isLaidOut }
            } finally { test.removeMonitor(monitor) }
        }
        fun assertState() = onMain {
            check(service!!.running && service!!.snapshot.message === message) { "Navigation replaced service or machine event" }
            check(inventory.batches() == balances && inventory.events() == events) { "Navigation modified inventory" }
            check(journal.exportJson() == journalState) { "Navigation modified journal" }
            check(preparation.current() == preparationState) { "Navigation modified preparation" }
            check(custom.list() == curves) { "Navigation modified custom curves" }
            disk.forEach { (name, bytes) -> check(readDocument(name) == bytes) { "Navigation modified $name on disk" } }
            preserved.forEach { (name, values) -> check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) { "Navigation modified $name preferences" } }
        }
        try {
            for (language in AppLanguage.entries) for (dark in listOf(false, true)) {
                onMain {
                    check(app.languagePreferences.select(language) != AppLanguagePreference.Selection.SAVE_FAILED)
                    check(appearance.edit().putBoolean("dark", dark).commit())
                }
                rebuildHome()
                for (intent in pages) {
                    val name = requireNotNull(intent.component).shortClassName.substringAfterLast('.')
                    val fixture = "$profile ${language.tag} dark=$dark page=$name"
                    test.sendStatus(0, Bundle().apply { putString("stream", "ADVANCED_PAGE_START $fixture\n") })
                    val activity = test.startActivitySync(intent).also { currentPage = it }
                    test.waitForIdleSync()
                    awaitMain {
                        activity.hasWindowFocus() && root(activity).height > 0 && !root(activity).isLayoutRequested &&
                            (activity !is ScaleOwnerActivity || scaleOwner(activity) === service)
                    }
                    onMain {
                        val config = activity.resources.configuration
                        check(config.screenWidthDp == expectedWidth && kotlin.math.abs(config.fontScale - expectedScale) < .001f) {
                            "$fixture wrong window configuration width=${config.screenWidthDp} font=${config.fontScale}"
                        }
                        check(config.locales[0] == language.locale && HoyiUi.dark(activity) == dark) { "$fixture locale/theme mismatch" }
                        check(activity.window.decorView.layoutDirection == if (language.rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
                    }
                    checkPage(activity, fixture)
                    if (language == AppLanguage.CHINESE || language == AppLanguage.ARABIC) {
                        val bitmap = requireNotNull(test.uiAutomation.takeScreenshot()) { "$fixture screenshot missing" }
                        try {
                            File(directory, "${language.tag}-${if (dark) "dark" else "light"}-$name.png").outputStream().use {
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "$fixture screenshot encoding failed" }
                            }
                        } finally { bitmap.recycle() }
                        screenshots++
                    }
                    assertState()
                    completed++
                    onMain { activity.finish() }
                    awaitMain { activity.isDestroyed && home.hasWindowFocus() && homeOwner(home) === service }
                    currentPage = null
                    assertState()
                }
            }
            check(completed == 224 && screenshots == 56) { "Incomplete advanced matrix: pages=$completed screenshots=$screenshots" }
        } catch (error: Throwable) { failure = error }
        finally {
            fun cleanup(action: () -> Unit) {
                try { action() } catch (error: Throwable) { if (failure == null) failure = error else failure!!.addSuppressed(error) }
            }
            cleanup {
                currentPage?.let { closing -> onMain { closing.finish() }; awaitMain { closing.isDestroyed && home.hasWindowFocus() } }
            }
            cleanup { onMain { check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED) } }
            cleanup { onMain { restoreMockPreferences(languagePrefs, oldLanguageStorage); restoreMockPreferences(appearance, oldAppearance) } }
            cleanup { rebuildHome(); assertState() }
        }
        failure?.let { throw it }
        return Result(completed, screenshots, directory)
    }

    private fun checkPage(activity: Activity, fixture: String) {
        awaitMain {
            !root(activity).isLayoutRequested && descendants(root(activity)).filterIsInstance<TextView>()
                .filter { it.isShown && !it.text.isNullOrEmpty() }.all { it.isLaidOut && it.layout != null && !it.isLayoutRequested }
        }
        var scrolls = emptyList<ScrollView>()
        var controls = emptyList<View>()
        onMain {
            checkGeometry(activity, fixture)
            val views = descendants(root(activity))
            scrolls = views.filterIsInstance<ScrollView>().filter { it.isShown }
            controls = views.filter { it.isShown && it.isClickable && it !is ScrollView }
            check(views.filterIsInstance<TextView>().count { it.isShown && it !is EditText && !it.text.isNullOrEmpty() } >= 3) {
                "$fixture no populated page inspected"
            }
        }
        for (scroll in scrolls) {
            var maximum = 0
            var step = 0
            onMain {
                maximum = (scroll.getChildAt(0).height - (scroll.height - scroll.paddingTop - scroll.paddingBottom)).coerceAtLeast(0)
                step = (scroll.height / 2).coerceAtLeast(1)
            }
            var position = 0
            while (true) {
                onMain { scroll.scrollTo(0, position) }
                test.waitForIdleSync()
                onMain { checkGeometry(activity, "$fixture scroll=$position") }
                if (position == maximum) break
                position = (position + step).coerceAtMost(maximum)
            }
            onMain { check(scroll.scrollY == maximum) { "$fixture unreachable scroll end" } }
        }
        for (control in controls) {
            onMain { control.requestRectangleOnScreen(Rect(0, 0, control.width, control.height), true) }
            test.waitForIdleSync()
            onMain {
                val rect = Rect()
                check(control.getGlobalVisibleRect(rect) && rect.width() == control.width && rect.height() == control.height) {
                    "$fixture clipped control: ${if (control is TextView) control.text else control.javaClass.simpleName} visible=$rect size=${control.width}x${control.height}"
                }
                check(control.height >= HoyiUi.dp(activity, 48)) { "$fixture control below 48dp" }
            }
        }
        onMain { scrolls.forEach { it.scrollTo(0, 0) } }
        test.waitForIdleSync()
        onMain { checkGeometry(activity, "$fixture restoredTop") }
    }

    /** No navigation-bar shape assumption: beanPage has none, scale pages have one. */
    private fun checkGeometry(activity: Activity, fixture: String) {
        val root = root(activity)
        check(root.width > 0 && root.height > 0 && root.isShown)
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child.visibility != View.VISIBLE) continue
            check(child.width > 0 && child.height > 0 && child.left >= root.paddingLeft && child.right <= root.width - root.paddingRight &&
                child.top >= root.paddingTop && child.bottom <= root.height - root.paddingBottom) { "$fixture root content outside usable bounds" }
            if (index > 0) check(child.top >= root.getChildAt(index - 1).bottom) { "$fixture root content overlaps" }
        }
        descendants(root).filterIsInstance<TextView>().filter { it.isShown && it !is EditText && !it.text.isNullOrEmpty() }.forEach {
            LanguageUiLayoutChecks.textFits(it, fixture)
            textWidthFits(it, fixture)
        }
    }
    private fun textWidthFits(view: TextView, fixture: String) {
        val available = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        val layout = requireNotNull(view.layout)
        for (line in 0 until layout.lineCount) check(layout.getLineRight(line) - layout.getLineLeft(line) <= available + 1) {
            "$fixture text exceeds actual width: ${view.text}"
        }
    }
    private fun negativeFixture(activity: Activity) {
        val overflow = TextView(activity).apply { text = "STOP STOP STOP STOP STOP STOP"; textSize = 18f; setSingleLine(true) }
        overflow.measure(View.MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, 40), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        overflow.layout(0, 0, overflow.measuredWidth, overflow.measuredHeight)
        check(runCatching { textWidthFits(overflow, "intentional overflow") }.exceptionOrNull() is IllegalStateException) {
            "Advanced checker accepted an intentionally clipped single-line label"
        }
    }
}
