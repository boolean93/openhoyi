package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale

/** Real Home views, local layout probes only. Never clicks start, confirm, tare or inventory actions. */
internal class HomeWorkbenchChecks(private val test: Instrumentation) {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    fun run(expectedFontScale: Float) {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val preferences = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val before = preferences.all.toMap()
        val ledger = app.curveUsageResult.getOrThrow()
        val recentFixtures = app.curves.items.filter(app.curves::canStart).take(3)
        check(recentFixtures.size == 3)
        main { recentFixtures.forEachIndexed { index, item ->
            ledger.record("home-recent-fixture-$index", item.id, 1700001000000L + index * 1000)
        } }
        val inventory = app.beanInventoryResult.getOrThrow().events()
        val preparation = app.beanPreparationResult.getOrThrow().current()
        val journal = app.journalResult.getOrThrow().exportJson()
        val usage = app.curves.items.associate { it.id to app.curveUsageResult.getOrThrow().stats(it.id) }
        val chosen = app.curves.items.first { CurveStageParameters.pressureTargets(it).isNotEmpty() }
        try {
        main { check(preferences.edit().putString("selected", chosen.id).commit()) }
        val activity = test.startActivitySync(Intent(context, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
        try {
            check(kotlin.math.abs(activity.resources.configuration.fontScale - expectedFontScale) < .01f) {
                "Home font scale ${activity.resources.configuration.fontScale} != $expectedFontScale"
            }
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (true) {
                var ready = false
                main {
                    val stage = activity.window.decorView.findViewWithTag<CurveStageView>("stage-preview")
                    ready = activity.window.decorView.isLaidOut && stage != null &&
                        stage.targets == CurveStageParameters.pressureTargets(chosen)
                }
                if (ready) break
                check(SystemClock.elapsedRealtime() < deadline) { "Home stage targets did not render" }
                SystemClock.sleep(50)
            }
            main {
                val workspace = activity.window.decorView.findViewWithTag<TaskWorkspaceLayout>("task-workspace")
                val stage = workspace.findViewWithTag<CurveStageView>("stage-preview")
                val fallback = workspace.findViewWithTag<ScrollView>("stage-fallback")
                val supporting = (0 until workspace.childCount).map { workspace.getChildAt(it) }
                    .filterIsInstance<ScrollView>().single()
                val primary = workspace.primaryBody
                val secondary = workspace.secondaryBody
                val originalWidth = workspace.width
                val originalHeight = workspace.height
                val directory = File(context.getExternalFilesDir(null), "home-workbench-checks").apply { mkdirs() }
                fun measure(name: String, widthDp: Int, heightDp: Int) {
                    val width = HoyiUi.dp(activity, widthDp)
                    val height = HoyiUi.dp(activity, heightDp)
                    workspace.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    workspace.layout(0, 0, width, height)
                    check(workspace.primaryBody === primary && workspace.secondaryBody === secondary)
                    check(supporting.height >= HoyiUi.dp(activity, 48)) { "$name supporting actions disappeared" }
                    if (stage.visibility == View.VISIBLE) {
                        check(stage.height >= HoyiUi.dp(activity, 96)) { "$name stage is cropped to ${stage.height}px" }
                        check(fallback.visibility == View.GONE)
                    } else {
                        check(fallback.visibility == View.VISIBLE && fallback.height > 0) { "$name missing stage fallback" }
                        val text = descendants(fallback).filterIsInstance<TextView>().single()
                        check(text.text.contains(activity.getString(R.string.task_brew_preview_note)))
                        val expectedStages = CurveStageParameters.pressureTargets(chosen).mapIndexed { index, target ->
                            activity.getString(R.string.chart_stage, (index + 1).toString()) + " · " +
                                String.format(Locale.ROOT, "%.1f bar", target / 10.0)
                        }.joinToString("\n")
                        check(text.text.toString().endsWith(expectedStages)) { "$name incomplete or reordered stage targets" }
                        LanguageUiLayoutChecks.textFits(text, name)
                        val maximum = (fallback.getChildAt(0).height - fallback.height).coerceAtLeast(0)
                        fallback.scrollTo(0, maximum)
                        check(fallback.scrollY == maximum) { "$name stage details end unreachable" }
                        fallback.scrollTo(0, 0)
                    }
                    // The last supporting action must remain reachable even after short-height fallback.
                    val maximum = (secondary.height - supporting.height).coerceAtLeast(0)
                    supporting.scrollTo(0, maximum)
                    check(supporting.scrollY == maximum) { "$name supporting actions end unreachable" }
                    supporting.scrollTo(0, 0)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(activity.getColor(R.color.mobile_background))
                        workspace.draw(Canvas(bitmap))
                        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                }
                try {
                    measure("recent-wide", 960, 720)
                    val recent = workspace.findViewWithTag<View>("home-recent-curves")
                    check(recent.isShown) { "Recent recipes missing from wide preparation view" }
                    val expectedRecent = CurveOrdering.recentUsed(app.curves.items, ledger, 3)
                    val serviceField = HomeActivity::class.java.getDeclaredField("service").apply { isAccessible = true }
                    val owner = serviceField.get(activity) as? MobileService
                    val state = owner?.shotState
                    expectedRecent.forEachIndexed { index, item ->
                        val row = workspace.findViewWithTag<TextView>("home-recent-curve-$index")
                        check(row.isShown && row.isEnabled && row.text.startsWith(item.name))
                        check(row.height >= HoyiUi.dp(activity, 48))
                        LanguageUiLayoutChecks.textFits(row, "recent-wide")
                    }
                    check(workspace.findViewWithTag<View>("home-recent-curve-0").performClick())
                    check(preferences.getString("selected", null) == expectedRecent.first().id)
                    check(serviceField.get(activity) === owner && owner?.shotState == state)
                    check(stage.targets == CurveStageParameters.pressureTargets(expectedRecent.first()))
                    check(preferences.edit().putString("selected", chosen.id).commit())
                    HomeActivity::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(activity)
                    measure("normal", 360, 640)
                    check(stage.visibility == View.VISIBLE)
                    measure("short-180dp", 360, 180)
                    check(workspace.findViewWithTag<View>("home-recent-curves").visibility == View.GONE)
                    val recentEntry = workspace.findViewWithTag<View>("home-recent-fallback")
                    check(recentEntry.isShown && recentEntry.isEnabled && recentEntry.isClickable)
                    check(recentEntry.height >= HoyiUi.dp(activity, 48))
                    check(supporting.height >= recentEntry.height) { "Short recent entry taller than its viewport" }
                    recentEntry.requestRectangleOnScreen(android.graphics.Rect(0, 0, recentEntry.width, recentEntry.height), true)
                    val entryPosition = IntArray(2); recentEntry.getLocationOnScreen(entryPosition)
                    val viewportPosition = IntArray(2); supporting.getLocationOnScreen(viewportPosition)
                    check(entryPosition[1] >= viewportPosition[1] &&
                        entryPosition[1] + recentEntry.height <= viewportPosition[1] + supporting.height) {
                        "Short recent entry cannot be revealed completely"
                    }
                    measure("recovered", 360, 640)
                    check(stage.visibility == View.VISIBLE)
                } finally {
                    workspace.measure(View.MeasureSpec.makeMeasureSpec(originalWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(originalHeight, View.MeasureSpec.EXACTLY))
                    workspace.layout(0, 0, originalWidth, originalHeight)
                    workspace.requestLayout()
                }
                ScaleTrendPanelChecks.verify(activity, directory)
                check(app.beanInventoryResult.getOrThrow().events() == inventory)
                check(app.beanPreparationResult.getOrThrow().current() == preparation)
                check(app.journalResult.getOrThrow().exportJson() == journal)
                check(app.curves.items.associate { it.id to app.curveUsageResult.getOrThrow().stats(it.id) } == usage)
            }
        } finally {
            main { activity.finish() }
            test.waitForIdleSync()
        }
        } finally { main { restoreMockPreferences(preferences, before) } }
    }
}
