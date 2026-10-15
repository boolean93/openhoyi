package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import java.util.Locale

/** Pressure and cup-weight-derived flow, one plot with explicit independent axes. */
internal class ExtractionOverlayView(context: Context) : View(context) {
    var points: List<ShotPoint> = emptyList()
        set(value) { field = value; refresh() }
    var reference: List<ShotPoint> = emptyList()
        set(value) { field = value; refresh() }
    private var data = ExtractionOverlayProjection.build(points, reference)
    private val pressureColor = context.getColor(R.color.chart_pressure)
    private val flowColor = context.getColor(R.color.chart_coffee)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_label)
        textSize = 11f * resources.displayMetrics.scaledDensity
    }
    private val grid = Paint().apply { color = context.getColor(R.color.chart_grid); strokeWidth = dp(1f) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2.5f) }
    private val path = Path()
    private fun refresh() {
        data = ExtractionOverlayProjection.build(points, reference)
        contentDescription = context.getString(R.string.live_pressure_chart) + "; " + context.getString(R.string.live_cup_flow_chart) + "; " +
            context.getString(R.string.live_cup_flow_source) +
            if (reference.isNotEmpty()) "; " + context.getString(R.string.reference_legend) else ""
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fm = text.fontMetrics
        val lineHeight = fm.descent - fm.ascent
        val inset = maxOf(dp(40f), text.measureText(format(maxOf(data.pressureMaximum, data.flowMaximum))) + dp(10f))
        val left = inset; val right = width - inset
        if (right <= left) return
        fun wrapped(value: String, available: Float): List<String> {
            var remaining = value
            val lines = mutableListOf<String>()
            while (remaining.isNotEmpty()) {
                var count = text.breakText(remaining, true, available, null).coerceAtLeast(1)
                if (count < remaining.length) {
                    val space = remaining.lastIndexOf(' ', count - 1)
                    if (space > 0) count = space
                }
                lines += remaining.take(count).trimEnd()
                remaining = remaining.drop(count).trimStart()
            }
            return lines
        }
        // Wrapped legend uses physical height; large fonts never paint over traces.
        val labels = listOf(context.getString(R.string.extraction_pressure), context.getString(R.string.live_cup_flow_chart))
        val colors = listOf(pressureColor, flowColor)
        var legendX = left; var legendY = dp(5f) - fm.ascent
        labels.forEachIndexed { index, label ->
            val needed = text.measureText(label) + dp(32f)
            if (legendX > left && legendX + needed > right) { legendX = left; legendY += lineHeight + dp(5f) }
            stroke.color = colors[index]; stroke.pathEffect = null
            canvas.drawLine(legendX, legendY + fm.ascent / 2, legendX + dp(14f), legendY + fm.ascent / 2, stroke)
            text.color = colors[index]; text.textAlign = Paint.Align.LEFT
            val lines = wrapped(label, right - legendX - dp(20f))
            lines.forEachIndexed { line, value -> canvas.drawText(value, legendX + dp(20f), legendY + line * lineHeight, text) }
            legendY += (lines.size - 1) * lineHeight
            legendX = if (lines.size > 1) right else legendX + needed
        }
        if (reference.isNotEmpty()) {
            text.color = context.getColor(R.color.chart_label)
            text.textAlign = Paint.Align.LEFT
            legendY += lineHeight + dp(5f)
            val lines = wrapped(context.getString(R.string.reference_legend_compact), right - left)
            lines.forEachIndexed { line, value -> canvas.drawText(value, left, legendY + line * lineHeight, text) }
            legendY += (lines.size - 1) * lineHeight
        }
        val top = legendY + fm.descent + lineHeight * 1.5f + dp(8f)
        val bottom = height - lineHeight * 2 - dp(14f)
        if (right <= left || bottom - top < dp(24f)) return
        text.color = pressureColor; text.textAlign = Paint.Align.RIGHT
        canvas.drawText("bar", left - dp(7f), top - fm.descent - lineHeight / 2 - dp(3f), text)
        text.color = flowColor; text.textAlign = Paint.Align.LEFT
        canvas.drawText("g/s", right + dp(7f), top - fm.descent - lineHeight / 2 - dp(3f), text)
        for (row in 0..4) {
            val py = bottom - (bottom - top) * row / 4
            canvas.drawLine(left, py, right, py, grid)
            text.color = pressureColor; text.textAlign = Paint.Align.RIGHT
            canvas.drawText(format(data.pressureMaximum * row / 4), left - dp(7f), py - (fm.ascent + fm.descent) / 2, text)
            text.color = flowColor; text.textAlign = Paint.Align.LEFT
            canvas.drawText(format(data.flowMaximum * row / 4), right + dp(7f), py - (fm.ascent + fm.descent) / 2, text)
        }
        text.color = context.getColor(R.color.chart_label)
        for (col in 0..3) {
            val px = left + (right - left) * col / 3
            text.textAlign = when (col) { 0 -> Paint.Align.LEFT; 3 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            canvas.drawText(String.format(Locale.ROOT, "%.0f", data.timeSeconds * col / 3), px, bottom + dp(4f) - fm.ascent, text)
        }
        text.textAlign = Paint.Align.CENTER
        canvas.drawText(context.getString(R.string.live_time_axis), (left + right) / 2, height - dp(3f) - fm.descent, text)
        fun trace(samples: List<ShotPoint>, values: List<Float?>, maximum: Float, color: Int, historic: Boolean) {
            path.reset(); var previous: Long? = null
            samples.forEachIndexed { index, point ->
                val value = values[index]
                if (value == null) { previous = null; return@forEachIndexed }
                val px = left + (right - left) * point.elapsedMs / (data.timeSeconds * 1000)
                val py = bottom - (bottom - top) * value / maximum
                if (previous?.let { point.elapsedMs > it && point.elapsedMs - it <= 1500 } == true) path.lineTo(px, py)
                else path.moveTo(px, py)
                previous = point.elapsedMs
            }
            stroke.color = color; stroke.alpha = if (historic) 110 else 255
            stroke.pathEffect = if (historic) DashPathEffect(floatArrayOf(dp(6f), dp(4f)), 0f) else null
            canvas.save(); canvas.clipRect(left, top, right, bottom); canvas.drawPath(path, stroke); canvas.restore()
        }
        trace(reference, data.referencePressure, data.pressureMaximum, pressureColor, true)
        trace(reference, data.referenceFlow, data.flowMaximum, flowColor, true)
        trace(points, data.pressure, data.pressureMaximum, pressureColor, false)
        trace(points, data.flow, data.flowMaximum, flowColor, false)
        if (points.isNotEmpty()) {
            val cursor = left + (right - left) * points.last().elapsedMs / (data.timeSeconds * 1000)
            canvas.drawLine(cursor, top, cursor, bottom, grid)
        } else if (reference.isEmpty()) {
            canvas.drawText(context.getString(R.string.chart_empty_title), (left + right) / 2, (top + bottom) / 2, text)
        }
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun format(value: Float) = String.format(Locale.ROOT, "%.1f", value)
}
