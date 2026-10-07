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
            canvas.drawText(context.getString(R.string.chart_empty_title), width / 2f, height / 2f - dp(4), emptyText)
            emptyText.textSize = dp(11)
            canvas.drawText(context.getString(R.string.chart_empty_message), width / 2f, height / 2f + dp(20), emptyText)
            return
        }
        val left = dp(42)
        val right = width - dp(18)
        val bottom = height - dp(28)
        if (right <= left || bottom <= dp(74)) return
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
        val legends = mutableListOf(
            context.getString(R.string.chart_pressure_legend, pressureMax.toInt().toString()) to pressure,
            context.getString(R.string.chart_water_legend, flowMax.toInt().toString()) to flow,
        )
        if (coffeeFlows.isNotEmpty()) legends += context.getString(R.string.chart_scale_flow_legend,
            coffeeFlowMax.toInt().toString()) to coffeeFlow
        if (weights.isNotEmpty()) legends += context.getString(R.string.chart_weight_legend,
            weightMax.toInt().toString()) to weight
        legends += context.getString(R.string.chart_temperature_legend,
            temperatureMin.toInt().toString(), temperatureMax.toInt().toString()) to temperature
        val top = max(dp(74), drawLegends(canvas, legends) + dp(14))
        if (bottom <= top) return
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

    /** Full labels keep their units; only layout changes, never the sampled values or scales. */
    private fun drawLegends(canvas: Canvas, legends: List<Pair<String, Paint>>): Float {
        val inset = dp(16)
        val available = width - inset * 2
        val gap = dp(12)
        val textPaint = Paint(label).apply { style = Paint.Style.FILL }
        val bounds = android.graphics.Rect()
        fun extent(text: String): Float {
            textPaint.getTextBounds(text, 0, text.length, bounds)
            return max(textPaint.measureText(text), bounds.right.toFloat()) - min(0f, bounds.left.toFloat())
        }
        // Normally labels occupy one item. Very narrow components can split at grapheme boundaries
        // without truncating the name, number or unit, or cutting UTF-16 surrogate pairs.
        fun fragments(text: String): List<String> {
            if (extent(text) <= available) return listOf(text)
            val iterator = java.text.BreakIterator.getCharacterInstance(resources.configuration.locales[0])
            iterator.setText(text)
            val result = mutableListOf<String>()
            var start = 0
            while (start < text.length) {
                var end = iterator.following(start)
                var fit = end
                while (end != java.text.BreakIterator.DONE && extent(text.substring(start, end)) <= available) {
                    fit = end
                    end = iterator.next()
                }
                result += text.substring(start, fit)
                start = fit
            }
            return result
        }
        val items = legends.map { (text, paint) -> paint.color to fragments(text) }
        var ascent = -textPaint.fontMetrics.ascent
        var descent = textPaint.fontMetrics.descent
        items.forEach { (_, pieces) -> pieces.forEach { piece ->
            textPaint.getTextBounds(piece, 0, piece.length, bounds)
            ascent = max(ascent, -bounds.top.toFloat())
            descent = max(descent, bounds.bottom.toFloat())
        } }
        val rowHeight = max(dp(18), ascent + descent + dp(6))
        var x = inset
        var baseline = dp(8) + ascent
        items.forEach { (color, pieces) -> pieces.forEach { piece ->
            val span = extent(piece)
            if (x > inset && x + span > width - inset) { x = inset; baseline += rowHeight }
            textPaint.getTextBounds(piece, 0, piece.length, bounds)
            textPaint.color = color
            canvas.drawText(piece, x - min(0f, bounds.left.toFloat()), baseline, textPaint)
            x += span + gap
        } }
        return baseline + descent
    }

    private fun nice(value: Float): Float = max(1f, ceil(value / 5f) * 5f)
    private fun dp(value: Number) = value.toFloat() * resources.displayMetrics.density
}
