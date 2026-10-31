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
    private fun awaitMain(diagnostic: () -> String = { "unspecified condition" }, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            if (SystemClock.elapsedRealtime() >= deadline) {
                var detail = ""
                onMain { detail = diagnostic() }
                error("Advanced page condition timed out: $detail")
            }
            Thread.sleep(25)
        }
    }
    private fun homeOwner(activity: MainActivity): MobileService? = activity.currentService
    private fun scaleOwner(activity: Activity): MobileService? = ScaleOwnerActivity::class.java.getDeclaredField("owner")
        .apply { isAccessible = true }.get(activity) as? MobileService
    private fun root(activity: Activity): ViewGroup =
        activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    fun run(profile: String): Result {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock") { "Only the isolated Mock package is allowed" }
        check(profile in listOf("compact", "landscape", "wideFont"))
        val expectedWidth = if (profile == "compact") 360 else 1138
        val expectedScale = if (profile == "wideFont") 1.3f else 1f
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "advanced-page-screenshots/$profile")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create screenshot directory" }
        val app = context.applicationContext as MobileApplication
        val oldLanguage = app.languagePreferences.current
        val languagePrefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldLanguageStorage = languagePrefs.all.toMap()
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldAppearance = appearance.all.toMap()
        var home = MainPageTestHost.launch(test).activity
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
            CustomCurveDocument.ControlMode.PRESSURE, listOf(
                CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 250),
                CustomCurveDocument.Stage(70, 150), CustomCurveDocument.Stage(50, 50),
            )).encode()
        fun page(type: Class<out Activity>) = Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pages = listOf(
            page(BeanInventoryActivity::class.java),
            page(BeanBatchActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, batch.id),
            page(BeanEntryActivity::class.java),
            page(BeanLedgerActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, batch.id),
            page(BeanPreparationActivity::class.java),
            page(ScaleActivity::class.java), page(AppSettingsActivity::class.java), page(ScaleSettingsActivity::class.java),
            page(ScaleActivity::class.java).putExtra(ScaleActivity.EXTRA_CAPTURE_BEAN_DOSE, true),
            page(BrewReviewActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, observations[0].id),
            page(BrewComparisonActivity::class.java).putExtra(BrewComparisonActivity.FIRST_SHOT_ID, observations[0].id)
                .putExtra(BrewComparisonActivity.SECOND_SHOT_ID, observations[1].id),
            page(CurveEditorActivity::class.java),
            page(CurveShareActivity::class.java).putExtra(CurveShareActivity.CURVE_ID, shareSource.id),
            page(CurveImportActivity::class.java),
            page(CurveImportPreviewActivity::class.java).putExtra(CurveImportPreviewActivity.DOCUMENT, preview),
        )
        check(pages.size == 15 && AppLanguage.entries.size == 8)
        var currentPage: Activity? = null
        var completed = 0
        var screenshots = 0
        var failure: Throwable? = null
        val homeLifecycle = test.addMonitor(MainActivity::class.java.name, null, false)
        var originalHomeTask = -1
        onMain { originalHomeTask = home.taskId }
        fun followHomeRecreation() {
            if (!home.isDestroyed) return
            val replacement = homeLifecycle.lastActivity as? MainActivity ?: return
            if (replacement === home || replacement.isDestroyed || replacement.taskId != originalHomeTask) return
            home = replacement
            test.sendStatus(0, Bundle().apply {
                putString("stream", "ADVANCED_HOME_RECREATED task=$originalHomeTask; still requiring original service and state\n")
            })
        }
        fun homeReturned(): Boolean {
            followHomeRecreation()
            return !home.isDestroyed && home.taskId == originalHomeTask && home.hasWindowFocus() &&
                homeOwner(home) === service && home.resources.configuration.locales[0] == app.languagePreferences.current.locale &&
                HoyiUi.dark(home) == appearance.getBoolean("dark", false)
        }
        fun rebuildHome() {
            val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
            try {
                onMain { home.recreate() }
                home = test.waitForMonitorWithTimeout(monitor, 10000) as? MainActivity ?: error("Advanced Home recreation missing")
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
                    val name = requireNotNull(intent.component).shortClassName.substringAfterLast('.') +
                        if (intent.getBooleanExtra(ScaleActivity.EXTRA_CAPTURE_BEAN_DOSE, false)) "-Capture" else ""
                    val fixture = "$profile ${language.tag} dark=$dark page=$name"
                    test.sendStatus(0, Bundle().apply { putString("stream", "ADVANCED_PAGE_START $fixture\n") })
                    val rootDestination = when {
                        intent.component?.className == BeanInventoryActivity::class.java.name -> TaskDestination.BEANS
                        intent.component?.className == AppSettingsActivity::class.java.name -> TaskDestination.SETTINGS
                        intent.component?.className == ScaleActivity::class.java.name &&
                            !intent.getBooleanExtra(ScaleActivity.EXTRA_CAPTURE_BEAN_DOSE, false) -> TaskDestination.SCALE
                        else -> null
                    }
                    val activity: Activity = if (rootDestination == null) {
                        test.startActivitySync(intent).also { currentPage = it }
                    } else {
                        MainPageTestHost(test, home).select(rootDestination)
                        home
                    }
                    test.waitForIdleSync()
                    awaitMain {
                        activity.hasWindowFocus() && root(activity).height > 0 && !root(activity).isLayoutRequested &&
                            (activity !is ScaleOwnerActivity || scaleOwner(activity) === service)
                    }
                    onMain {
                        val config = activity.resources.configuration
                        check(config.screenWidthDp == expectedWidth && kotlin.math.abs(config.fontScale - expectedScale) < .001f &&
                            (profile == "compact" || config.densityDpi == 360)) {
                            "$fixture wrong window configuration width=${config.screenWidthDp} height=${config.screenHeightDp} " +
                                "font=${config.fontScale} density=${config.densityDpi}"
                        }
                        check(config.locales[0] == language.locale && HoyiUi.dark(activity) == dark) { "$fixture locale/theme mismatch" }
                        check(activity.window.decorView.layoutDirection == if (language.rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
                        if (intent.getBooleanExtra(ScaleActivity.EXTRA_CAPTURE_BEAN_DOSE, false)) {
                            check(activity is ScaleActivity && service!!.beanDoseCaptureAt(SystemClock.elapsedRealtime()) == null)
                            val labels = descendants(root(activity)).filterIsInstance<TextView>()
                            check(labels.single { it.isClickable && it.text.toString() == activity.getString(R.string.scale_dose_capture) }.let {
                                it.isShown && !it.isEnabled
                            }) { "$fixture Mock must not capture real bean weight" }
                            check(labels.any { it.isShown && it.text.toString() == activity.getString(R.string.scale_dose_help) })
                        }
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
                    if (activity is CurveImportPreviewActivity || activity is CurveShareActivity) {
                        onMain {
                            val views = descendants(root(activity))
                            val tableTag = if (activity is CurveImportPreviewActivity) "import-stage-table" else "share-stage-table"
                            val geometry = File(directory, "${language.tag}-${if (dark) "dark" else "light"}-$name-geometry.txt")
                            val table = views.singleOrNull { it.tag == tableTag } as? ViewGroup
                            val chart = views.filterIsInstance<CurveStageView>().singleOrNull()
                            if (table == null || chart == null) {
                                geometry.writeText("pressurePreview=false tablePresent=${table != null} chartPresent=${chart != null}\n")
                                check(activity !is CurveImportPreviewActivity) { "$fixture four-stage pressure preview missing" }
                                return@onMain
                            }
                            val bounds = Rect()
                            val visible = table.getGlobalVisibleRect(bounds)
                            val complete = table.isShown && visible && bounds.width() == table.width && bounds.height() == table.height
                            val config = activity.resources.configuration
                            geometry.writeText(
                                "screenDp=${config.screenWidthDp}x${config.screenHeightDp} font=${config.fontScale} " +
                                    "stages=${table.childCount - 1} tableShown=${table.isShown} tableComplete=$complete " +
                                    "table=${table.measuredWidth}x${table.measuredHeight} chart=${chart.width}x${chart.height}\n"
                            )
                            if (activity is CurveImportPreviewActivity) {
                                check(table.childCount == 5) { "$fixture must exercise all four stages" }
                                check(table.isShown) { "$fixture four-stage parameter table must never be hidden" }
                            }
                        }
                    }
                    if (activity is CurveImportPreviewActivity) {
                        verifyImportPreviewTable(activity, fixture, directory,
                            "${language.tag}-${if (dark) "dark" else "light"}-$name",
                            captureWindow = language == AppLanguage.CHINESE || language == AppLanguage.ARABIC)
                    }
                    assertState()
                    completed++
                    if (rootDestination == null) onMain { activity.finish() }
                    else MainPageTestHost(test, home).select(TaskDestination.BREW)
                    awaitMain(diagnostic = {
                        "$fixture return: childDestroyed=${activity.isDestroyed} childFinishing=${activity.isFinishing} " +
                            "homeDestroyed=${home.isDestroyed} homeFinishing=${home.isFinishing} " +
                            "homeFocus=${home.hasWindowFocus()} homeAttached=${home.window.decorView.isAttachedToWindow} " +
                            "sameService=${homeOwner(home) === service} serviceRunning=${service?.running} " +
                            "childTask=${activity.taskId} homeTask=${home.taskId}"
                    }) { (rootDestination != null || activity.isDestroyed) && homeReturned() }
                    currentPage = null
                    assertState()
                }
            }
            check(completed == 240 && screenshots == 60) { "Incomplete advanced matrix: pages=$completed screenshots=$screenshots" }
        } catch (error: Throwable) { failure = error }
        finally {
            fun cleanup(action: () -> Unit) {
                try { action() } catch (error: Throwable) { if (failure == null) failure = error else failure!!.addSuppressed(error) }
            }
            cleanup {
                currentPage?.let { closing -> onMain { closing.finish() }; awaitMain { closing.isDestroyed && homeReturned() } }
            }
            cleanup { onMain { check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED) } }
            cleanup { onMain { restoreMockPreferences(languagePrefs, oldLanguageStorage); restoreMockPreferences(appearance, oldAppearance) } }
            cleanup { rebuildHome(); assertState() }
            test.removeMonitor(homeLifecycle)
        }
        failure?.let { throw it }
        return Result(completed, screenshots, directory)
    }

    /** Read-only reachability: scroll only the preview pane, never invoke save/copy. */
    private fun verifyImportPreviewTable(activity: CurveImportPreviewActivity, fixture: String,
        directory: File, name: String, captureWindow: Boolean) {
        lateinit var table: ViewGroup
        lateinit var chart: CurveStageView
        lateinit var scroll: ScrollView
        lateinit var decisions: View
        var firstScreenComplete = false
        var tableFitsViewport = false
        var decisionBounds = Rect()
        val evidence = File(directory, "import-preview-reachability").apply { mkdirs() }
        fun fullBounds(view: View): Rect {
            val position = IntArray(2)
            view.getLocationOnScreen(position)
            return Rect(position[0], position[1], position[0] + view.width, position[1] + view.height)
        }
        fun complete(view: View): Boolean {
            val visible = Rect()
            return view.isShown && view.getGlobalVisibleRect(visible) &&
                visible.width() == view.width && visible.height() == view.height
        }
        fun geometry(): String {
            val visible = Rect()
            table.getGlobalVisibleRect(visible)
            return "$fixture tableShown=${table.isShown} rows=${table.childCount} " +
                "tableComplete=${complete(table)} full=${fullBounds(table)} visible=$visible " +
                "table=${table.width}x${table.height} chart=${chart.width}x${chart.height} " +
                "scrollY=${scroll.scrollY} viewport=${scroll.width}x${scroll.height} " +
                "contentHeight=${scroll.getChildAt(0).height} decisions=${fullBounds(decisions)}\n"
        }
        fun capture(phase: String) {
            onMain { File(evidence, "$name-$phase.txt").writeText(geometry()) }
            if (!captureWindow) return
            val drawn = java.util.concurrent.CountDownLatch(1)
            onMain { activity.window.decorView.postOnAnimation {
                activity.window.decorView.postOnAnimation { drawn.countDown() }
            } }
            check(drawn.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "$fixture preview capture did not draw" }
            Thread.sleep(100) // Allow RenderThread/compositor submission after the layout callbacks.
            test.waitForIdleSync()
            val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
            try {
                File(evidence, "$name-$phase.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { bitmap.recycle() }
            onMain { File(evidence, "$name-$phase.txt").appendText("afterCapture actualWindow=true\n" + geometry()) }
        }
        onMain {
            val views = descendants(root(activity))
            table = views.single { it.tag == "import-stage-table" } as ViewGroup
            chart = views.filterIsInstance<CurveStageView>().single()
            scroll = views.single { it.tag == "import-preview-scroll" } as ScrollView
            decisions = views.single { it.tag == "import-preview-actions" }
            check(table.isShown && table.childCount == 5) { "$fixture must keep the header and all four stages present" }
            check(chart.height >= HoyiUi.dp(activity, 160)) { "$fixture chart fell below its unchanged 160dp minimum" }
            scroll.scrollTo(0, 0)
            decisionBounds = fullBounds(decisions)
        }
        test.waitForIdleSync()
        capture("before-scroll")
        onMain {
            firstScreenComplete = complete(table)
            tableFitsViewport = table.height <= scroll.height - scroll.paddingTop - scroll.paddingBottom
            val config = activity.resources.configuration
            if (config.screenWidthDp >= 1000 && config.screenHeightDp >= 600 && config.fontScale <= 1.1f) {
                check(firstScreenComplete && complete(chart)) { "$fixture standard user window must show the whole table and chart on the first screen" }
            }
            if (!firstScreenComplete) {
                check(scroll.getChildAt(0).height > scroll.height - scroll.paddingTop - scroll.paddingBottom) {
                    "$fixture incomplete table without an actual local scroll range"
                }
            }
        }
        // Visibility of the last row alone is insufficient: independently reach every
        // measured row with exact full-width/full-height bounds and intact text.
        for (index in 0 until 5) {
            onMain {
                val row = table.getChildAt(index)
                row.requestRectangleOnScreen(Rect(0, 0, row.width, row.height), true)
            }
            test.waitForIdleSync()
            onMain {
                val row = table.getChildAt(index)
                check(complete(row)) { "$fixture table row $index cannot be fully reached: ${fullBounds(row)}" }
                descendants(row).filterIsInstance<TextView>().forEach {
                    LanguageUiLayoutChecks.textFits(it, "$fixture reachable table row=$index")
                    textWidthFits(it, "$fixture reachable table row=$index")
                }
                check(fullBounds(decisions) == decisionBounds) { "$fixture preview scroll moved the fixed decision region" }
            }
        }
        onMain {
            if (tableFitsViewport) {
                table.requestRectangleOnScreen(Rect(0, 0, table.width, table.height), true)
            }
        }
        test.waitForIdleSync()
        capture("after-scroll")
        onMain {
            check(table.isShown && table.childCount == 5)
            if (tableFitsViewport) check(complete(table)) { "$fixture whole table fits but is not fully reachable" }
            check(fullBounds(decisions) == decisionBounds) { "$fixture preview scroll moved the fixed decision region" }
            File(directory, "$name-geometry.txt").appendText(
                "reachabilityChecked=true firstScreenComplete=$firstScreenComplete allRowsReachable=true " +
                    "tableFitsViewport=$tableFitsViewport tableCompleteAfterScroll=${complete(table)} " +
                    "fixedDecisions=true chartMinimumDp=160\n")
            scroll.scrollTo(0, 0)
        }
        test.waitForIdleSync()
    }

    private fun checkPage(activity: Activity, fixture: String) {
        fun settledGeometry(label: String) = awaitMain {
            val ready = !root(activity).isLayoutRequested && descendants(root(activity)).filterIsInstance<TextView>()
                .filter { it.isShown && !it.text.isNullOrEmpty() }.all { it.isLaidOut && it.layout != null && !it.isLayoutRequested }
            // Polling telemetry may replace text between two main-thread callbacks.
            // Validate the same settled frame we observed; never skip a clipping assertion.
            if (ready) checkGeometry(activity, label)
            ready
        }
        settledGeometry(fixture)
        var scrolls = emptyList<ScrollView>()
        var controls = emptyList<View>()
        onMain {
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
                settledGeometry("$fixture scroll=$position")
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
                check(control.height >= HoyiUi.dp(activity, 48)) { "$fixture control below 48dp: ${if (control is TextView) control.text else control.javaClass.simpleName} height=${control.height}" }
            }
        }
        onMain { scrolls.forEach { it.scrollTo(0, 0) } }
        test.waitForIdleSync()
        settledGeometry("$fixture restoredTop")
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
