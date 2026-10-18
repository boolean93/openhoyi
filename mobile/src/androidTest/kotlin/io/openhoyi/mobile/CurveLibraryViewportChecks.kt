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
                var chosen = app.curves.items.first { CurveStageParameters.pressureTargets(it).isNotEmpty() }
                val originalChosen = chosen
                CurveActivity::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java)
                    .apply { isAccessible = true }.invoke(activity, chosen)
                val collection = activity.window.decorView.findViewWithTag<TaskCollectionLayout>("task-collection")
                val chart = field("stageChart") as CurveStageView
                val fallback = field("previewFallbackScroll") as ScrollView
                val text = field("previewFallback") as TextView
                val metrics = field("previewMetrics") as CurvePreviewMetrics
                val ability = field("availability") as TextView
                val select = field("select") as Button
                val parameter = field("parameterShortcut") as Button
                val directory = File(context.getExternalFilesDir(null), "curve-library-checks").apply { mkdirs() }
                val oldWidth = collection.width
                val oldHeight = collection.height
                fun measure(name: String, width: Int, height: Int) {
                    collection.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    collection.layout(0, 0, width, height)
                    check(collection.detailBody.visibility == View.VISIBLE)
                    if (width == HoyiUi.dp(activity, 360)) check(collection.browserBody.visibility == View.GONE)
                    for (action in listOf(select, parameter)) {
                        check(action.isShown && action.height >= HoyiUi.dp(activity, 48))
                        LanguageUiLayoutChecks.textFits(action, "$name fixed curve action")
                        val bounds = android.graphics.Rect(0, 0, action.width, action.height)
                        collection.offsetDescendantRectToMyCoords(action, bounds)
                        check(bounds.left >= 0 && bounds.right <= width && bounds.top >= 0 && bounds.bottom <= height) {
                            "$name fixed curve action clipped: ${action.text} $bounds"
                        }
                        val row = action.parent as android.widget.LinearLayout
                        check(action.top >= 0 && action.bottom <= row.height) {
                            "$name action extends outside its own row: ${action.top}..${action.bottom}/${row.height}"
                        }
                    }
                    // Doubled integer centers allow <=1 only for odd/even height rounding
                    // (<=0.5px center quantization), never clipping; bounds above remain strict.
                    check(kotlin.math.abs((parameter.top + parameter.bottom) - (select.top + select.bottom)) <= 1) {
                        "$name footer bounds are not vertically centered"
                    }
                    File(directory, "$name-footer.txt").writeText(listOf(parameter, select).joinToString("\n") {
                        "text=${it.text} size=${it.width}x${it.height} local=${it.left},${it.top}..${it.right},${it.bottom} " +
                            "parent=${(it.parent as View).width}x${(it.parent as View).height}"
                    } + "\nfontScale=$expectedFontScale collection=${width}x$height syntheticMeasure=true\n")
                    for (index in 0 until collection.detailBody.childCount) {
                        val child = collection.detailBody.getChildAt(index)
                        if (child.visibility != View.VISIBLE) continue
                        check(child.height > 0 && child.top >= 0 && child.bottom <= collection.detailBody.height) {
                            "$name detail control/fallback clipped: ${child.javaClass.simpleName} ${child.top}..${child.bottom}/${collection.detailBody.height}"
                        }
                    }
                    if (chart.visibility == View.VISIBLE) {
                        check(metrics.visibility == View.VISIBLE && metrics.contentDescription.isNotBlank())
                        check(metrics.childCount == 3) { "$name recipe metrics lost a column" }
                        for (columnIndex in 0 until metrics.childCount) {
                            val column=metrics.getChildAt(columnIndex) as android.view.ViewGroup
                            check(column.childCount == 2) { "$name recipe metric column lost label/value" }
                            if (column.getChildAt(1) !is TextView) check(column.getChildAt(0).tag == "product/curve-library/summary/$columnIndex/label")
                            for (labelIndex in 0 until column.childCount) {
                                val node=column.getChildAt(labelIndex)
                                if (node is TextView) {
                                    LanguageUiLayoutChecks.textFits(node,"$name recipe metric")
                                } else {
                                    // Only the explicit M3 number/unit row may replace the legacy value TextView.
                                    val prefix="product/curve-library/summary/$columnIndex"
                                    check(node is android.widget.LinearLayout && node.tag == prefix) {
                                        "$name unexpected recipe metric container ${node.javaClass.name}/${node.tag}"
                                    }
                                    check(node.childCount == if (columnIndex < 2) 2 else 1)
                                    val number=node.getChildAt(0) as TextView
                                    check(number.tag == "$prefix/number" && number.isShown)
                                    LanguageUiLayoutChecks.textFits(number,"$name recipe metric number")
                                    if (columnIndex < 2) {
                                        val unit=node.getChildAt(1) as TextView
                                        check(unit.tag == "$prefix/unit")
                                        if (unit.visibility == View.VISIBLE) {
                                            check(unit.isShown)
                                            LanguageUiLayoutChecks.textFits(unit,"$name recipe metric unit")
                                            val a=IntArray(2); val b=IntArray(2)
                                            number.getLocationOnScreen(a); unit.getLocationOnScreen(b)
                                            check(number.baseline >= 0 && unit.baseline >= 0 &&
                                                a[1]+number.baseline == b[1]+unit.baseline) {
                                                "$name recipe metric number/unit baseline differs"
                                            }
                                        } else check(unit.visibility == View.GONE) { "$name unit state must be visible or explicitly absent" }
                                    }
                                }
                            }
                        }
                        check(chart.height >= HoyiUi.dp(activity, 96) && ability.visibility == View.VISIBLE && ability.parent != null)
                    } else {
                        check(fallback.visibility == View.VISIBLE && fallback.height > 0)
                        val targets = CurveStageParameters.pressureTargets(chosen)
                        if (targets.isEmpty()) {
                            check(text.text.contains(activity.getString(R.string.task_curve_library_no_pressure)))
                        } else {
                            check(text.text.contains(chosen.name) && text.text.contains(ability.text))
                            check(text.text.contains(metrics.contentDescription))
                            check(text.text.contains(activity.getString(R.string.profiles_stage_caption)))
                            targets.forEachIndexed { index, value ->
                                check(text.text.contains(activity.getString(R.string.reference_stage, (index + 1).toString(),
                                    String.format(Locale.ROOT, "%.1f", value / 10.0))))
                            }
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
                    val compactWidth = HoyiUi.dp(activity, 360)
                    measure("normal", compactWidth, HoyiUi.dp(activity, 640)); check(chart.visibility == View.VISIBLE)
                    measure("short-180dp", compactWidth, HoyiUi.dp(activity, 180))
                    measure("recovered", compactWidth, HoyiUi.dp(activity, 640)); check(chart.visibility == View.VISIBLE)
                    for ((kind, item) in listOf(
                        "noPressure" to app.curves.items.first { it.factoryCurve != null && CurveStageParameters.pressureTargets(it).isEmpty() },
                        "fourStage" to CurveFooterFixtures.fourStage(app.curves))) {
                        chosen = item
                        CurveActivity::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java)
                            .apply { isAccessible = true }.invoke(activity, chosen)
                        // Use this CI profile's actual collection space, including the user tablet profiles.
                        measure("footer-$kind-actual-space", oldWidth, oldHeight)
                        check(select.isEnabled == app.curves.canStart(item))
                    }
                    CurveActivity::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java)
                        .apply { isAccessible = true }.invoke(activity, originalChosen)
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
