package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale

/** Measures actual library views; preview only, never selects a recipe for brewing. */
internal class CurveLibraryViewportChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    fun run(expectedFontScale: Float) {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val preferences = context.getSharedPreferences("curves", 0)
        val before = preferences.all.toMap()
        val usage = app.curves.items.associate { it.id to app.curveUsageResult.getOrThrow().stats(it.id) }
        val activity = test.startActivitySync(Intent(context, CurveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CurveActivity
        try {
            test.waitForIdleSync()
            main {
                check(kotlin.math.abs(activity.resources.configuration.fontScale - expectedFontScale) < .01f)
                fun field(name: String) = CurveActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
                val chosen = app.curves.items.first { CurveStageParameters.pressureTargets(it).isNotEmpty() }
                CurveActivity::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java)
                    .apply { isAccessible = true }.invoke(activity, chosen)
                val collection = activity.window.decorView.findViewWithTag<TaskCollectionLayout>("task-collection")
                val chart = field("stageChart") as CurveStageView
                val fallback = field("previewFallbackScroll") as ScrollView
                val text = field("previewFallback") as TextView
                val metrics = field("previewMetrics") as CurvePreviewMetrics
                val ability = field("availability") as TextView
                val select = field("select") as Button
                val directory = File(context.getExternalFilesDir(null), "curve-library-checks").apply { mkdirs() }
                val oldWidth = collection.width
                val oldHeight = collection.height
                fun measure(name: String, heightDp: Int) {
                    val width = HoyiUi.dp(activity, 360)
                    val height = HoyiUi.dp(activity, heightDp)
                    collection.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    collection.layout(0, 0, width, height)
                    check(collection.browserBody.visibility == View.GONE && collection.detailBody.visibility == View.VISIBLE)
                    check(select.visibility == View.VISIBLE && select.height >= HoyiUi.dp(activity, 48))
                    for (index in 0 until collection.detailBody.childCount) {
                        val child = collection.detailBody.getChildAt(index)
                        if (child.visibility != View.VISIBLE) continue
                        check(child.height > 0 && child.top >= 0 && child.bottom <= collection.detailBody.height) {
                            "$name detail control/fallback clipped: ${child.javaClass.simpleName} ${child.top}..${child.bottom}/${collection.detailBody.height}"
                        }
                    }
                    if (chart.visibility == View.VISIBLE) {
                        check(metrics.visibility == View.VISIBLE && metrics.contentDescription.isNotBlank())
                        for (columnIndex in 0 until metrics.childCount) {
                            val column=metrics.getChildAt(columnIndex) as android.view.ViewGroup
                            for (labelIndex in 0 until column.childCount)
                                LanguageUiLayoutChecks.textFits(column.getChildAt(labelIndex) as TextView,"$name recipe metric")
                        }
                        check(chart.height >= HoyiUi.dp(activity, 96) && ability.visibility == View.VISIBLE && ability.parent != null)
                    } else {
                        check(fallback.visibility == View.VISIBLE && fallback.height > 0)
                        check(text.text.contains(chosen.name) && text.text.contains(ability.text))
                        check(text.text.contains(metrics.contentDescription))
                        check(text.text.contains(activity.getString(R.string.profiles_stage_caption)))
                        CurveStageParameters.pressureTargets(chosen).forEachIndexed { index, value ->
                            check(text.text.contains(activity.getString(R.string.reference_stage, (index + 1).toString(),
                                String.format(Locale.ROOT, "%.1f", value / 10.0))))
                        }
                        LanguageUiLayoutChecks.textFits(text, name)
                        val maximum = (fallback.getChildAt(0).height - fallback.height).coerceAtLeast(0)
                        fallback.scrollTo(0, maximum); check(fallback.scrollY == maximum); fallback.scrollTo(0, 0)
                    }
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(activity.getColor(R.color.mobile_background)); collection.draw(Canvas(bitmap))
                        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                }
                try {
                    measure("normal", 640); check(chart.visibility == View.VISIBLE)
                    measure("short-180dp", 180)
                    measure("recovered", 640); check(chart.visibility == View.VISIBLE)
                    check(preferences.all == before)
                    check(app.curves.items.associate { it.id to app.curveUsageResult.getOrThrow().stats(it.id) } == usage)
                } finally {
                    collection.measure(View.MeasureSpec.makeMeasureSpec(oldWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(oldHeight, View.MeasureSpec.EXACTLY))
                    collection.layout(0, 0, oldWidth, oldHeight); collection.requestLayout()
                }
            }
            test.waitForIdleSync()
            val frames = java.util.concurrent.CountDownLatch(1)
            main {
                activity.window.decorView.postOnAnimation {
                    activity.window.decorView.postOnAnimation { frames.countDown() }
                }
            }
            check(frames.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "Selected curve window did not draw" }
            val directory = File(context.getExternalFilesDir(null), "curve-library-checks")
            main {
                val collection = activity.window.decorView.findViewWithTag<TaskCollectionLayout>("task-collection")
                check(collection.detailBody.visibility == View.VISIBLE)
                check(preferences.all == before)
                check(app.curves.items.associate { it.id to app.curveUsageResult.getOrThrow().stats(it.id) } == usage)
                TaskTypographyEvidence.capture(activity, directory)
            }
            val window = requireNotNull(test.uiAutomation.takeScreenshot())
            try {
                File(directory, "selected-window.png").outputStream().use {
                    check(window.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { window.recycle() }
            File(directory, "selected-window.txt").writeText(
                "actualWindow=true restoredAfterSyntheticViewport=true fontScale=$expectedFontScale preferencesUnchanged=true usageUnchanged=true noBle=true\n")
        } finally { main { activity.finish() }; test.waitForIdleSync() }
    }
}
