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
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_label); textSize = dp(12f)
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(context.getColor(R.color.mobile_surface))
        if (points.isEmpty()) { canvas.drawText("暂无旧版采样", dp(16f), height / 2f, label); return }
        val left = dp(42f); val right = width - dp(18f)
        val top = dp(52f); val bottom = height - dp(30f)
        if (right <= left || bottom <= top) return
        val maxTime = max(1.0, points.last().seconds)
        val maxPressure = max(12.0, points.maxOf(LegacyPoint::pressureBar))
        val maxFlow = max(6.0, points.maxOf(LegacyPoint::waterFlow))
        canvas.drawText("压力 ${"%.1f".format(maxPressure)} bar", left, dp(20f), label)
        canvas.drawText("机器水流 ${"%.1f".format(maxFlow)} ml/s", left, dp(40f), label)
        canvas.drawLine(left, top, left, bottom, axis)
        canvas.drawLine(left, bottom, right, bottom, axis)
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
        canvas.drawText("0", dp(14f), bottom, label)
        canvas.drawText("${maxTime.toInt()}s", right - dp(30f), height - dp(8f), label)
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
