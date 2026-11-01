package io.openhoyi.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import io.openhoyi.protocol.ScaleEvidence

/** Native pixel assertions: real plot marks exist, but a telemetry gap stays empty. */
internal object ScaleTrendChartChecks {
    fun verify(context: Context) {
        val chart = ScaleTrendView(context)
        val density = context.resources.displayMetrics.density
        val width = (600 * density).toInt()
        val height = (300 * density).toInt()
        chart.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        chart.layout(0, 0, width, height)
        val source = ScaleEvidence.LIVE_READ_ONLY
        chart.present(listOf(
            ScaleTrendWindow.Point(1000, 200, 1, source), ScaleTrendWindow.Point(2000, 800, 1, source),
            ScaleTrendWindow.Point(8000, 300, 2, source), ScaleTrendWindow.Point(9000, 900, 2, source)), 10000)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            chart.draw(Canvas(bitmap))
            val color = context.getColor(R.color.mobile_accent)
            fun count(low: Double, high: Double): Int {
                var result = 0
                for (x in (width * low).toInt()..(width * high).toInt())
                    for (y in 0 until height) if (bitmap.getPixel(x, y) == color) result++
                return result
            }
            check(count(.10, .30) > 0 && count(.75, .95) > 0) { "Scale trend missing native segments" }
            check(count(.40, .65) == 0) { "Scale trend bridged disconnected segments" }
            chart.present(emptyList(), 20001)
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            chart.draw(Canvas(bitmap))
            check(count(0.0, .99) == 0) { "Scale trend retained expired marks" }
            check(chart.contentDescription == context.getString(R.string.scale_trend_empty))
        } finally { bitmap.recycle() }
    }
}
