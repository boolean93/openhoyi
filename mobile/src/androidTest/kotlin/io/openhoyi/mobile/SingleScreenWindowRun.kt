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
        val shotIds = listOf("single-screen-ui-shot-1", "single-screen-ui-shot-2")
        main {
            if (inventory.batches().none { it.batch.id == batch.id }) inventory.addBatch("single-screen-ui-inbound", batch)
            shotIds.forEachIndexed { index, id ->
                if (journal.find(id) == null) journal.observe(BrewJournal.Observation(id, "capture-1",
                    1700000000000L + index * 60000, 1700000030000L + index * 60000, 30000,
                    "ENDED", null, 3600))
            }
        }
        val samples = (0..30).map { second ->
            ShotPoint(second * 1000L, if (second < 4) second * 20 else 90 - (second - 4),
                25, second * 30, 9300, second * 120, 120, true)
        }
        shotIds.forEach { id ->
            val existing = app.samples.load(id)
            if (existing.isEmpty()) app.samples.save(id, samples)
            else check(existing == samples) { "Existing named samples differ; not overwritten" }
        }
        val sampleBaseline = shotIds.associateWith { app.samples.load(it) }
        val legacyId = "single-screen-legacy-shot"
        app.legacyHistory.import(java.io.ByteArrayInputStream("""{"version":1,"items":[{"id":"$legacyId","createdAt":1700000000000,"durationSec":30,"chartSlot":6,"profileName":"Single screen fixture","points":{"t":[0,1,2],"press":[0,2,4],"flow":[0,1,2],"wFlow":[0,1,2],"wTrend":[0,1,3]}}]}""".toByteArray()))
        val legacyBefore = app.legacyHistory.list()
        // Legacy imports remain read-only and separate from executable profiles.
        // Seed only an absent store; never replace existing imported records.
        if (app.legacyCurves.load() == null) app.legacyCurves.import(java.io.ByteArrayInputStream(
            """{"format":"openhoyi-legacy-curves-v1","factoryVersion":3,"categoryConfig":{"labels":{"test":"测试分类"}},"items":[{"name":"单屏旧曲线测试","category":"test","factory":false,"temp":93,"flow":70,"weight":360,"seg":2,"press1":9,"flow1":30,"press2":6,"flow2":40,"singleScreenFixture":true}]}""".toByteArray()))
        val legacyCurvesBefore = requireNotNull(app.legacyCurves.load())
        check(legacyCurvesBefore.curves.isNotEmpty())
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
        fun page(type: Class<out Activity>) = Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
            page(ScaleActivity::class.java), page(ScaleConnectionActivity::class.java),
            page(LegacyCurveActivity::class.java), page(LegacyHistoryActivity::class.java), page(LegacyHistoryDetailActivity::class.java).putExtra("legacyId", legacyId),
        )
        check(pages.size == 21)
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "single-screen-window-checks").apply { mkdirs() }
        val failures = mutableListOf<String>()
        var checked = 0
        var chineseFormCaptures = 0
        try {
        for (language in AppLanguage.entries) for (dark in listOf(false, true)) {
            main {
                check(app.languagePreferences.select(language) != AppLanguagePreference.Selection.SAVE_FAILED)
                check(appearance.edit().putBoolean("dark", dark).commit())
            }
        for (intent in pages) {
            var activity: Activity? = null
            val pageName = requireNotNull(intent.component).shortClassName.substringAfterLast('.')
            val name = "${language.tag}-${if (dark) "dark" else "light"}-$pageName"
            try {
                activity = test.startActivitySync(intent)
                test.waitForIdleSync()
                val deadline = SystemClock.elapsedRealtime() + 10000
                while (true) {
                    var ready = false
                    main { ready = activity.hasWindowFocus() && activity.findViewById<ViewGroup>(android.R.id.content)
                        .let { it.height > 0 && !it.isLayoutRequested } }
                    if (ready) break
                    check(SystemClock.elapsedRealtime() < deadline) { "$name layout timeout" }
                    Thread.sleep(25)
                }
                // Allow asynchronous sample readers to attach the actual chart.
                Thread.sleep(300)
                test.waitForIdleSync()
                main { SingleScreenPageChecks.verify(activity) }
                // Verify the actual populated states too, not just empty previews.
                if (activity is CurveActivity || activity is HistoryActivity) {
                    val stateDirectory = File(directory, "states").apply { mkdirs() }
                    test.uiAutomation.takeScreenshot()?.let { bitmap ->
                        try { File(stateDirectory, "$name-initial.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                        finally { bitmap.recycle() }
                    }
                    main {
                        fun children(view: View): List<View> = listOf(view) +
                            if (view is ViewGroup) (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
                        val views = children(activity.findViewById(android.R.id.content))
                        if (activity is CurveActivity) {
                            val list = views.filterIsInstance<ListView>().single()
                            check(list.count > 0)
                            check(list.performItemClick(list.adapter.getView(0, null, list), 0, list.adapter.getItemId(0)))
                        } else {
                            check(views.filterIsInstance<TextView>().single { it.text.toString() == activity.getString(R.string.journal_tab) }.performClick())
                        }
                    }
                    awaitDraw(activity)
                    main {
                        SingleScreenPageChecks.verify(activity)
                        if (activity is CurveActivity) {
                            val chart = CurveActivity::class.java.getDeclaredField("stageChart").apply { isAccessible = true }.get(activity) as CurveStageView
                            check(chart.isShown && chart.targets.isNotEmpty()) { "Selected curve has no preview" }
                        } else {
                            val list = HistoryActivity::class.java.getDeclaredField("historyList").apply { isAccessible = true }.get(activity) as ListView
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
                if (language == AppLanguage.CHINESE) chineseFormCaptures +=
                    ChineseFormWindowChecks.run(test, activity, directory, name) { awaitDraw(activity) }
                if (language == AppLanguage.CHINESE && activity is HomeActivity) {
                    val home = activity
                    val serviceField = HomeActivity::class.java.getDeclaredField("service").apply { isAccessible = true }
                    val boundDeadline = SystemClock.elapsedRealtime() + 10000
                    while (true) {
                        var bound = false
                        main { bound = serviceField.get(home) != null }
                        if (bound) break
                        check(SystemClock.elapsedRealtime() < boundDeadline) { "$name Mock service not bound for warnings" }
                        Thread.sleep(25)
                    }
                    val safetyDirectory = File(directory, "chinese-safety-states").apply { mkdirs() }
                    var warningIndex = 0
                    LanguageDetailChecks(test).warnings(home, name) {
                        awaitDraw(home)
                        main { SingleScreenPageChecks.verify(home) }
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
                capturePage = test.startActivitySync(page(ScaleActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    .putExtra(ScaleActivity.EXTRA_CAPTURE_BEAN_DOSE, true))
                test.waitForIdleSync()
                awaitDraw(capturePage)
                main {
                    SingleScreenPageChecks.verify(capturePage)
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
        } finally {
            main {
                check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED)
                restoreMockPreferences(languagePrefs, oldLanguageStorage)
                restoreMockPreferences(appearance, oldAppearance)
            }
        }
        main {
            check(shotIds.associateWith { app.samples.load(it) } == sampleBaseline)
            check(app.legacyHistory.list() == legacyBefore)
            check(app.legacyCurves.load() == legacyCurvesBefore)
            check(inventory.events() == inventoryBefore && journal.exportJson() == journalBefore)
            check(app.beanPreparationResult.getOrThrow().current() == preparationBefore)
            check(app.customCurvesResult.getOrThrow().list() == customBefore)
        }
        File(directory, "result.txt").writeText("checked=$checked pages=21 fixtures=336 legacyDetailFixture=true chineseFormCaptures=$chineseFormCaptures\n" + failures.joinToString("\n\n"))
        check(checked == 336 && failures.isEmpty()) { "Single-screen geometry failed: checked=$checked/336\n${failures.joinToString("\n")}" }
        check(chineseFormCaptures >= 44) { "Chinese form groups/long editor evidence incomplete: $chineseFormCaptures" }
        test.sendStatus(0, Bundle().apply { putString("stream", "SINGLE_SCREEN_WINDOW_GEOMETRY_PASSED pages=21 fixtures=336 legacyDetailFixture=true noBle=true\n") })
    }
}
