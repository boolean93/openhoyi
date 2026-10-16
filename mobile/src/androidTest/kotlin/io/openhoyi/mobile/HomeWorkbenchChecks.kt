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
                    measure("normal", 360, 640)
                    check(stage.visibility == View.VISIBLE)
                    measure("short-180dp", 360, 180)
                    measure("recovered", 360, 640)
                    check(stage.visibility == View.VISIBLE)
                } finally {
                    workspace.measure(View.MeasureSpec.makeMeasureSpec(originalWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(originalHeight, View.MeasureSpec.EXACTLY))
                    workspace.layout(0, 0, originalWidth, originalHeight)
                    workspace.requestLayout()
                }
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
