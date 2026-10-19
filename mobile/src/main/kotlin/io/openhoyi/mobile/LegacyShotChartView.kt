package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max

/** Only the two legacy series with established physical units are charted. */
class LegacyShotChartView(context: Context) : View(context) {
    internal var visualStyle: LegacyVisualStyle? = null
        set(value) { field = value; visualRenderer = value?.let { LegacyChartRenderer(context, it) }; requestLayout(); invalidate() }
    private var visualRenderer: LegacyChartRenderer? = null
    internal var visualEvidence = org.json.JSONObject()
        private set
    internal fun minimumVisualHeight(width: Int, plotDp: Int = 160): Int =
        visualRenderer?.minimumHeight(width, plotDp) ?: 0
    var points: List<LegacyPoint> = emptyList()
        set(value) { field = value; invalidate() }
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_axis); strokeWidth = dp(1f)
    }
    private val pressure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_pressure); strokeWidth = dp(2.5f); style = Paint.Style.STROKE
    }
    private val flow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_water); strokeWidth = dp(2.5f); style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(8f), dp(6f)), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = TaskTypography.face(context)
        color = context.getColor(R.color.chart_label); textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        visualRenderer?.let { visualEvidence = it.draw(canvas, width, height, points); return }
        canvas.drawColor(context.getColor(R.color.mobile_surface))
        if (points.isEmpty()) { canvas.drawText(context.getString(R.string.chart_legacy_empty), dp(16f), height / 2f, label); return }
        val maxTime = max(1.0, points.last().seconds)
        val maxPressure = max(12.0, points.maxOf(LegacyPoint::pressureBar))
        val maxFlow = max(6.0, points.maxOf(LegacyPoint::waterFlow))
        fun number(value: Double) = "%.1f".format(value)
        val lineHeight = label.fontMetrics.descent - label.fontMetrics.ascent
        val left = max(dp(42f), label.measureText(number(maxPressure)) + dp(12f))
        val right = width - max(dp(42f), label.measureText(number(maxFlow)) + dp(12f))
        val top = max(dp(72f), lineHeight * 3 + dp(24f))
        val bottom = height - max(dp(30f), lineHeight + dp(12f))
        if (right <= left || bottom <= top) return
        val firstBaseline = dp(4f) - label.fontMetrics.ascent
        val secondBaseline = firstBaseline + lineHeight + dp(4f)
        canvas.drawLine(left, firstBaseline - lineHeight / 3, left + dp(24f), firstBaseline - lineHeight / 3, pressure)
        canvas.drawLine(left, secondBaseline - lineHeight / 3, left + dp(24f), secondBaseline - lineHeight / 3, flow)
        canvas.drawText(context.getString(R.string.chart_legacy_pressure, number(maxPressure)), left + dp(32f), firstBaseline, label)
        canvas.drawText(context.getString(R.string.chart_legacy_water, number(maxFlow)), left + dp(32f), secondBaseline, label)
        canvas.drawText("bar", left, top - dp(8f), label)
        canvas.drawText("ml/s", right - label.measureText("ml/s"), top - dp(8f), label)
        val grid = Paint(axis).apply { alpha = 55 }
        val divisions = ((bottom - top) / (lineHeight + dp(8f))).toInt().coerceIn(1, 6)
        for (index in 0..divisions) {
            val fraction = index.toFloat() / divisions
            val y = bottom - (bottom - top) * fraction
            canvas.drawLine(left, y, right, y, grid)
            val baseline = y - (label.fontMetrics.ascent + label.fontMetrics.descent) / 2
            val pressureTick = number(maxPressure * fraction)
            canvas.drawText(pressureTick, left - dp(6f) - label.measureText(pressureTick), baseline, label)
            canvas.drawText(number(maxFlow * fraction), right + dp(6f), baseline, label)
        }
        canvas.drawLine(left, top, left, bottom, axis)
        canvas.drawLine(right, top, right, bottom, axis)
        canvas.drawLine(left, bottom, right, bottom, axis)
        val timeDivisions = ((right - left) / (label.measureText(number(maxTime) + " s") + dp(16f))).toInt().coerceIn(1, 4)
        for (index in 0..timeDivisions) {
            val x = left + (right - left) * index / timeDivisions
            canvas.drawLine(x, top, x, bottom, grid)
            val tick = number(maxTime * index / timeDivisions) + " s"
            val textX = (x - label.measureText(tick) / 2).coerceIn(0f, (width - label.measureText(tick)).coerceAtLeast(0f))
            canvas.drawText(tick, textX, bottom + dp(6f) - label.fontMetrics.ascent, label)
        }
        fun plot(paint: Paint, maximum: Double, value: (LegacyPoint) -> Double) {
            path.reset()
            points.forEachIndexed { index, point ->
                val x = left + (right - left) * (point.seconds / maxTime).toFloat()
                val y = bottom - (bottom - top) * (value(point) / maximum).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
        }
        plot(pressure, maxPressure, LegacyPoint::pressureBar)
        plot(flow, maxFlow, LegacyPoint::waterFlow)
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
}

