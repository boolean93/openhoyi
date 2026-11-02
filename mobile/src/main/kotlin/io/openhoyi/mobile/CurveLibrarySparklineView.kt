package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Shared-scale target outline: equal stage widths, with a gap at each unit change. */
internal class CurveLibrarySparklineView(context: Context, private val style: TaskProductVisualStyle) : View(context) {
    var preview = CurveStageParameters.Preview(emptyList())
        set(value) { field = value; invalidate() }
    var scale = CurveStageParameters.SharedScale(12f, 6f)
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(value: Float) = value * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!preview.renderable) return
        val stages = preview.stages
        val left = paddingLeft + dp(3f)
        val right = width - paddingRight - dp(3f)
        val top = paddingTop + dp(4f)
        val bottom = height - paddingBottom - dp(4f)
        if (left >= right || top >= bottom) return
        val step = (right - left) / stages.size
        fun color(stage: CurveStageParameters.Stage) = when (stage.unit) {
            CurveStageParameters.Unit.PRESSURE_BAR -> style.pressure
            CurveStageParameters.Unit.FLOW_ML_PER_SECOND -> style.accent
            CurveStageParameters.Unit.RAW_UNKNOWN -> style.muted
        }
        fun y(stage: CurveStageParameters.Stage): Float {
            val limit = if (stage.unit == CurveStageParameters.Unit.PRESSURE_BAR) scale.pressureMaxBar
                else scale.flowMaxMlPerSecond
            return bottom - (bottom - top) * stage.target / 10f / limit
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f); paint.strokeCap = Paint.Cap.ROUND
        stages.forEachIndexed { index, stage ->
            val x1 = left + step * index
            val x2 = x1 + step
            val center = (x1 + x2) / 2f
            val level = y(stage)
            paint.color = color(stage)
            canvas.drawLine(if (index > 0 && stages[index - 1].unit != stage.unit) x1 + dp(3f) else x1,
                level, if (index < stages.lastIndex && stages[index + 1].unit != stage.unit) x2 - dp(3f) else x2,
                level, paint)
            if (index > 0 && stages[index - 1].unit == stage.unit) {
                canvas.drawLine(x1, y(stages[index - 1]), x1, level, paint)
            }
            paint.style = Paint.Style.FILL
            canvas.drawCircle(if (index == stages.lastIndex) x2 else center, level, dp(2.5f), paint)
            paint.style = Paint.Style.STROKE
        }
    }
}
