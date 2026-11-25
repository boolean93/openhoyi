package io.openhoyi.mobile

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Shared-scale targets, with separate bars in the library and an outline in compact previews. */
internal class CurveLibrarySparklineView(
    context: Context,
    private val style: TaskProductVisualStyle,
    private val compact: Boolean = false,
    private val stageBars: Boolean = false,
) : View(context) {
    var preview = CurveStageParameters.Preview(emptyList())
        set(value) { field = value; invalidate() }
    var scale = CurveStageParameters.SharedScale(12f, 6f)
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Home's translucent light tiles may be over a dark picture. Keep the unit
    // hues while increasing their contrast there; never alter library-chart ink.
    private val lightCompact = compact && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
        Configuration.UI_MODE_NIGHT_YES
    private fun compactInk(color: Int) = if (lightCompact) ColorUtils.blendARGB(color, Color.BLACK, .14f) else color
    private fun dp(value: Float) = value * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!preview.renderable) return
        if (compact && width < dp(8f)) return
        val stages = preview.stages
        val horizontal = dp(if (compact) 1.8f else 3f)
        val vertical = dp(if (compact) 1.5f else 4f)
        val left = paddingLeft + horizontal
        val right = width - paddingRight - horizontal
        val top = paddingTop + vertical
        val bottom = height - paddingBottom - vertical
        if (left >= right || top >= bottom) return
        val step = (right - left) / stages.size
        fun color(stage: CurveStageParameters.Stage) = when (stage.unit) {
            CurveStageParameters.Unit.PRESSURE_BAR -> if (stageBars) style.libraryPressureBar else style.pressure
            CurveStageParameters.Unit.FLOW_ML_PER_SECOND -> if (stageBars) style.libraryFlowBar else style.flowMetric
            CurveStageParameters.Unit.RAW_UNKNOWN -> style.muted
        }
        fun y(stage: CurveStageParameters.Stage): Float {
            val limit = if (stage.unit == CurveStageParameters.Unit.PRESSURE_BAR) scale.pressureMaxBar
                else scale.flowMaxMlPerSecond
            return bottom - (bottom - top) * stage.target / 10f / limit
        }
        if (stageBars) {
            val halfGap = minOf(dp(2f), step / 4f)
            paint.style = Paint.Style.FILL
            stages.forEachIndexed { index, stage ->
                val slotLeft = left + step * index
                val minimumHeight = dp(if (stage.target == 0) 1f else 2f)
                val barTop = y(stage).coerceAtMost(bottom - minimumHeight).coerceAtLeast(top)
                paint.color = color(stage)
                canvas.drawRect(slotLeft + halfGap, barTop, slotLeft + step - halfGap, bottom, paint)
            }
            return
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(if (compact) 1.35f else 2f); paint.strokeCap = Paint.Cap.ROUND
        stages.forEachIndexed { index, stage ->
            val x1 = left + step * index
            val x2 = x1 + step
            val center = (x1 + x2) / 2f
            val level = y(stage)
            paint.color = compactInk(color(stage))
            canvas.drawLine(if (index > 0 && stages[index - 1].unit != stage.unit) x1 + horizontal else x1,
                level, if (index < stages.lastIndex && stages[index + 1].unit != stage.unit) x2 - horizontal else x2,
                level, paint)
            if (index > 0 && stages[index - 1].unit == stage.unit) {
                canvas.drawLine(x1, y(stages[index - 1]), x1, level, paint)
            }
            paint.style = Paint.Style.FILL
            canvas.drawCircle(if (index == stages.lastIndex) x2 else center, level, dp(if (compact) 1.3f else 2.5f), paint)
            paint.style = Paint.Style.STROKE
        }
    }
}