/** M7-only Paint. Legacy constructor/default draw remains byte-for-byte on its original path. */
private class LegacyChartRenderer(private val context: Context, private val style: LegacyVisualStyle) {
    private val palette = style.palette
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = TaskTypography.face(context, 400); textSize = palette.sp(14)
        isFakeBoldText = false; color = palette.muted
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.border; strokeWidth = dp(1f) }
    private val pressure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.pressure; strokeWidth = dp(2f); this.style = Paint.Style.STROKE
    }
    private val flow = Paint(pressure).apply {
        color = this@LegacyChartRenderer.style.water; pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(9f), dp(5f)), 0f)
    }
    private val line get() = label.fontMetrics.descent - label.fontMetrics.ascent
    private fun dp(value: Float) = value * context.resources.displayMetrics.density
    private fun number(value: Double) = String.format(java.util.Locale.ROOT, "%.1f", value)
    private val pressureLegend get() = context.getString(R.string.legacy_visual_pressure)
    private val flowLegend get() = context.getString(R.string.legacy_visual_flow)
    private fun stacked(width: Int) = label.measureText(pressureLegend) + label.measureText(flowLegend) + dp(112f) > width
    private fun top(width: Int) = line * (if (stacked(width)) 3 else 2) + dp(24f)
    private val bottom get() = line + dp(20f)
    fun minimumHeight(width: Int, plotDp: Int) = kotlin.math.ceil(top(width) + bottom + dp(plotDp.toFloat())).toInt()

    fun draw(canvas: Canvas, width: Int, height: Int, points: List<LegacyPoint>): org.json.JSONObject {
        val operations = org.json.JSONArray()
        fun color(value: Int) = String.format(java.util.Locale.ROOT, "#%08X", value)
        fun text(value: String, x: Float, baseline: Float, role: String, ink: Int = palette.muted) {
            label.color = ink
            val bounds = android.graphics.Rect(); label.getTextBounds(value, 0, value.length, bounds)
            canvas.drawText(value, x, baseline, label)
            operations.put(org.json.JSONObject().put("operation", "text").put("role", role).put("text", value)
                .put("positionPx", org.json.JSONArray(listOf(x, baseline)))
                .put("glyphBoundsPx", org.json.JSONArray(listOf(x + bounds.left, baseline + bounds.top, x + bounds.right, baseline + bounds.bottom)))
                .put("textSizePx", label.textSize).put("weight", 400).put("fakeBold", label.isFakeBoldText)
                .put("colorArgb", color(ink)).put("alpha", label.alpha)
                .put("fontAssetSha256", if (context.resources.configuration.locales[0].language == "zh") TaskProductVisualStyle.FONT_SHA else org.json.JSONObject.NULL)
                .put("typefaceSource", "TaskTypography.face(context,400)")
                .put("fontIdentityStatus", "candidate_asset_association_not_platform_glyph_identity")
                .put("paintTypefaceWeight", if (android.os.Build.VERSION.SDK_INT >= 28) label.typeface.weight else org.json.JSONObject.NULL)
                .put("fontMetrics", org.json.JSONObject().put("ascent", label.fontMetrics.ascent).put("descent", label.fontMetrics.descent)))
        }
        canvas.drawColor(palette.surface)
        val maxTime = max(1.0, points.lastOrNull()?.seconds ?: 1.0)
        val maxPressure = max(12.0, points.maxOfOrNull(LegacyPoint::pressureBar) ?: 12.0)
        val maxFlow = max(6.0, points.maxOfOrNull(LegacyPoint::waterFlow) ?: 6.0)
        val left = max(dp(42f), label.measureText(number(maxPressure)) + dp(12f))
        val right = width - max(dp(42f), label.measureText(number(maxFlow)) + dp(12f))
        val box = android.graphics.RectF(left, top(width), right, height - bottom)
        val evidence = org.json.JSONObject().put("plotBoundsPx", org.json.JSONArray(listOf(box.left, box.top, box.right, box.bottom)))
            .put("plotHeightDp", box.height() / context.resources.displayMetrics.density)
            .put("axisAndLegendHeightPx", top(width) + bottom)
            .put("pressureArgb", color(pressure.color)).put("waterArgb", color(flow.color))
            .put("operations", operations).put("pointCount", points.size)
        if (box.width() <= 0 || box.height() <= 0) return evidence
        val baseline = dp(4f) - label.fontMetrics.ascent
        fun legend(value: String, x: Float, y: Float, paint: Paint) {
            canvas.drawLine(x, y - line / 3, x + dp(24f), y - line / 3, paint)
            text(value, x + dp(32f), y, "legend", paint.color)
        }
        legend(pressureLegend, left, baseline, pressure)
        legend(flowLegend, if (stacked(width)) left else right - label.measureText(flowLegend) - dp(32f),
            if (stacked(width)) baseline + line + dp(4f) else baseline, flow)
        text("bar", left, box.top - dp(8f), "pressure-unit", pressure.color)
        text("ml/s", right - label.measureText("ml/s"), box.top - dp(8f), "flow-unit", flow.color)
        val divisions = (box.height() / (line + dp(8f))).toInt().coerceIn(1, 6)
        for (index in 0..divisions) {
            val fraction = index.toFloat() / divisions
            val y = box.bottom - box.height() * fraction
            canvas.drawLine(left, y, right, y, grid)
            val tickBaseline = y - (label.fontMetrics.ascent + label.fontMetrics.descent) / 2
            val tick = number(maxPressure * fraction)
            text(tick, left - dp(8f) - label.measureText(tick), tickBaseline, "pressure-tick", pressure.color)
            text(number(maxFlow * fraction), right + dp(8f), tickBaseline, "flow-tick", flow.color)
        }
        canvas.drawLine(left, box.top, left, box.bottom, grid)
        canvas.drawLine(right, box.top, right, box.bottom, grid)
        val count = (box.width() / (label.measureText(number(maxTime) + " s") + dp(16f))).toInt().coerceIn(1, 4)
        for (index in 0..count) {
            val x = left + box.width() * index / count
            canvas.drawLine(x, box.top, x, box.bottom, grid)
            val tick = number(maxTime * index / count) + " s"
            val tickX = (x - label.measureText(tick) / 2).coerceIn(0f, (width - label.measureText(tick)).coerceAtLeast(0f))
            text(tick, tickX, box.bottom + dp(8f) - label.fontMetrics.ascent, "time-tick")
        }
        canvas.save(); canvas.clipRect(box)
        fun plot(paint: Paint, maximum: Double, value: (LegacyPoint) -> Double) {
            val path = Path(); val vertices = org.json.JSONArray()
            points.forEachIndexed { index, point ->
                val x = left + box.width() * (point.seconds / maxTime).toFloat()
                val y = box.bottom - box.height() * (value(point) / maximum).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                vertices.put(org.json.JSONArray(listOf(x, y)))
            }
            canvas.drawPath(path, paint)
            if (points.size == 1) {
                val point = points.first(); val fill = Paint(paint).apply { this.style = Paint.Style.FILL; pathEffect = null }
                canvas.drawCircle(left + box.width() * (point.seconds / maxTime).toFloat(),
                    box.bottom - box.height() * (value(point) / maximum).toFloat(), dp(2f), fill)
            }
            operations.put(org.json.JSONObject().put("operation", "path").put("colorArgb", color(paint.color))
                .put("verticesPx", vertices).put("strokeWidthPx", paint.strokeWidth))
        }
        plot(pressure, maxPressure, LegacyPoint::pressureBar)
        plot(flow, maxFlow, LegacyPoint::waterFlow)
        canvas.restore()
        if (points.isEmpty()) text(context.getString(R.string.chart_legacy_empty), left + dp(8f), box.centerY(), "empty")
        return evidence
    }
}
