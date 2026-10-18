package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Native sample adapter: same pressure projection, explicit proposed paints, no production switch. */
internal class StrictVisualSampleChart(context: Context,
    private val fixture: StrictVisualSampleFixtures.Fixture,
    private val palette: StrictVisualSampleTokens.Palette) : View(context) {
    var evidence = JSONArray()
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(value: Float) = StrictVisualSampleTokens.dp(context, value).toFloat()
    private fun paintJson() = JSONObject().put("colorArgb", String.format(Locale.ROOT, "#%08X", paint.color))
        .put("alpha", paint.alpha).put("strokeWidthPx", paint.strokeWidth).put("style", paint.style.name)
        .put("textSizePx", paint.textSize).put("fakeBold", paint.isFakeBoldText)
        .put("weight", if (Build.VERSION.SDK_INT >= 28) paint.typeface?.weight else JSONObject.NULL)
        .put("fontSha256", StrictVisualSampleTokens.FONT_SHA).put("textAlign", paint.textAlign.name)
    private fun label(canvas: Canvas, value: String, x: Float, baseline: Float, color: Int, align: Paint.Align, role: String) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color
        paint.textSize = StrictVisualSampleTokens.sp(context, 14)
        paint.typeface = TaskTypography.face(context, 400); paint.textAlign = align
        val bounds = Rect(); paint.getTextBounds(value, 0, value.length, bounds)
        val advance = paint.measureText(value)
        val origin = x - when (align) { Paint.Align.CENTER -> advance / 2; Paint.Align.RIGHT -> advance; else -> 0f }
        canvas.drawText(value, x, baseline, paint)
        evidence.put(JSONObject().put("operation", "text").put("role", role).put("text", value).put("xPx", x).put("baselinePx", baseline)
            .put("advancePx", advance).put("glyphBoundsPx", JSONArray(listOf(origin + bounds.left,
                baseline + bounds.top, origin + bounds.right, baseline + bounds.bottom)))
            .put("fontMetrics", JSONObject().put("top", paint.fontMetrics.top).put("bottom", paint.fontMetrics.bottom)
                .put("ascent", paint.fontMetrics.ascent).put("descent", paint.fontMetrics.descent))
            .put("paint", paintJson()))
    }
    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, strokeDp: Float) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color; paint.strokeWidth = dp(strokeDp)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x1, y1, x2, y2, paint)
        evidence.put(JSONObject().put("operation", "line").put("endpointsPx", JSONArray(listOf(x1, y1, x2, y2))).put("paint", paintJson()))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        evidence = JSONArray()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = StrictVisualSampleTokens.sp(context, 14); typeface = TaskTypography.face(context, 400) }
        val metrics = textPaint.fontMetrics
        fun ink(value: String) = Rect().also { textPaint.getTextBounds(value, 0, value.length, it) }
        val titleBaseline = -metrics.top
        val titleInkBottom = titleBaseline + ink("压力 / bar").bottom
        val tickBaselineOffset = -(metrics.ascent + metrics.descent) / 2
        val highestTickInkTopRelative = tickBaselineOffset + ink("12").top
        val stageBaseline = height - metrics.bottom
        val stageInkTop = (1..4).minOf { stageBaseline + ink("阶段 $it").top }
        val plot = RectF(dp(40f), maxOf(dp(32f), titleInkBottom + dp(8f) - highestTickInkTopRelative),
            width - dp(16f), minOf(height - dp(38f), stageInkTop - dp(8f)))
        check(plot.width() > 0 && plot.height() > 0) { "Sample chart has no plot area" }
        evidence.put(JSONObject().put("operation", "plotGeometry").put("projection", "CurveStageParameters.pressureFraction")
            .put("semanticId", "$tag/plot").put("plotBoundsPx", JSONArray(listOf(plot.left, plot.top, plot.right, plot.bottom)))
            .put("targetsTenthsBar", JSONArray(fixture.targetsTenthsBar)))
        evidence.put(JSONObject().put("operation", "labelBands").put("titleInkBottomPx", titleInkBottom)
            .put("highestTickInkTopPx", plot.top + highestTickInkTopRelative)
            .put("titleToHighestTickGapPx", plot.top + highestTickInkTopRelative - titleInkBottom)
            .put("stageInkTopPx", stageInkTop).put("plotToStageInkGapPx", stageInkTop - plot.bottom)
            .put("requiredGapPx", dp(8f)).put("minimumChartHeightPx", dp(160f)))
        label(canvas, "压力 / bar", 0f, titleBaseline, palette.muted, Paint.Align.LEFT, "axisTitle")
        val share = fixture.id == StrictVisualSampleFixtures.ids[1]
        val step = plot.width() / fixture.targetsTenthsBar.size
        if (share) for (index in fixture.targetsTenthsBar.indices) {
            paint.reset(); paint.color = palette.stageColors[index]; paint.alpha = (255 * .06f).toInt()
            val rect = RectF(plot.left + index * step, plot.top, plot.left + (index + 1) * step, plot.bottom)
            canvas.drawRect(rect, paint)
            evidence.put(JSONObject().put("operation", "stageBackground").put("requestedOpacity", .06)
                .put("boundsPx", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))).put("paint", paintJson()))
        }
        for (tick in listOf(0, 6, 12)) {
            val y = plot.bottom - plot.height() * tick / 12f
            line(canvas, plot.left, y, plot.right, y, palette.border, 1f)
            label(canvas, "$tick", plot.left - dp(8f), y - (metrics.ascent + metrics.descent) / 2, palette.muted, Paint.Align.RIGHT, "axisTick")
        }
        // Actual left/bottom axes and stage boundaries; grid strokes remain distinct from data strokes.
        for (index in 1 until fixture.targetsTenthsBar.size) {
            val x = plot.left + step * index
            line(canvas, x, plot.top, x, plot.bottom, palette.border, 1f)
        }
        line(canvas, plot.left, plot.top, plot.left, plot.bottom, palette.muted, 1f)
        line(canvas, plot.left, plot.bottom, plot.right, plot.bottom, palette.muted, 1f)
        fixture.targetsTenthsBar.forEachIndexed { index, target ->
            val color = if (share) palette.stageColors[index] else palette.pressure
            val center = plot.left + step * (index + .5f)
            val y = plot.bottom - plot.height() * CurveStageParameters.pressureFraction(target)
            line(canvas, center - step * (if (share) .5f else .34f), y,
                center + step * (if (share) .5f else .34f), y, color, 2f)
            paint.reset(); paint.isAntiAlias = true; paint.color = color
            canvas.drawCircle(center, y, dp(4f), paint)
            evidence.put(JSONObject().put("operation", "targetPoint").put("centerPx", JSONArray(listOf(center, y)))
                .put("radiusPx", dp(4f)).put("paint", paintJson()))
            val value = String.format(Locale.ROOT, "%.1f%s", target / 10.0, if (share) "" else " bar")
            label(canvas, value, center, y - dp(8f), color, Paint.Align.CENTER, "targetLabel")
            label(canvas, "阶段 ${index + 1}", center, stageBaseline, palette.muted, Paint.Align.CENTER, "stageLabel")
        }
    }
}
