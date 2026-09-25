package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Lightweight native chart. Each series keeps its own labeled unit and scale. */
class ShotChartView(context: Context) : View(context) {
    var points: List<ShotPoint> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.chart_axis); strokeWidth = dp(1) }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.chart_grid); strokeWidth = dp(1) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.chart_label); textSize = dp(11) }
    private val pressure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_pressure); strokeWidth = dp(2.4f); style = Paint.Style.STROKE
    }
    private val flow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_water); strokeWidth = dp(2f); style = Paint.Style.STROKE
    }
    private val coffeeFlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_coffee); strokeWidth = dp(2f); style = Paint.Style.STROKE
    }
    private val weight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_weight); strokeWidth = dp(2.4f); style = Paint.Style.STROKE
    }
    private val temperature = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_temperature); strokeWidth = dp(2f); style = Paint.Style.STROKE
    }
    private val seriesPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(context.getColor(R.color.mobile_surface))
        if (points.isEmpty()) {
            val emptyText = Paint(label).apply { textAlign = Paint.Align.CENTER; textSize = dp(14) }
            canvas.drawText("尚无萃取采样", width / 2f, height / 2f - dp(4), emptyText)
            emptyText.textSize = dp(11)
            canvas.drawText("开始后显示压力、流速、温度与秤重", width / 2f, height / 2f + dp(20), emptyText)
            return
        }
        val left = dp(42)
        val right = width - dp(18)
        val top = dp(74)
        val bottom = height - dp(28)
        if (right <= left || bottom <= top) return
        val maximumMs = max(1000L, points.last().elapsedMs)
        val pressureMax = nice(max(12f, points.maxOf { it.pressureTenthsBar }.toFloat() / 10f))
        val flowMax = nice(max(6f, points.maxOf { it.machineFlowTenthsMlPerSecond }.toFloat() / 10f))
        val coffeeFlows = points.mapNotNull { it.scaleFlowHundredths }
        val coffeeFlowMin = min(0f, (coffeeFlows.minOrNull() ?: 0).toFloat() / 100f)
        val coffeeFlowMax = nice(max(6f, (coffeeFlows.maxOrNull() ?: 0).toFloat() / 100f))
        val weights = points.mapNotNull { it.weightHundredthsGram }
        val weightMin = min(0f, (weights.minOrNull() ?: 0).toFloat() / 100f)
        val weightMax = nice(max(50f, (weights.maxOrNull() ?: 0).toFloat() / 100f))
        val temperatureValues = points.map { it.temperatureHundredthsC / 100f }
        val temperatureMin = max(0f, floor(temperatureValues.minOrNull()!! / 10f) * 10f - 10f)
        val temperatureMax = nice(max(temperatureMin + 20f, temperatureValues.maxOrNull()!! + 5f))
        val widthPx = (right - left).toFloat()
        val heightPx = (bottom - top).toFloat()
        fun x(t: Long) = left + widthPx * t.toFloat() / maximumMs
        fun y(value: Float, low: Float, high: Float) = bottom - heightPx * (value - low) / (high - low)
        for (row in 0..4) {
            val lineY = top + heightPx * row / 4
            canvas.drawLine(left.toFloat(), lineY, right.toFloat(), lineY, grid)
        }
        canvas.drawLine(left.toFloat(), top.toFloat(), left.toFloat(), bottom.toFloat(), axis)
        canvas.drawLine(left.toFloat(), bottom.toFloat(), right.toFloat(), bottom.toFloat(), axis)
        val legendMid = (left + right) / 2f
        canvas.drawText("${pressureMax.toInt()} bar 压力", left.toFloat(), dp(17), pressure.apply { style = Paint.Style.FILL; textSize = dp(11) })
        canvas.drawText("${flowMax.toInt()} ml/s 水流", legendMid, dp(17), flow.apply { style = Paint.Style.FILL; textSize = dp(11) })
        if (coffeeFlows.isNotEmpty()) canvas.drawText("${coffeeFlowMax.toInt()} g/s 秤流速", left.toFloat(), dp(35),
            coffeeFlow.apply { style = Paint.Style.FILL; textSize = dp(11) })
        if (weights.isNotEmpty()) canvas.drawText("${weightMax.toInt()} g 重量", legendMid, dp(35),
            weight.apply { style = Paint.Style.FILL; textSize = dp(11) })
        canvas.drawText("${temperatureMin.toInt()}–${temperatureMax.toInt()} °C 温度", left.toFloat(), dp(53),
            temperature.apply { style = Paint.Style.FILL; textSize = dp(11) })
        pressure.style = Paint.Style.STROKE
        flow.style = Paint.Style.STROKE
        coffeeFlow.style = Paint.Style.STROKE
        weight.style = Paint.Style.STROKE
        temperature.style = Paint.Style.STROKE
        canvas.drawText("0", dp(16), bottom.toFloat(), label)
        canvas.drawText("${maximumMs / 1000}s", right - dp(28f), height - dp(8f), label)
        fun drawSeries(paint: Paint, values: (ShotPoint) -> Float?, low: Float, high: Float) {
            seriesPath.reset()
            var drawing = false
            var count = 0
            var lastX = 0f
            var lastY = 0f
            for (point in points) {
                val value = values(point)
                if (value == null) { drawing = false; continue }
                val px = x(point.elapsedMs)
                val py = y(value.coerceIn(low, high), low, high)
                if (drawing) seriesPath.lineTo(px, py) else { seriesPath.moveTo(px, py); drawing = true }
                lastX = px; lastY = py; count++
            }
            canvas.drawPath(seriesPath, paint)
            if (count == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(lastX, lastY, dp(3), paint)
                paint.style = Paint.Style.STROKE
            }
        }
        drawSeries(pressure, { it.pressureTenthsBar / 10f }, 0f, pressureMax)
        drawSeries(flow, { it.machineFlowTenthsMlPerSecond / 10f }, 0f, flowMax)
        if (coffeeFlows.isNotEmpty()) drawSeries(coffeeFlow,
            { it.scaleFlowHundredths?.div(100f) }, coffeeFlowMin, coffeeFlowMax)
        if (weights.isNotEmpty()) drawSeries(weight, { it.weightHundredthsGram?.div(100f) }, weightMin, weightMax)
        drawSeries(temperature, { it.temperatureHundredthsC / 100f }, temperatureMin, temperatureMax)
    }

    private fun nice(value: Float): Float = max(1f, ceil(value / 5f) * 5f)
    private fun dp(value: Number) = value.toFloat() * resources.displayMetrics.density
}
