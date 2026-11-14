package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View

/** Equal-width stages, not time. Water labels make every quantity edit observable. */
internal class CurveEditorPreviewView(context: Context, private val style: TaskProductVisualStyle) : View(context) {
    var model = CurveEditorPreview.Model(emptyList())
        set(value) {
            field = value
            contentDescription = value.stages.mapIndexed { index, stage ->
                context.getString(R.string.profiles_stage, index + 1) + ": " +
                    (stage.target?.let { CurveDraftNumber.format(it, 1) + if (stage.flow) " mL/s" else " bar" } ?: "—") +
                    ", " + (stage.water?.let { CurveDraftNumber.format(it, 1) + " mL" } ?: "—")
            }.joinToString("; ")
            requestLayout(); invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = TaskTypography.face(context, 400) }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        paint.textSize = AppTextUnits.pixels(resources, 14f)
        val required = (paint.measureText("10 mL/s") + style.dp(16)) * model.stages.size + style.dp(84)
        setMeasuredDimension(maxOf(MeasureSpec.getSize(widthMeasureSpec), required.toInt()), MeasureSpec.getSize(heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        fun dp(n: Int) = style.dp(n).toFloat()
        paint.textSize = AppTextUnits.pixels(resources, 14f)
        val lineHeight = paint.fontSpacing
        val left = dp(40); val right = width - dp(44)
        val top = lineHeight + dp(12); val bottom = height - lineHeight * 2 - dp(16)
        if (right <= left || bottom <= top || model.stages.isEmpty()) return
        fun text(value: String, x: Float, y: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
            paint.style = Paint.Style.FILL; paint.color = color; paint.textAlign = align
            canvas.drawText(value, x, y, paint)
        }
        fun line(x: Float, y: Float, x2: Float, y2: Float, color: Int, thick: Boolean = false) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(if (thick) 3 else 1); paint.color = color
            canvas.drawLine(x, y, x2, y2, paint)
        }
        val hasPressure = model.stages.any { !it.flow }; val hasFlow = model.stages.any { it.flow }
        val flowMax = if (model.stages.filter { it.flow }.any { (it.target ?: 0) > 60 }) 10 else 6
        text(if (hasPressure) "bar" else "mL/s", left, lineHeight, style.muted, Paint.Align.LEFT)
        text(if (hasPressure) "12" else "$flowMax", left - dp(8), top + lineHeight / 3, style.muted, Paint.Align.RIGHT)
        text("0", left - dp(8), bottom, style.muted, Paint.Align.RIGHT)
        line(left, top, left, bottom, style.border); line(left, bottom, right, bottom, style.border)
        if (hasPressure && hasFlow) {
            line(right, top, right, bottom, style.border)
            text("mL/s", right, lineHeight, style.muted, Paint.Align.RIGHT)
            text("$flowMax", right + dp(8), top + lineHeight / 3, style.muted, Paint.Align.LEFT)
            text("0", right + dp(8), bottom, style.muted, Paint.Align.LEFT)
        }
        val step = (right - left) / model.stages.size
        model.stages.forEachIndexed { index, stage ->
            val x = left + step * index; val center = x + step / 2
            if (index > 0) {
                paint.pathEffect = DashPathEffect(floatArrayOf(dp(4), dp(3)), 0f)
                line(x, top, x, bottom, style.border); paint.pathEffect = null
            }
            val color = CurveEditorColors(context).ink(stage.flow)
            if (stage.target != null) {
                val max = if (stage.flow) flowMax * 10 else 120
                val y = bottom - (bottom - top) * stage.target / max
                line(x + dp(4), y, x + step - dp(4), y, color, true)
                // Near the axis ceiling, put the label below the target instead of across its line.
                val baseline = if (y - dp(8) + paint.fontMetrics.ascent >= top + dp(2)) y - dp(8)
                    else y + dp(8) - paint.fontMetrics.ascent
                text(CurveDraftNumber.format(stage.target, 1) + if (stage.flow) " mL/s" else " bar", center,
                    baseline, color)
            } else text("—", center, (top + bottom) / 2, style.muted)
            text(context.getString(R.string.profiles_stage, index + 1), center, bottom + lineHeight + dp(4), style.ink)
            text(stage.water?.let { CurveDraftNumber.format(it, 1) + " mL" } ?: "— mL", center,
                bottom + lineHeight * 2 + dp(6), style.muted)
        }
    }
}
