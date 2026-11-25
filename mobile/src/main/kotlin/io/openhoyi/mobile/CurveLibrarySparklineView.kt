package io.openhoyi.mobile

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Shared-scale targets, with adjoining bars in the library and an outline in compact previews. */
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
            CurveStageParameters.Unit.PRESSURE_BAR -> style.pressure
            CurveStageParameters.Unit.FLOW_ML_PER_SECOND -> style.flowMetric
            CurveStageParameters.Unit.RAW_UNKNOWN -> style.muted
        }
        fun y(stage: CurveStageParameters.Stage): Float {
            val limit = if (stage.unit == CurveStageParameters.Unit.PRESSURE_BAR) scale.pressureMaxBar
                else scale.flowMaxMlPerSecond
            return bottom - (bottom - top) * stage.target / 10f / limit
        }
        if (stageBars) {
            fun stageTop(stage: CurveStageParameters.Stage): Float {
                val minimumHeight = dp(if (stage.target == 0) 1f else 2f)
                return y(stage).coerceAtMost(bottom - minimumHeight).coerceAtLeast(top)
            }

            // Shrink the visible bar width independently of the spacing removed between stages.
            val barWidth = .8f * (step - minOf(dp(4f), step / 2f))
            val groupLeft = (left + right - barWidth * stages.size) / 2f
            paint.style = Paint.Style.FILL
            stages.forEachIndexed { index, stage ->
                val barLeft = groupLeft + barWidth * index
                val barRight = groupLeft + barWidth * (index + 1)
                val barTop = stageTop(stage)
                val ink = color(stage)
                paint.color = ColorUtils.setAlphaComponent(ink, 60)
                canvas.drawRect(barLeft, barTop, barRight, bottom, paint)

                val stroke = minOf(dp(1f), (bottom - barTop) / 4f, barWidth / 4f)
                paint.color = ink
                canvas.drawRect(barLeft, barTop, barRight, barTop + stroke, paint)
                canvas.drawRect(barLeft, bottom - stroke, barRight, bottom, paint)
                // The taller bar owns the shared edge. On a tie the bar on the right draws it.
                if (index == 0 || barTop <= stageTop(stages[index - 1])) {
                    canvas.drawRect(barLeft, barTop, barLeft + stroke, bottom, paint)
                }
                if (index == stages.lastIndex || barTop < stageTop(stages[index + 1])) {
                    canvas.drawRect(barRight - stroke, barTop, barRight, bottom, paint)
                }
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
