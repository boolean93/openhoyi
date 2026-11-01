package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import java.util.Locale
import kotlin.math.ceil

internal enum class ExtractionTrendMetric { PRESSURE, CUP_FLOW }

/** One physical quantity per chart; identical time windows keep two plots directly aligned. */
internal class ExtractionTrendView(context: Context, private val metric: ExtractionTrendMetric) : View(context) {
    private var maximum = if (metric == ExtractionTrendMetric.PRESSURE) 12f else 3f
    private var projected = emptyList<Float?>()
    private var referenceProjected = emptyList<Float?>()
    var reference: List<ShotPoint> = emptyList()
        set(value) {
            field=value
            referenceProjected=if(metric==ExtractionTrendMetric.PRESSURE) RealtimeShotProjection.values(value)
                else CupFlowProjection.values(value)
            updateMaximum()
            invalidate()
        }
    var points: List<ShotPoint> = emptyList()
        set(value) {
            if (value.isEmpty() || (value.last().elapsedMs < (field.lastOrNull()?.elapsedMs ?: 0)))
                maximum = if (metric == ExtractionTrendMetric.PRESSURE) 12f else 3f
            field = value
            projected = if (metric == ExtractionTrendMetric.PRESSURE) RealtimeShotProjection.values(value)
                else CupFlowProjection.values(value)
            updateMaximum()
            invalidate()
        }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(if (metric == ExtractionTrendMetric.PRESSURE) R.color.chart_pressure else R.color.chart_coffee)
        strokeWidth = dp(2.5f); style = Paint.Style.STROKE
    }
    private val grid = Paint().apply { color = context.getColor(R.color.chart_grid); strokeWidth = dp(1f) }
    private val referenceLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=context.getColor(R.color.mobile_muted);strokeWidth=dp(2f);style=Paint.Style.STROKE
        pathEffect=DashPathEffect(floatArrayOf(dp(6f),dp(4f)),0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_label)
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
            11f, resources.displayMetrics)
    }
    private val emptyArea = Paint().apply { color = context.getColor(R.color.mobile_accent_soft); alpha = 75 }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val metrics = text.fontMetrics
        val labelHeight = metrics.descent - metrics.ascent
        val left = maxOf(dp(48f), text.measureText(String.format(Locale.ROOT, "%.1f", maximum)) + dp(10f))
        val right = width - dp(16f)
        val top = maxOf(dp(20f), labelHeight / 2 + dp(8f))
        val bottom = height - labelHeight * 2 - dp(18f)
        if (right <= left || bottom <= top) return
        if (points.isEmpty() && reference.isEmpty()) {
            text.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.chart_empty_title), width / 2f, height / 2f, text)
            text.textAlign = Paint.Align.LEFT
            return
        }
        val lastAt=maxOf(points.lastOrNull()?.elapsedMs ?: 0,reference.lastOrNull()?.elapsedMs ?: 0)
        val timeSeconds = maxOf(30f, ceil(lastAt / 10000f) * 10f)
        fun x(ms: Long) = left + (right - left) * ms / (timeSeconds * 1000f)
        fun y(v: Float) = bottom - (bottom - top) * v / maximum
        val endX = x(points.lastOrNull()?.elapsedMs ?: 0)
        canvas.drawRect(endX, top, right, bottom, emptyArea)
        val noData = context.getString(R.string.live_chart_no_data)
        if (right - endX >= text.measureText(noData) + dp(16f))
            canvas.drawText(noData, endX + dp(8f), top - metrics.ascent, text)
        text.textAlign = Paint.Align.RIGHT
        for (row in 0..4) {
            val value = maximum * row / 4
            val py = y(value)
            canvas.drawLine(left, py, right, py, grid)
            canvas.drawText(String.format(Locale.ROOT, "%.1f", value), left - dp(8f), py - (metrics.ascent + metrics.descent) / 2, text)
        }
        for (column in 0..3) {
            val px = left + (right - left) * column / 3
            canvas.drawLine(px, top, px, bottom, grid)
            text.textAlign = when (column) { 0 -> Paint.Align.LEFT; 3 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            canvas.drawText(String.format(Locale.ROOT, "%.0f", timeSeconds * column / 3), px, bottom + dp(6f) - metrics.ascent, text)
        }
        text.textAlign = Paint.Align.CENTER
        canvas.drawText(context.getString(R.string.live_time_axis), (left + right) / 2, height - dp(4f) - metrics.descent, text)
        text.textAlign = Paint.Align.LEFT
        drawTrace(canvas,reference,referenceProjected,referenceLine,::x,::y,false)
        drawTrace(canvas,points,projected,line,::x,::y,true)
    }
    private fun drawTrace(canvas:Canvas,samples:List<ShotPoint>,values:List<Float?>,paint:Paint,
        x:(Long)->Float,y:(Float)->Float,latest:Boolean) {
        path.reset()
        var connected = false
        var previousAt: Long? = null
        var lastX: Float? = null
        var lastY = 0f
        samples.forEachIndexed { index, point ->
            val value = values[index]
            if (value == null) { connected = false; previousAt = null; return@forEachIndexed }
            val px = x(point.elapsedMs); val py = y(value)
            if (connected && previousAt?.let { point.elapsedMs - it <= 1500 } == true) path.lineTo(px, py)
            else path.moveTo(px, py)
            connected = true; previousAt = point.elapsedMs; lastX = px; lastY = py
        }
        canvas.drawPath(path, paint)
        if(latest) lastX?.let { px ->
            paint.style = Paint.Style.FILL
            canvas.drawCircle(px, lastY, dp(3f), paint)
            paint.style = Paint.Style.STROKE
        }
    }
    private fun updateMaximum() {
        maximum=maxOf(maximum,ceil((projected+referenceProjected).filterNotNull().maxOrNull() ?: 0f))
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
