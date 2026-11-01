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
        .put("alpha", paint.alpha).put("strokeWidthPx", paint.strokeWidth).put("strokeCap", paint.strokeCap.name).put("style", paint.style.name)
        .put("textSizePx", paint.textSize).put("fakeBold", paint.isFakeBoldText)
        .put("weight", if (Build.VERSION.SDK_INT >= 28) paint.typeface?.weight else JSONObject.NULL)
        .put("fontSha256", StrictVisualSampleTokens.FONT_SHA).put("textAlign", paint.textAlign.name)
    private fun label(canvas: Canvas, value: String, x: Float, baseline: Float, color: Int, align: Paint.Align, role: String, targetIndex: Int? = null) {
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
            .put("paint", paintJson()).apply { if (targetIndex != null) put("targetIndex", targetIndex) })
    }
    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, strokeDp: Float, role: String = "data", gridId: String? = null) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color; paint.strokeWidth = dp(strokeDp)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x1, y1, x2, y2, paint)
        evidence.put(JSONObject().put("operation", "line").put("role", role)
            .put("endpointsPx", JSONArray(listOf(x1, y1, x2, y2))).put("paint", paintJson())
            .apply { if (gridId != null) put("gridId", gridId) })
    }
    private data class TargetLabel(val index: Int, val value: String, val x: Float,
        val baseline: Float, val color: Int, val glyph: RectF, val protection: RectF)
    private fun boundsJson(rect: RectF) = JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))
    /** Subtract protection intervals from actual grid strokes; axes/data/backgrounds never use this path. */
    private fun grid(canvas: Canvas, id: String, x1: Float, y1: Float, x2: Float, y2: Float, targets: List<TargetLabel>) {
        val horizontal = y1 == y2
        require(horizontal && x1 < x2 || x1 == x2 && y1 < y2)
        val strokeWidth = dp(1f)
        val halfStroke = strokeWidth / 2
        val start = if (horizontal) x1 else y1
        val end = if (horizontal) x2 else y2
        val cross = if (horizontal) y1 else x1
        val excluded = mutableListOf<Int>()
        val intervals = targets.mapNotNull { target ->
            val rect = target.protection
            val perpendicularStart = (if (horizontal) rect.top else rect.left) - halfStroke
            val perpendicularEnd = (if (horizontal) rect.bottom else rect.right) + halfStroke
            if (cross <= perpendicularStart || cross >= perpendicularEnd) return@mapNotNull null
            val low = maxOf(start, (if (horizontal) rect.left else rect.top) - halfStroke)
            val high = minOf(end, (if (horizontal) rect.right else rect.bottom) + halfStroke)
            if (high <= low) null else { excluded.add(target.index); low to high }
        }.sortedBy { it.first }
        val visible = mutableListOf<List<Float>>()
        var cursor = start
        fun append(low: Float, high: Float) {
            if (high > low) visible.add(if (horizontal) listOf(low, y1, high, y1) else listOf(x1, low, x1, high))
        }
        intervals.forEach { (low, high) ->
            if (low > cursor) append(cursor, low)
            cursor = maxOf(cursor, high)
        }
        append(cursor, end)
        evidence.put(JSONObject().put("operation", "gridSegmentPlan").put("gridId", id)
            .put("originalEndpointsPx", JSONArray(listOf(x1, y1, x2, y2)))
            .put("visibleSegmentsPx", JSONArray(visible.map { JSONArray(it) }))
            .put("strokeWidthPx", strokeWidth).put("strokeCap", Paint.Cap.BUTT.name)
            .put("strokeGuardPx", halfStroke).put("excludedProtectionIndices", JSONArray(excluded))
            .put("measurement", "actual_drawLine_segments; original_is_audit_only; half_stroke_guard_keeps_visible_ink_outside_2dp_protection"))
        visible.forEach { segment -> line(canvas, segment[0], segment[1], segment[2], segment[3], palette.border, 1f, "grid", id) }
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
        // Round the plot inward to whole physical pixels, leaving the label bands
        // outward of it. This guarantees the minimum gap rather than tolerating
        // a Float subtraction that produces slightly less than the required 8dp.
        val plot = RectF(dp(40f), kotlin.math.ceil(maxOf(dp(32f), titleInkBottom + dp(8f) - highestTickInkTopRelative)),
            width - dp(16f), kotlin.math.floor(minOf(height - dp(38f), stageInkTop - dp(8f))))
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
        // Same Paint, formatting, alignment and coordinates used by the eventual drawText calls.
        // Keep these boxes before grid drawing; no opaque eraser ever touches the stage backgrounds.
        val protectionPadding = 2f * resources.displayMetrics.density
        val targetLabels = fixture.targetsTenthsBar.mapIndexed { index, target ->
            val value = String.format(Locale.ROOT, "%.1f%s", target / 10.0, if (share) "" else " bar")
            val center = plot.left + step * (index + .5f)
            val y = plot.bottom - plot.height() * CurveStageParameters.pressureFraction(target)
            val baseline = y - dp(8f)
            val local = ink(value)
            val origin = center - textPaint.measureText(value) / 2
            val glyph = RectF(origin + local.left, baseline + local.top, origin + local.right, baseline + local.bottom)
            val protection = RectF(glyph).apply { inset(-protectionPadding, -protectionPadding) }
            TargetLabel(index, value, center, baseline, if (share) palette.stageColors[index] else palette.pressure, glyph, protection)
        }
        targetLabels.forEach { target ->
            evidence.put(JSONObject().put("operation", "targetLabelProtection").put("targetIndex", target.index)
                .put("text", target.value).put("glyphBoundsPx", boundsJson(target.glyph))
                .put("protectedBoundsPx", boundsJson(target.protection)).put("paddingDp", 2)
                .put("paddingPx", protectionPadding).put("measurement", "same_actual_Paint_getTextBounds_and_measureText_as_targetLabel_draw"))
        }
        if (share) for (index in fixture.targetsTenthsBar.indices) {
            paint.reset(); paint.color = palette.stageColors[index]; paint.alpha = (255 * .06f).toInt()
            val rect = RectF(plot.left + index * step, plot.top, plot.left + (index + 1) * step, plot.bottom)
            canvas.drawRect(rect, paint)
            evidence.put(JSONObject().put("operation", "stageBackground").put("requestedOpacity", .06)
                .put("boundsPx", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))).put("paint", paintJson()))
        }
        for (tick in listOf(0, 6, 12)) {
            val y = plot.bottom - plot.height() * tick / 12f
            grid(canvas, "horizontal-$tick", plot.left, y, plot.right, y, targetLabels)
            label(canvas, "$tick", plot.left - dp(8f), y - (metrics.ascent + metrics.descent) / 2, palette.muted, Paint.Align.RIGHT, "axisTick")
        }
        // Actual left/bottom axes and stage boundaries; grid strokes remain distinct from data strokes.
        for (index in 1 until fixture.targetsTenthsBar.size) {
            val x = plot.left + step * index
            grid(canvas, "stage-$index", x, plot.top, x, plot.bottom, targetLabels)
        }
        line(canvas, plot.left, plot.top, plot.left, plot.bottom, palette.muted, 1f, "axis")
        line(canvas, plot.left, plot.bottom, plot.right, plot.bottom, palette.muted, 1f, "axis")
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
            val targetLabel = targetLabels[index]
            label(canvas, targetLabel.value, targetLabel.x, targetLabel.baseline, targetLabel.color, Paint.Align.CENTER, "targetLabel", index)
            label(canvas, "阶段 ${index + 1}", center, stageBaseline, palette.muted, Paint.Align.CENTER, "stageLabel")
        }
    }
}
