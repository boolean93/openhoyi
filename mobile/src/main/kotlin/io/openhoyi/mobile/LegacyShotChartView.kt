package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max

/** Only the two legacy series with established physical units are charted. */
class LegacyShotChartView(context: Context) : View(context) {
    var points: List<LegacyPoint> = emptyList()
        set(value) { field = value; invalidate() }
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_axis); strokeWidth = dp(1f)
    }
    private val pressure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_pressure); strokeWidth = dp(2.5f); style = Paint.Style.STROKE
    }
    private val flow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_water); strokeWidth = dp(2.5f); style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(8f), dp(6f)), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = TaskTypography.face(context)
        color = context.getColor(R.color.chart_label); textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(context.getColor(R.color.mobile_surface))
        if (points.isEmpty()) { canvas.drawText(context.getString(R.string.chart_legacy_empty), dp(16f), height / 2f, label); return }
        val maxTime = max(1.0, points.last().seconds)
        val maxPressure = max(12.0, points.maxOf(LegacyPoint::pressureBar))
        val maxFlow = max(6.0, points.maxOf(LegacyPoint::waterFlow))
        fun number(value: Double) = "%.1f".format(value)
        val lineHeight = label.fontMetrics.descent - label.fontMetrics.ascent
        val left = max(dp(42f), label.measureText(number(maxPressure)) + dp(12f))
        val right = width - max(dp(42f), label.measureText(number(maxFlow)) + dp(12f))
        val top = max(dp(72f), lineHeight * 3 + dp(24f))
        val bottom = height - max(dp(30f), lineHeight + dp(12f))
        if (right <= left || bottom <= top) return
        val firstBaseline = dp(4f) - label.fontMetrics.ascent
        val secondBaseline = firstBaseline + lineHeight + dp(4f)
        canvas.drawLine(left, firstBaseline - lineHeight / 3, left + dp(24f), firstBaseline - lineHeight / 3, pressure)
        canvas.drawLine(left, secondBaseline - lineHeight / 3, left + dp(24f), secondBaseline - lineHeight / 3, flow)
        canvas.drawText(context.getString(R.string.chart_legacy_pressure, number(maxPressure)), left + dp(32f), firstBaseline, label)
        canvas.drawText(context.getString(R.string.chart_legacy_water, number(maxFlow)), left + dp(32f), secondBaseline, label)
        canvas.drawText("bar", left, top - dp(8f), label)
        canvas.drawText("ml/s", right - label.measureText("ml/s"), top - dp(8f), label)
        val grid = Paint(axis).apply { alpha = 55 }
        val divisions = ((bottom - top) / (lineHeight + dp(8f))).toInt().coerceIn(1, 6)
        for (index in 0..divisions) {
            val fraction = index.toFloat() / divisions
            val y = bottom - (bottom - top) * fraction
            canvas.drawLine(left, y, right, y, grid)
            val baseline = y - (label.fontMetrics.ascent + label.fontMetrics.descent) / 2
            val pressureTick = number(maxPressure * fraction)
            canvas.drawText(pressureTick, left - dp(6f) - label.measureText(pressureTick), baseline, label)
            canvas.drawText(number(maxFlow * fraction), right + dp(6f), baseline, label)
        }
        canvas.drawLine(left, top, left, bottom, axis)
        canvas.drawLine(right, top, right, bottom, axis)
        canvas.drawLine(left, bottom, right, bottom, axis)
        val timeDivisions = ((right - left) / (label.measureText(number(maxTime) + " s") + dp(16f))).toInt().coerceIn(1, 4)
        for (index in 0..timeDivisions) {
            val x = left + (right - left) * index / timeDivisions
            canvas.drawLine(x, top, x, bottom, grid)
            val tick = number(maxTime * index / timeDivisions) + " s"
            val textX = (x - label.measureText(tick) / 2).coerceIn(0f, (width - label.measureText(tick)).coerceAtLeast(0f))
            canvas.drawText(tick, textX, bottom + dp(6f) - label.fontMetrics.ascent, label)
        }
        fun plot(paint: Paint, maximum: Double, value: (LegacyPoint) -> Double) {
            path.reset()
            points.forEachIndexed { index, point ->
                val x = left + (right - left) * (point.seconds / maxTime).toFloat()
                val y = bottom - (bottom - top) * (value(point) / maximum).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
        }
        plot(pressure, maxPressure, LegacyPoint::pressureBar)
        plot(flow, maxFlow, LegacyPoint::waterFlow)
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
