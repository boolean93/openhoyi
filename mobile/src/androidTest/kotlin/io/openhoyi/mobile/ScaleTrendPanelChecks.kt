package io.openhoyi.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.openhoyi.protocol.ScaleEvidence
import java.io.File

/** Detached native component fixtures, not hardware or full-window acceptance. */
internal object ScaleTrendPanelChecks {
    fun verify(activity: ThemedActivity, directory: File) {
        check(BuildConfig.MOCK_MODE && activity.packageName == "io.openhoyi.mobile.mock")
        val root = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val primary = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val secondary = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(primary, LinearLayout.LayoutParams(HoyiUi.dp(activity, 600), -2))
        root.addView(secondary, LinearLayout.LayoutParams(HoyiUi.dp(activity, 280), -2))
        val panel = ScaleTrendPanel(activity, primary, secondary)
        val block = primary.findViewWithTag<View>("scale-trend")
        val fallback = secondary.findViewWithTag<View>("scale-trend-fallback")
        fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        val chart = descendants(block).filterIsInstance<ScaleTrendView>().single()
        fun capture(name: String) {
            root.measure(View.MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, 880), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            check(root.height > 0)
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            try {
                image.eraseColor(activity.getColor(R.color.mobile_background))
                root.draw(Canvas(image))
                File(directory, "scale-trend-$name.png").outputStream().use {
                    check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { image.recycle() }
        }
        try {
            val fixture = listOf(
                ScaleTrendWindow.Point(1000, -100, 1, ScaleEvidence.LIVE_READ_ONLY),
                ScaleTrendWindow.Point(2000, 1200, 1, ScaleEvidence.LIVE_READ_ONLY),
                ScaleTrendWindow.Point(8000, 1700, 2, ScaleEvidence.LIVE_READ_ONLY),
                ScaleTrendWindow.Point(9000, 1800, 2, ScaleEvidence.LIVE_READ_ONLY))
            panel.present(fixture, 10000, activity.getString(R.string.scale_read_only_live))
            val natural = panel.fit(HoyiUi.dp(activity, 600), HoyiUi.dp(activity, 500))
            check(natural > 0 && block.visibility == View.VISIBLE && fallback.visibility == View.GONE)
            capture("normal")
            check(chart.height >= HoyiUi.dp(activity, 180))
            val description = chart.contentDescription.toString()
            check(description != activity.getString(R.string.scale_trend_empty))
            check(panel.fit(HoyiUi.dp(activity, 600), HoyiUi.dp(activity, 100)) == 0)
            capture("short")
            check(block.visibility == View.GONE && fallback.visibility == View.VISIBLE)
            check(fallback.isEnabled && fallback.isClickable && fallback.height >= HoyiUi.dp(activity, 48))
            LanguageUiLayoutChecks.textFits(fallback as TextView, "scale trend fallback")
            check(fallback.performClick())
            val dialogField = ScaleTrendPanel::class.java.getDeclaredField("dialog").apply { isAccessible = true }
            val detail = dialogField.get(panel) as android.app.AlertDialog
            check(detail.isShowing)
            fun detailText() = descendants(detail.window!!.decorView).filterIsInstance<TextView>()
                .joinToString("\n") { it.text.toString() }
            check(detailText().contains(activity.getString(R.string.scale_read_only_live)))
            panel.present(fixture, 10000, activity.getString(R.string.scale_disconnected))
            check(detailText().contains(activity.getString(R.string.scale_disconnected)))
            check(!detailText().contains(activity.getString(R.string.scale_read_only_live)))
            check(detailText().contains(description))
            panel.dismiss()
            check(!detail.isShowing && dialogField.get(panel) == null)
            check(panel.fit(HoyiUi.dp(activity, 600), HoyiUi.dp(activity, 500)) == natural)
            capture("recovered")
            check(descendants(block).filterIsInstance<ScaleTrendView>().single() === chart)
            check(chart.contentDescription.toString() == description)
            panel.present(emptyList(), 20001, activity.getString(R.string.scale_disconnected))
            panel.fit(HoyiUi.dp(activity, 600), HoyiUi.dp(activity, 500))
            capture("empty")
            check(chart.contentDescription == activity.getString(R.string.scale_trend_empty))
        } finally { panel.dismiss() }
    }
}
