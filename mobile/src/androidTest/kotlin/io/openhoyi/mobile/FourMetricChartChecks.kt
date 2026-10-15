package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import java.io.File

/** Native component fixtures, not fabricated machine sessions or hardware evidence. */
internal class FourMetricChartChecks(private val test: Instrumentation) {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    fun run() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        val activity = test.startActivitySync(Intent(test.targetContext, ExtractionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ExtractionActivity
        try {
            test.waitForIdleSync()
            val directory = File(test.targetContext.getExternalFilesDir(null), "four-metric-chart-checks").apply { mkdirs() }
            val samples = (0..30).map { index ->
                ShotPoint(index * 800L, if (index < 5) index * 18 else 90 - (index - 5),
                    999, index * 18, 9300 + index * 2, index * 120, 999, true)
            }
            val cases = listOf(
                Triple("machine-only", samples.map { it.copy(weightHundredthsGram = null) }, emptyList()),
                Triple("four-metrics", samples, emptyList()),
                Triple("gaps-and-reference", samples.filterIndexed { index, _ -> index !in 12..16 }.mapIndexed { index, point ->
                    point.copy(weightHundredthsGram = point.weightHundredthsGram.takeUnless { index in 5..7 },
                        waterTenthsMl = if (index > 15) (index - 16) * 15 else point.waterTenthsMl)
                }, samples.map { it.copy(elapsedMs = it.elapsedMs * 2) })
            )
            test.runOnMainSync {
                val actual = ExtractionActivity::class.java.getDeclaredField("chart").apply { isAccessible = true }.get(activity) as View
                check(actual.width > 0 && actual.height > 0)
                for ((name, points, reference) in cases) {
                    val probe = ExtractionOverlayView(activity)
                    probe.points = points
                    probe.reference = reference
                    probe.measure(View.MeasureSpec.makeMeasureSpec(actual.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(actual.height, View.MeasureSpec.EXACTLY))
                    probe.layout(0, 0, actual.width, actual.height)
                    val expected = if (name == "machine-only") listOf("PRESSURE", "WATER", "TEMPERATURE")
                        else listOf("PRESSURE", "WATER", "WEIGHT", "TEMPERATURE")
                    val controls = descendants(probe).filter { it.tag?.toString()?.startsWith("chart-metric:") == true }
                    check(controls.map { it.tag } == expected.map { "chart-metric:$it" }) {
                        "$name: expected usable four-metric legend, got ${controls.map { it.tag }}"
                    }
                    for (control in controls) {
                        check(control.isClickable && control.width >= HoyiUi.dp(activity, 48) && control.height >= HoyiUi.dp(activity, 48))
                        check(!control.contentDescription.isNullOrBlank())
                        check(control.performClick() && control.isSelected) { "$name: legend cannot focus ${control.tag}" }
                    }
                    val bitmap = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
                    try {
                        probe.draw(android.graphics.Canvas(bitmap))
                        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                }
            }
            test.sendStatus(0, Bundle().apply { putString("stream", "FOUR_METRIC_COMPONENT_FIXTURES machine-only,four-metrics,gaps-and-reference\n") })
        } finally { test.runOnMainSync { activity.finish() }; test.waitForIdleSync() }
    }
}
