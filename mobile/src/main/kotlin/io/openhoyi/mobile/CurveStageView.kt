package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** A schematic of stage targets. It deliberately does not claim to be a timed pressure trace. */
internal class CurveStageView(context: Context) : View(context) {
    var targets: List<Int> = emptyList()
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft + 32f
        val right = width - paddingRight - 16f
        val top = paddingTop + 18f
        val bottom = height - paddingBottom - 30f
        if (right <= left || bottom <= top) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = resources.displayMetrics.density
        paint.color = context.getColor(R.color.chart_grid)
        for (fraction in listOf(0f, .5f, 1f)) {
            val y = bottom - (bottom - top) * fraction
            canvas.drawLine(left, y, right, y, paint)
        }
        if (targets.isEmpty()) return
        val values = targets.take(4)
        val maximum = maxOf(1, values.maxOrNull() ?: 1)
        val path = Path()
        values.forEachIndexed { index, target ->
            val x = left + (right - left) * (index + .5f) / values.size
            val y = bottom - (bottom - top) * (target.toFloat() / maximum)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = context.getColor(R.color.mobile_accent)
        paint.strokeWidth = resources.displayMetrics.density * 3f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = resources.displayMetrics.scaledDensity * 12f
        values.forEachIndexed { index, target ->
            val x = left + (right - left) * (index + .5f) / values.size
            val y = bottom - (bottom - top) * (target.toFloat() / maximum)
            canvas.drawCircle(x, y, resources.displayMetrics.density * 4f, paint)
            paint.color = context.getColor(R.color.mobile_muted)
            canvas.drawText("${index + 1} 段", x - resources.displayMetrics.density * 15f,
                height - paddingBottom - 5f, paint)
            paint.color = context.getColor(R.color.mobile_accent)
        }
    }
}
