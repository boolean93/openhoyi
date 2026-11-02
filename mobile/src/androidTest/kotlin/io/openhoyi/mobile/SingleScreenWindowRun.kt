package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.view.View
import android.widget.ListView
import android.widget.TextView
import io.openhoyi.bean.Bean
import io.openhoyi.bean.BeanBatch
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Mock-only additive named fixtures; navigation only, no deductions, BLE or record mutation. */
internal class SingleScreenWindowRun(private val test: Instrumentation) {
    private fun main(action: () -> Unit) {
        var error: Throwable? = null
        test.runOnMainSync { try { action() } catch (e: Throwable) { error = e } }
        error?.let { throw it }
    }
    private fun awaitDraw(activity: Activity) {
        val drawn = CountDownLatch(1)
        main {
            val decor = activity.window.decorView
            val observer = decor.viewTreeObserver
            val listener = object : android.view.ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    drawn.countDown()
                    decor.post { if (observer.isAlive) observer.removeOnDrawListener(this) }
                }
            }
            observer.addOnDrawListener(listener)
            decor.invalidate()
        }
        check(drawn.await(5, TimeUnit.SECONDS)) { "Changed page did not draw" }
        // OnDraw precedes RenderThread's buffer submission. Allow the compositor
        // to receive it; queue-idle alone can return an identical old screenshot.
        Thread.sleep(250)
        test.waitForIdleSync()
    }
    fun run() {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val inventory = app.beanInventoryResult.getOrThrow()
        val journal = app.journalResult.getOrThrow()
        val batch = BeanBatch("single-screen-ui-batch", Bean("single-screen-ui-bean", "单屏测试豆"), 250000)
        val shotIds = listOf("single-screen-ui-shot-v2-1", "single-screen-ui-shot-v2-2")
        main {
            if (inventory.batches().none { it.batch.id == batch.id }) inventory.addBatch("single-screen-ui-inbound", batch)
            shotIds.forEachIndexed { index, id ->
                if (journal.find(id) == null) journal.observe(BrewJournal.Observation(id, "capture-1",
                    1700000000000L + index * 60000, 1700000000000L + index * 60000 + if (index == 0) 30000 else 32000,
                    if (index == 0) 30000 else 32000, "ENDED", null, if (index == 0) 3600 else 3520))
            }
        }
        shotIds.forEachIndexed { index, id ->
            val samples = (0..if (index == 0) 30 else 32).map { second ->
                if (index == 0) ShotPoint(second * 1000L,
                    if (second < 4) second * 20 else 90 - (second - 4), 30, second * 30, 9300, second * 120, 120, true)
                else ShotPoint(second * 1000L,
                    if (second < 5) second * 17 else 85 - (second - 5), 28, second * 28,
                    9200 + minOf(second, 5) * 10, second * 110, 110, true)
            }
            val existing = app.samples.load(id)
            if (existing.isEmpty()) app.samples.save(id, samples)
            else check(existing == samples) { "Existing named samples differ; not overwritten" }
        }
        // Named additive fixtures are installed once, before every immutable baseline.
        // They never replace original inventory, journal or legacy rows.
        SingleScreenListStateChecks.seed(app)
        val sampleIds = shotIds + SingleScreenListStateChecks.sampleFixtureIds
        val sampleBaseline = sampleIds.associateWith { app.samples.load(it) }
        val legacyId = "single-screen-legacy-shot"
        app.legacyHistory.import(java.io.ByteArrayInputStream("""{"version":1,"items":[{"id":"$legacyId","createdAt":1700000000000,"durationSec":30,"chartSlot":6,"profileName":"Single screen fixture","points":{"t":[0,1,2],"press":[0,2,4],"flow":[0,1,2],"wFlow":[0,1,2],"wTrend":[0,1,3]}}]}""".toByteArray()))
        val legacyBefore = app.legacyHistory.list()
        // Resolve only the pre-recorded immutable fixture facts; never re-read storage on the UI thread.
        fun expectedLegacyFixture(activity:Activity):LegacyShot? = if(activity is LegacyHistoryDetailActivity)
            legacyBefore.single {it.id==activity.intent.getStringExtra("legacyId")} else null
        // Legacy imports remain read-only and separate from executable profiles.
        // Seed only an absent store; never replace existing imported records.
        val legacyCurvesBefore = requireNotNull(app.legacyCurves.load())
        check(legacyCurvesBefore.curves.isNotEmpty())
        val curvesPrefs = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val oldCurvesStorage = curvesPrefs.all.toMap()
        val historyBefore = app.history.entries
        val usageBefore = app.curves.items.associate { it.id to app.curveUsageResult.getOrThrow().stats(it.id) }
        val oldLanguage = app.languagePreferences.current
        val languagePrefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldLanguageStorage = languagePrefs.all.toMap()
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldAppearance = appearance.all.toMap()
        val inventoryBefore = inventory.events()
        val journalBefore = journal.exportJson()
        val preparationBefore = app.beanPreparationResult.getOrThrow().current()
        val customBefore = app.customCurvesResult.getOrThrow().list()
        val curve = app.curves.items.first { it.controlProfile != null || it.factoryCurve != null }
        val document = CustomCurveDocument.fromLibraryItem(curve,
            "draft-00e9bc40-f1d4-40ef-a59a-7f10b0d58870").encode()
        fun page(type: Class<out Activity>): Intent {
            val destination = when (type) {
                HomeActivity::class.java -> TaskDestination.BREW
                CurveActivity::class.java -> TaskDestination.CURVES
                HistoryActivity::class.java -> TaskDestination.HISTORY
                BeanInventoryActivity::class.java -> TaskDestination.BEANS
                ScaleActivity::class.java -> TaskDestination.SCALE
                AppSettingsActivity::class.java -> TaskDestination.SETTINGS
                else -> null
            }
            val root = destination != null || type == ExtractionActivity::class.java
            return Intent(context, if (root) MainActivity::class.java else type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (root) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0)
                .apply {
                    if (root) putExtra("fixturePage", type.simpleName)
                    destination?.let { putExtra(MainActivity.EXTRA_INITIAL_DESTINATION, it.name) }
                    if (type == ExtractionActivity::class.java) putExtra(MainActivity.EXTRA_OPEN_EXTRACTION, true)
                }
        }
        val pages = listOf(
            page(HomeActivity::class.java), page(ExtractionActivity::class.java), page(CurveActivity::class.java),
            page(HistoryActivity::class.java), page(HistoryDetailActivity::class.java).putExtra("shotId", shotIds[0]),
            page(BeanInventoryActivity::class.java), page(BeanEntryActivity::class.java),
            page(BeanBatchActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, batch.id),
            page(BeanLedgerActivity::class.java), page(BeanPreparationActivity::class.java),
            page(BrewReviewActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, shotIds[0]),
            page(BrewComparisonActivity::class.java).putExtra(BrewComparisonActivity.FIRST_SHOT_ID, shotIds[0])
                .putExtra(BrewComparisonActivity.SECOND_SHOT_ID, shotIds[1]),
            page(CurveEditorActivity::class.java), page(CurveShareActivity::class.java).putExtra(CurveShareActivity.CURVE_ID, curve.id),
            page(CurveImportActivity::class.java), page(CurveImportPreviewActivity::class.java).putExtra(CurveImportPreviewActivity.DOCUMENT, document),
            page(ScaleActivity::class.java), page(AppSettingsActivity::class.java),
            page(LegacyCurveActivity::class.java), page(LegacyHistoryActivity::class.java), page(LegacyHistoryDetailActivity::class.java).putExtra("legacyId", legacyId),
        )
        check(pages.size == 21)
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "single-screen-window-checks").apply { mkdirs() }
        val failures = mutableListOf<String>()
        var checked = 0
        var chineseFormCaptures = 0
        var listRestoreFixtures = 0
        try {
        for (language in AppLanguage.entries) for (dark in listOf(false, true)) {
            main {
                check(app.languagePreferences.select(language) != AppLanguagePreference.Selection.SAVE_FAILED)
                check(appearance.edit().putBoolean("dark", dark).commit())
            }
        for (intent in pages) {
            var activity: Activity? = null
            val pageName = intent.getStringExtra("fixturePage")
                ?: requireNotNull(intent.component).shortClassName.substringAfterLast('.')
            val name = "${language.tag}-${if (dark) "dark" else "light"}-$pageName"
            try {
                activity = test.startActivitySync(intent)
                test.waitForIdleSync()
                if (activity is MainActivity) MainPageTestHost(test, activity).active(when (pageName) {
                    "ExtractionActivity" -> "extraction"
                    "CurveActivity" -> "root:CURVES"
                    "HistoryActivity" -> "root:HISTORY"
                    "BeanInventoryActivity" -> "root:BEANS"
                    "ScaleActivity" -> "root:SCALE"
                    "AppSettingsActivity" -> "root:SETTINGS"
                    else -> "root:BREW"
                })
                val deadline = SystemClock.elapsedRealtime() + 10000
                while (true) {
                    var ready = false
                    main { ready = activity.hasWindowFocus() && activity.findViewById<ViewGroup>(android.R.id.content)
                        .let { it.height > 0 && !it.isLayoutRequested } }
                    if (ready) break
                    check(SystemClock.elapsedRealtime() < deadline) { "$name layout timeout" }
                    Thread.sleep(25)
                }
                // Wait for actual comparison sources, not an assumed background-thread duration.
                if (activity is BrewComparisonActivity) {
                    val sampleDeadline = SystemClock.elapsedRealtime() + 10000
                    var detail = ""
                    while (true) {
                        var loaded = false
                        main {
                            val chart = activity.window.decorView.findViewWithTag<TwoCupChartView>("two-cup-chart")
                            loaded = chart != null && chart.firstPoints.size >= 2 && chart.secondPoints.size >= 2 &&
                                !activity.findViewById<ViewGroup>(android.R.id.content).isLayoutRequested && !chart.isLayoutRequested
                            detail = chart?.let { "first=${it.firstPoints.size} second=${it.secondPoints.size} message=${it.comparisonMessage}" } ?: "chart missing"
                        }
                        if (loaded) break
                        check(SystemClock.elapsedRealtime() < sampleDeadline) { "$name comparison samples timeout: $detail" }
                        Thread.sleep(25)
                    }
                } else if (activity is HistoryDetailActivity) {
                    val sampleDeadline = SystemClock.elapsedRealtime() + 10000
                    var detail = ""
                    while (true) {
                        var loaded = false
                        main {
                            fun descendants(view: View): List<View> = listOf(view) +
                                if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                            val content = activity.findViewById<ViewGroup>(android.R.id.content)
                            val views = descendants(content)
                            val chart = views.filterIsInstance<ShotChartView>().singleOrNull()
                            loaded = chart != null && chart.points.size >= 2 && !content.isLayoutRequested && !chart.isLayoutRequested
                            detail = "points=${chart?.points?.size} status=" + views.filterIsInstance<TextView>()
                                .filter { it.isShown }.joinToString(" | ") { it.text.toString() }
                        }
                        if (loaded) break
                        check(SystemClock.elapsedRealtime() < sampleDeadline) { "$name historical samples timeout: $detail" }
                        Thread.sleep(25)
                    }
                } else Thread.sleep(300)
                test.waitForIdleSync()
                main { SingleScreenPageChecks.verify(activity, expectedLegacyShot = expectedLegacyFixture(activity)) }
                if (language == AppLanguage.CHINESE && activity is BrewComparisonActivity) {
                    val comparison = activity
                    TwoCupComparisonPageChecks.run(test, comparison) { metric ->
                        awaitDraw(comparison)
                        val evidence = File(directory, "comparison-metrics/$name/${metric.name.lowercase(java.util.Locale.ROOT)}")
                            .apply { check(mkdirs() || isDirectory) }
                        main {
                            val chart = comparison.window.decorView.findViewWithTag<TwoCupChartView>("two-cup-chart")
                            check(chart.metric == metric && chart.firstPoints.size >= 2 && chart.secondPoints.size >= 2)
                            TaskTypographyEvidence.capture(comparison, evidence)
                            File(evidence, "sources.txt").writeText(
                                "fixture=distinct-v2 metric=$metric firstSamples=${chart.firstPoints.size} secondSamples=${chart.secondPoints.size} " +
                                    "firstEndMs=${chart.firstPoints.last().elapsedMs} secondEndMs=${chart.secondPoints.last().elapsedMs}\n")
                        }
                        val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
                        try {
                            File(evidence, "window.png").outputStream().use {
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                            }
                        } finally { bitmap.recycle() }
                    }
                    main { SingleScreenPageChecks.verify(activity, expectedLegacyShot = expectedLegacyFixture(activity)) }
                }
                // Verify the actual populated states too, not just empty previews.
                if (pageName == "CurveActivity" || pageName == "HistoryActivity") {
                    val stateDirectory = File(directory, "states").apply { mkdirs() }
                    test.uiAutomation.takeScreenshot()?.let { bitmap ->
                        try { File(stateDirectory, "$name-initial.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                        finally { bitmap.recycle() }
                    }
                    main {
                        fun children(view: View): List<View> = listOf(view) +
                            if (view is ViewGroup) (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
                        val views = children(activity.findViewById(android.R.id.content))
                        if (pageName == "CurveActivity") {
                            val list = views.filterIsInstance<ListView>().single()
                            check(list.count > 0)
                            check(list.performItemClick(list.adapter.getView(0, null, list), 0, list.adapter.getItemId(0)))
                        } else {
                            check(views.filterIsInstance<TextView>().single { it.text.toString() == activity.getString(R.string.journal_tab) }.performClick())
                        }
                    }
                    awaitDraw(activity)
                    main {
                        SingleScreenPageChecks.verify(activity, expectedLegacyShot = expectedLegacyFixture(activity))
                        if (pageName == "CurveActivity") {
                            val fragment = (activity as MainActivity).supportFragmentManager.primaryNavigationFragment as CurveFragment
                            val plan = CurveFragment::class.java.getDeclaredField("recipePlan").apply { isAccessible = true }.get(fragment) as CurveRecipePlanView
                            val selected = CurveFragment::class.java.getDeclaredField("selected").apply { isAccessible = true }.get(fragment) as CurveLibraryItem
                            val stages = CurveStageParameters.preview(selected).stages
                            check(plan.isShown && stages.isNotEmpty() && (1..stages.size).all {
                                plan.findViewWithTag<View>("product/curve-library/stage/$it")?.isShown == true
                            }) { "Selected curve has no visible stage-card preview" }
                        } else {
                            val fragment = (activity as MainActivity).supportFragmentManager.primaryNavigationFragment as HistoryFragment
                            val list = HistoryFragment::class.java.getDeclaredField("historyList").apply { isAccessible = true }.get(fragment) as ListView
                            check(list.isShown && list.count >= 2) { "Journal fixture not displayed" }
                        }
                    }
                    val changed = requireNotNull(test.uiAutomation.takeScreenshot())
                    try {
                        val initialFile = File(stateDirectory, "$name-initial.png")
                        val initial = requireNotNull(android.graphics.BitmapFactory.decodeFile(initialFile.absolutePath))
                        try { check(!changed.sameAs(initial)) { "State screenshot still shows the initial frame" } }
                        finally { initial.recycle() }
                        File(stateDirectory, "$name-populated.png").outputStream().use { changed.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { changed.recycle() }
                }
                if (language == AppLanguage.CHINESE) {
                    // Pair the populated fixture window with native measurements before
                    // form/IME exercises mutate its transient draft or scroll position.
                    awaitDraw(activity)
                    val baseline = File(directory, "visual-baselines/$name").apply { check(mkdirs() || isDirectory) }
                    main { TaskTypographyEvidence.capture(activity, baseline) }
                    val screenshot = requireNotNull(test.uiAutomation.takeScreenshot())
                    try {
                        File(baseline, "window.png").outputStream().use {
                            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    } finally { screenshot.recycle() }
                }
                if (language == AppLanguage.CHINESE) chineseFormCaptures +=
                    ChineseFormWindowChecks.run(test, activity, directory, name) { awaitDraw(activity) }
                if (language == AppLanguage.CHINESE && pageName == "HomeActivity") {
                    val home = activity as MainActivity
                    val boundDeadline = SystemClock.elapsedRealtime() + 10000
                    while (true) {
                        var bound = false
                        main { bound = home.currentService != null }
                        if (bound) break
                        check(SystemClock.elapsedRealtime() < boundDeadline) { "$name Mock service not bound for warnings" }
                        Thread.sleep(25)
                    }
                    val safetyDirectory = File(directory, "chinese-safety-states").apply { mkdirs() }
                    var warningIndex = 0
                    LanguageDetailChecks(test).warnings(home, name) {
                        awaitDraw(home)
                        main { SingleScreenPageChecks.verify(home, expectedLegacyShot = expectedLegacyFixture(home)) }
                        val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
                        try {
                            File(safetyDirectory, "$name-warning-${warningIndex++}.png").outputStream().use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                        } finally { bitmap.recycle() }
                    }
                    check(warningIndex == 3) { "$name incomplete full warning states" }
                    awaitDraw(home) // Do not label the warning's old frame as the ordinary home page.
                }
                checked++
                test.sendStatus(0, Bundle().apply { putString("stream", "SINGLE_SCREEN_PAGE_OK $name\n") })
            } catch (error: Throwable) {
                failures += "$name: ${error.stackTraceToString()}"
                test.sendStatus(0, Bundle().apply { putString("stream", "SINGLE_SCREEN_PAGE_FAILED $name ${error.message}\n") })
            } finally {
                val bitmap = test.uiAutomation.takeScreenshot()
                if (bitmap != null) try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    finally { bitmap.recycle() }
                activity?.let { main { it.finish() } }
            }
        }
        // Do not synchronously start the same NEW_TASK root while its ordinary
        // instance is foreground: Android can reuse it without creating a new
        // Activity, leaving Instrumentation.startActivitySync waiting forever.
        if (language == AppLanguage.CHINESE) {
            val name = "${language.tag}-${if (dark) "dark" else "light"}-ScaleActivity-dose-capture"
            var capturePage: Activity? = null
            try {
                test.sendStatus(0, Bundle().apply { putString("stream", "SINGLE_SCREEN_STATE_START $name\n") })
                capturePage = test.startActivitySync(Intent(context, ScaleActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    .putExtra(ScaleActivity.EXTRA_CAPTURE_BEAN_DOSE, true))
                test.waitForIdleSync()
                awaitDraw(capturePage)
                main {
                    SingleScreenPageChecks.verify(capturePage, expectedLegacyShot = expectedLegacyFixture(capturePage))
                    val capture = ScaleActivity::class.java.getDeclaredField("capture")
                        .apply { isAccessible = true }.get(capturePage) as android.widget.Button
                    check(capture.isShown && !capture.isEnabled) { "Mock must not accept real bean weight" }
                }
                val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
                try {
                    val extra = File(directory, "chinese-extra-states").apply { mkdirs() }
                    File(extra, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
                test.sendStatus(0, Bundle().apply { putString("stream", "SINGLE_SCREEN_STATE_OK $name\n") })
            } catch (error: Throwable) {
                failures += "$name: ${error.stackTraceToString()}"
                test.sendStatus(0, Bundle().apply { putString("stream", "SINGLE_SCREEN_STATE_FAILED $name ${error.message}\n") })
            } finally { capturePage?.let { main { it.finish() } }; test.waitForIdleSync() }
        }
        }
        // Separate post-matrix checks do not relabel ordinary screenshots or alter
        // the 336-page matrix. All navigation remains in the isolated Mock package.
        main {
            check(app.languagePreferences.select(AppLanguage.CHINESE) != AppLanguagePreference.Selection.SAVE_FAILED)
            check(appearance.edit().putBoolean("dark", false).commit())
        }
        listRestoreFixtures = SingleScreenListStateChecks(test).run(directory)
        } finally {
            main {
                check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED)
                restoreMockPreferences(languagePrefs, oldLanguageStorage)
                restoreMockPreferences(appearance, oldAppearance)
                restoreMockPreferences(curvesPrefs, oldCurvesStorage)
                check(curvesPrefs.all == oldCurvesStorage)
            }
        }
        main {
            check(sampleIds.associateWith { app.samples.load(it) } == sampleBaseline)
            check(app.legacyHistory.list() == legacyBefore)
            check(app.legacyCurves.load() == legacyCurvesBefore)
            check(inventory.events() == inventoryBefore && journal.exportJson() == journalBefore)
            check(app.beanPreparationResult.getOrThrow().current() == preparationBefore)
            check(app.customCurvesResult.getOrThrow().list() == customBefore)
            check(app.history.entries == historyBefore)
            check(app.curves.items.associate { it.id to app.curveUsageResult.getOrThrow().stats(it.id) } == usageBefore)
        }
        File(directory, "result.txt").writeText("checked=$checked pages=21 fixtures=336 legacyDetailFixture=true chineseFormCaptures=$chineseFormCaptures listRestorePages=7 listRestoreFixtures=$listRestoreFixtures syntheticCandidates=true noScan=true noConnect=true\n" + failures.joinToString("\n\n"))
        check(checked == 336 && failures.isEmpty()) { "Single-screen geometry failed: checked=$checked/336\n${failures.joinToString("\n")}" }
        check(chineseFormCaptures >= 44) { "Chinese form groups/long editor evidence incomplete: $chineseFormCaptures" }
        check(listRestoreFixtures == 14) { "List restore evidence incomplete: $listRestoreFixtures/14" }
        test.sendStatus(0, Bundle().apply { putString("stream", "SINGLE_SCREEN_WINDOW_GEOMETRY_PASSED pages=21 fixtures=336 legacyDetailFixture=true noBle=true\n") })
    }
}
