package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import io.openhoyi.bean.Bean
import io.openhoyi.bean.BeanBatch
import java.io.File

/** Mock-only additive named fixtures; no clicks, deductions, BLE, clearing or unknown-record mutation. */
internal class SingleScreenWindowRun(private val test: Instrumentation) {
    private fun main(action: () -> Unit) {
        var error: Throwable? = null
        test.runOnMainSync { try { action() } catch (e: Throwable) { error = e } }
        error?.let { throw it }
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
        val legacyId = "single-screen-legacy-shot"
        app.legacyHistory.import(java.io.ByteArrayInputStream("""{"version":1,"items":[{"id":"$legacyId","createdAt":1700000000000,"durationSec":30,"chartSlot":6,"profileName":"Single screen fixture","points":{"t":[0,1,2],"press":[0,2,4],"flow":[0,1,2],"wFlow":[0,1,2],"wTrend":[0,1,3]}}]}""".toByteArray()))
        val legacyBefore = app.legacyHistory.list()
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
        }
        } finally {
            main {
                check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED)
                restoreMockPreferences(languagePrefs, oldLanguageStorage)
                restoreMockPreferences(appearance, oldAppearance)
            }
        }
        main {
            check(app.legacyHistory.list() == legacyBefore)
            check(inventory.events() == inventoryBefore && journal.exportJson() == journalBefore)
            check(app.beanPreparationResult.getOrThrow().current() == preparationBefore)
            check(app.customCurvesResult.getOrThrow().list() == customBefore)
        }
        File(directory, "result.txt").writeText("checked=$checked pages=21 fixtures=336 legacyDetailFixture=true\n" + failures.joinToString("\n\n"))
        check(checked == 336 && failures.isEmpty()) { "Single-screen geometry failed: checked=$checked/336\n${failures.joinToString("\n")}" }
        test.sendStatus(0, Bundle().apply { putString("stream", "SINGLE_SCREEN_WINDOW_GEOMETRY_PASSED pages=21 fixtures=336 legacyDetailFixture=true noBle=true\n") })
    }
}
