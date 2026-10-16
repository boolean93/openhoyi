package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** Physical grams against monotonic reception time; segments never bridge missing data. */
internal class ScaleTrendView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = TaskTypography.face(context) }
    private var points = emptyList<ScaleTrendWindow.Point>()
    private var nowMs = 0L
    fun present(values: List<ScaleTrendWindow.Point>, now: Long) {
        points = values.filter { it.atMs <= now && now - it.atMs <= 10_000 }
        nowMs = now
        contentDescription = if (points.isEmpty()) context.getString(R.string.scale_trend_empty)
        else context.getString(R.string.scale_trend_summary, number(points.last().hundredthsGram / 100.0),
            number((now - points.last().atMs) / 1000.0))
        invalidate()
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun number(value: Double) = String.format(resources.configuration.locales[0], "%.1f", value)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.textSize = 12 * resources.displayMetrics.scaledDensity
        paint.style = Paint.Style.FILL
        val low = floor(minOf(0.0, points.minOfOrNull { it.hundredthsGram / 100.0 } ?: 0.0))
        val high = ceil(maxOf(low + 1.0, points.maxOfOrNull { it.hundredthsGram / 100.0 } ?: 1.0))
        val labels = (0..2).map { number(low + (high - low) * it / 2) }
        val fm = paint.fontMetrics
        val line = fm.descent - fm.ascent
        val left = labels.maxOf { paint.measureText(it) } + dp(12f)
        val right = width - dp(16f)
        val top = line + dp(8f)
        val bottom = height - line - dp(12f)
        if (right <= left || bottom <= top) return
        paint.color = context.getColor(R.color.mobile_muted)
        canvas.drawText("g", left, -fm.ascent, paint)
        labels.forEachIndexed { index, label ->
            val y = bottom - (bottom - top) * index / 2
            paint.color = context.getColor(R.color.chart_grid)
            paint.strokeWidth = dp(1f)
            canvas.drawLine(left, y, right, y, paint)
            paint.color = context.getColor(R.color.mobile_muted)
            canvas.drawText(label, left - dp(8f) - paint.measureText(label), y - (fm.ascent + fm.descent) / 2, paint)
        }
        listOf(-10, -5, 0).forEachIndexed { index, seconds ->
            val x = left + (right - left) * index / 2
            val text = String.format(Locale.ROOT, "%d s", seconds)
            val textX = (x - paint.measureText(text) / 2).coerceIn(0f, (width - paint.measureText(text)).coerceAtLeast(0f))
            canvas.drawText(text, textX, height - fm.descent, paint)
        }
        if (points.isEmpty()) return
        fun x(point: ScaleTrendWindow.Point) = left + (right - left) * (1 - (nowMs - point.atMs) / 10000f)
        fun y(point: ScaleTrendWindow.Point) = bottom - (bottom - top) * ((point.hundredthsGram / 100.0 - low) / (high - low)).toFloat()
        val save = canvas.save()
        canvas.clipRect(left, top - dp(3f), right + dp(3f), bottom + dp(3f))
        paint.color = context.getColor(R.color.mobile_accent)
        paint.strokeWidth = dp(2f)
        points.forEachIndexed { index, point ->
            val previous = points.getOrNull(index - 1)
            if (previous != null && previous.segment == point.segment)
                canvas.drawLine(x(previous), y(previous), x(point), y(point), paint)
            if (previous == null || previous.segment != point.segment || index == points.lastIndex)
                canvas.drawCircle(x(point), y(point), dp(2.5f), paint)
        }
        canvas.restoreToCount(save)
    }
}
