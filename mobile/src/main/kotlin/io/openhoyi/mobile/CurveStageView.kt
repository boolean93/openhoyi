package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View

/** A schematic of stage targets. It deliberately does not claim to be a timed pressure trace. */
internal class CurveStageView(context: Context) : View(context) {
    var targets: List<Int> = emptyList()
        set(value) { field = value; invalidate() }
    var compact: Boolean = false
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        fun dp(value: Float) = value * density
        val left = paddingLeft + dp(if (compact) 8f else 20f)
        val right = width - paddingRight - dp(if (compact) 8f else 20f)
        val top = paddingTop + dp(if (compact) 8f else 18f)
        val bottom = height - paddingBottom - dp(if (compact) 8f else 28f)
        if (right <= left || bottom <= top) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = context.getColor(R.color.chart_grid)
        for (fraction in if (compact) listOf(0f) else listOf(0f, .5f, 1f)) {
            val y = bottom - (bottom - top) * fraction
            canvas.drawLine(left, y, right, y, paint)
        }
        if (targets.isEmpty()) return
        val values = targets.take(4)
        val maximum = maxOf(1, values.maxOrNull() ?: 1)
        val path = Path()
        values.forEachIndexed { index, target ->
            val x = left + (right - left) * (index + .5f) / values.size
            val y = bottom - (bottom - top) * (target.coerceAtLeast(0).toFloat() / maximum)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = context.getColor(R.color.mobile_accent)
        paint.strokeWidth = dp(if (compact) 2f else 3f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, paint)
        if (compact) {
            paint.style = Paint.Style.FILL
            values.forEachIndexed { index, target ->
                val x = left + (right - left) * (index + .5f) / values.size
                val y = bottom - (bottom - top) * (target.coerceAtLeast(0).toFloat() / maximum)
                canvas.drawCircle(x, y, dp(2.5f), paint)
            }
            return
        }
        paint.style = Paint.Style.FILL
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        paint.textAlign = Paint.Align.CENTER
        values.forEachIndexed { index, target ->
            val x = left + (right - left) * (index + .5f) / values.size
            val y = bottom - (bottom - top) * (target.coerceAtLeast(0).toFloat() / maximum)
            canvas.drawCircle(x, y, dp(4f), paint)
            paint.color = context.getColor(R.color.mobile_muted)
            canvas.drawText("${index + 1} 段", x, height - paddingBottom - dp(5f), paint)
            paint.color = context.getColor(R.color.mobile_accent)
        }
    }
}
