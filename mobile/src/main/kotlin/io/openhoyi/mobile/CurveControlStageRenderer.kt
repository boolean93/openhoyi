package io.openhoyi.mobile

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Equal-width stage targets; neither stage width nor connector implies elapsed time. */
internal class CurveControlStageRenderer(private val view: CurveStageView) {
    var evidence = JSONArray()
        private set

    fun draw(canvas: Canvas, preview: CurveStageParameters.Preview, style: TaskProductVisualStyle?, compact: Boolean) {
        evidence = JSONArray()
        if (!preview.renderable || !preview.hasFlow) return
        val density = view.resources.displayMetrics.density
        fun dp(value: Float) = value * density
        val left = view.paddingLeft + dp(if (compact) 6f else 48f)
        val right = view.width - view.paddingRight - dp(if (compact) 6f else if (preview.mixed) 52f else 18f)
        val top = view.paddingTop + dp(if (compact) 7f else 38f)
        val bottom = view.height - view.paddingBottom - dp(if (compact) 7f else 38f)
        if (right <= left || bottom <= top) return
        val plot = RectF(left, top, right, bottom)
        evidence.put(JSONObject().put("operation", "plotGeometry")
            .put("semanticId", "${view.tag}/plot").put("xAxis", "stage")
            .put("pressureAxisMaxBar", if (preview.hasPressure) 12 else JSONObject.NULL)
            .put("flowAxisMaxMlPerSecond", preview.flowAxisMax.toDouble())
            .put("plotBoundsPx", JSONArray(listOf(left, top, right, bottom))))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TaskTypography.face(view.context, 400)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                if (compact) 9f else 14f, view.resources.displayMetrics)
        }
        val muted = style?.muted ?: view.context.getColor(R.color.mobile_muted)
        val border = style?.border ?: view.context.getColor(R.color.chart_grid)
        val pressure = style?.pressure ?: view.context.getColor(R.color.chart_pressure)
        val flow = style?.accent ?: view.context.getColor(R.color.mobile_accent)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, dashed: Boolean, role: String) {
            paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(if (role == "target") 2.5f else 1f)
            paint.pathEffect = if (dashed) DashPathEffect(floatArrayOf(dp(5f), dp(3f)), 0f) else null
            canvas.drawLine(x1, y1, x2, y2, paint)
            paint.pathEffect = null
            evidence.put(JSONObject().put("operation", "line").put("role", role).put("dashed", dashed)
                .put("endpointsPx", JSONArray(listOf(x1, y1, x2, y2))))
        }
        line(left, bottom, right, bottom, muted, false, "axis")
        line(left, top, left, bottom, muted, false, "axis")
        if (preview.mixed) line(right, top, right, bottom, muted, false, "axis")
        line(left, (top + bottom) / 2f, right, (top + bottom) / 2f, border, false, "grid")
        val step = plot.width() / preview.stages.size
        for (index in 1 until preview.stages.size) {
            val x = left + step * index
            line(x, top, x, bottom, border, false, "stageBoundary")
        }
        if (!compact) {
            paint.style = Paint.Style.FILL; paint.color = muted; paint.textAlign = Paint.Align.LEFT
            val baseline = view.paddingTop + dp(20f)
            canvas.drawText(if (preview.hasPressure) "bar" else "mL/s", left, baseline, paint)
            if (preview.mixed) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("mL/s", right, baseline, paint)
            }
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(if (preview.hasPressure) "12" else String.format(Locale.ROOT, "%.0f", preview.flowAxisMax),
                left - dp(6f), top + dp(5f), paint)
            canvas.drawText("0", left - dp(6f), bottom + dp(5f), paint)
            if (preview.mixed) {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(String.format(Locale.ROOT, "%.0f", preview.flowAxisMax), right + dp(6f), top + dp(5f), paint)
                canvas.drawText("0", right + dp(6f), bottom + dp(5f), paint)
            }
        }
        preview.stages.forEachIndexed { index, stage ->
            val isFlow = stage.unit == CurveStageParameters.Unit.FLOW_ML_PER_SECOND
            val max = if (isFlow) preview.flowAxisMax else 12f
            val quantity = stage.target / 10f
            val y = bottom - plot.height() * quantity / max
            val center = left + step * (index + .5f)
            val color = if (isFlow) flow else pressure
            line(center - step * .28f, y, center + step * .28f, y, color, isFlow, "target")
            paint.style = Paint.Style.FILL; paint.color = color
            canvas.drawCircle(center, y, dp(if (compact) 2.5f else 4f), paint)
            val unit = if (isFlow) "mL/s" else "bar"
            evidence.put(JSONObject().put("operation", "stageTarget").put("index", index)
                .put("targetTenths", stage.target).put("unit", unit).put("axisMax", max.toDouble())
                .put("centerPx", JSONArray(listOf(center, y))))
            if (!compact) {
                paint.textAlign = Paint.Align.CENTER; paint.color = color
                val value = String.format(Locale.ROOT, "%.1f %s", quantity, unit)
                canvas.drawText(value, center, (y - dp(8f)).coerceAtLeast(top + dp(16f)), paint)
                paint.color = muted
                canvas.drawText(view.context.getString(R.string.profiles_stage, index + 1), center,
                    view.height - view.paddingBottom - dp(10f), paint)
            }
        }
    }
}
