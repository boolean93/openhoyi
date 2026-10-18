package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View

/** A schematic of stage targets. It deliberately does not claim to be a timed pressure trace. */
internal class CurveStageView(context: Context) : View(context) {
    var targets: List<Int> = emptyList()
        set(value) { field = value; invalidate() }
    var compact: Boolean = false
        set(value) { field = value; invalidate() }
    var pressureColor: Boolean = false
        set(value) { field = value; invalidate() }
    private fun stageColor(index: Int): Int = if (pressureColor)
        context.getColor(R.color.chart_pressure) else CurveStagePalette.color(context, index)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = TaskTypography.face(context) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        fun dp(value: Float) = value * density
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        val metrics = paint.fontMetrics
        val labelHeight = metrics.descent - metrics.ascent
        val left = paddingLeft + if (compact) dp(8f) else paint.measureText("12") + dp(16f)
        val right = width - paddingRight - dp(if (compact) 8f else 20f)
        val top = paddingTop + if (compact) dp(8f) else labelHeight + dp(16f)
        val bottom = height - paddingBottom - if (compact) dp(8f) else labelHeight + dp(16f)
        if (right <= left || bottom <= top) return
        val values = targets.take(4)
        if (!compact && !pressureColor && values.isNotEmpty() && values.all { it in 0..120 }) {
            paint.style = Paint.Style.FILL
            values.indices.forEach { index ->
                paint.color = stageColor(index)
                paint.alpha = 12
                canvas.drawRect(left + (right - left) * index / values.size, top,
                    left + (right - left) * (index + 1) / values.size, bottom, paint)
            }
            paint.alpha = 255
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = context.getColor(R.color.chart_grid)
        for (fraction in if (compact) listOf(0f) else listOf(0f, .25f, .5f, .75f, 1f)) {
            val y = bottom - (bottom - top) * fraction
            canvas.drawLine(left, y, right, y, paint)
        }
        if (!compact) {
            paint.style = Paint.Style.FILL
            paint.color = context.getColor(R.color.mobile_muted)
            paint.textAlign = Paint.Align.RIGHT
            for (tick in listOf(0, 3, 6, 9, 12)) {
                val y = bottom - (bottom - top) * tick / 12f
                canvas.drawText(tick.toString(), left - dp(8f), y - (metrics.ascent + metrics.descent) / 2, paint)
            }
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("bar", left, paddingTop + dp(8f) - metrics.ascent, paint)
        }
        if (targets.isEmpty()) {
            if(compact) {
                paint.style=Paint.Style.FILL
                paint.color=context.getColor(R.color.mobile_muted)
                paint.textSize=dp(16f);paint.textAlign=Paint.Align.CENTER
                canvas.drawText("—",width/2f,height/2f,paint)
            }
            return
        }
        if(values.any {it !in 0..120}) return
        paint.strokeWidth = dp(if (compact) 2f else 3f)
        paint.strokeCap = Paint.Cap.ROUND
        values.forEachIndexed {index,target->
            paint.color = stageColor(index)
            val start=left+(right-left)*(index+.12f)/values.size
            val end=left+(right-left)*(index+.88f)/values.size
            val y=bottom-(bottom-top)*CurveStageParameters.pressureFraction(target)
            canvas.drawLine(start,y,end,y,paint)
        }
        if (compact) {
            paint.style = Paint.Style.FILL
            values.forEachIndexed { index, target ->
                paint.color = stageColor(index)
                val x = left + (right - left) * (index + .5f) / values.size
                val y = bottom - (bottom - top) * CurveStageParameters.pressureFraction(target)
                canvas.drawCircle(x, y, dp(2.5f), paint)
            }
            return
        }
        paint.style = Paint.Style.FILL
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        paint.textAlign = Paint.Align.CENTER
        values.forEachIndexed { index, target ->
            paint.color = stageColor(index)
            val x = left + (right - left) * (index + .5f) / values.size
            val y = bottom - (bottom - top) * CurveStageParameters.pressureFraction(target)
            canvas.drawCircle(x, y, dp(4f), paint)
            canvas.drawText(java.lang.String.format(java.util.Locale.ROOT,"%.1f bar",target/10.0),x,
                (y-dp(8f)).coerceAtLeast(paddingTop+dp(8f)-metrics.ascent),paint)
            paint.color = context.getColor(R.color.mobile_muted)
            canvas.drawText(context.getString(R.string.chart_stage, (index + 1).toString()), x,
                height - paddingBottom - dp(8f) - metrics.descent, paint)
        }
    }
}
