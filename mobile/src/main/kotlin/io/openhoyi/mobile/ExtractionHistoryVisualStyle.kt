package io.openhoyi.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.widget.Button
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** M5-only visual input. No state, sampling or projection is changed here. */
internal class ExtractionHistoryVisualStyle(val context: Context) {
    val palette = TaskProductVisualStyle(context)
    val focus = TaskProductVisualStyle.Type(48, 700, 72)
    val focusUnit = TaskProductVisualStyle.Type(22, 400, 34)
    private val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private fun pick(light: String, night: String) = android.graphics.Color.parseColor(if (dark) night else light)
    val water = pick("#007E89", "#66CAD3")
    val weight = pick("#00685F", "#83D6B9")
    val temperature = pick("#A46500", "#F2C16D")
    val warningSurface = pick("#FFF5E8", "#382F22")
    val warningBorder = pick("#D7B782", "#A78B52")
    val warningInk = pick("#795119", "#EACB87")
    val stop = android.graphics.Color.parseColor("#B5302D")
    fun metric(metric: ChartMetric) = when (metric) {
        ChartMetric.PRESSURE -> palette.pressure
        ChartMetric.WATER -> water
        ChartMetric.WEIGHT -> weight
        ChartMetric.TEMPERATURE -> temperature
    }
    /** The live four-line palette; History keeps its established metric colors. */
    fun liveMetric(metric: ChartMetric) = pick(when (metric) {
        ChartMetric.PRESSURE -> "#176BD1"
        ChartMetric.WATER -> "#C05A13"
        ChartMetric.WEIGHT -> "#168A55"
        ChartMetric.TEMPERATURE -> "#654ACC"
    }, when (metric) {
        ChartMetric.PRESSURE -> "#80B4FF"
        ChartMetric.WATER -> "#FFAE78"
        ChartMetric.WEIGHT -> "#64DEA3"
        ChartMetric.TEMPERATURE -> "#A994FF"
    })
    fun choiceAdapter(activity: ThemedActivity, labels: List<String>) = object : android.widget.ArrayAdapter<String>(activity, android.R.layout.simple_spinner_dropdown_item, labels) {
        private fun present(view: android.view.View, selected: Boolean) = (view as android.widget.TextView).apply {
            palette.apply(this, TaskProductVisualStyle.body)
            setSingleLine(false); maxLines = Int.MAX_VALUE
            ellipsize = null
            minHeight = palette.dp(52); minimumHeight = palette.dp(52)
            layoutParams = (layoutParams ?: android.view.ViewGroup.LayoutParams(-1, -2)).apply { height = -2 }
            setPadding(palette.dp(12), palette.dp(8), palette.dp(12), palette.dp(8))
            setBackgroundColor(palette.surface); contentDescription = text
            if (selected) tag = "full-text-details"
        }
        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup) = present(super.getView(position, convertView, parent), true)
        override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup) = present(super.getDropDownView(position, convertView, parent), false)
    }
    fun stopButton(button: Button) {
        palette.button(button, primary = true)
        button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(palette.onDisabled, android.graphics.Color.WHITE)))
        button.background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
            android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled), palette.shape(palette.disabled))
                addState(intArrayOf(), palette.shape(stop))
            }, palette.shape(android.graphics.Color.WHITE))
    }
}

/** Independent M5 Paint and actual-draw evidence; consumes the existing validated projection only. */
internal class ProductHistoryChartRenderer(private val context: Context, private val style: ExtractionHistoryVisualStyle,
    private val liveMode: Boolean = false) {
    private val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = TaskTypography.face(context, 400); textSize = this@ProductHistoryChartRenderer.style.palette.sp(14); isFakeBoldText = false
        letterSpacingCompat()
    }
    private fun Paint.letterSpacingCompat() { textScaleX = 1f; textSkewX = 0f }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@ProductHistoryChartRenderer.style.palette.border; strokeWidth = dp(1f); strokeCap = Paint.Cap.BUTT }
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@ProductHistoryChartRenderer.style.palette.muted; strokeWidth = dp(1f); strokeCap = Paint.Cap.BUTT }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Rect()
    var evidence = JSONArray()
        private set
    private fun dp(value: Float) = value * context.resources.displayMetrics.density
    private val line get() = labels.fontMetrics.descent - labels.fontMetrics.ascent
    fun minimumAxisHeight() = kotlin.math.ceil(if (liveMode) line * 4.5f + dp(32f) else line * 5 + dp(48f)).toInt()
    // Non-comparison bounds reserve 1.5 lines + 16dp above and 2.5 lines + 24dp below the real plot.
    fun minimumEmbeddedHistoryHeight() = kotlin.math.ceil(dp(96f) + line * 4 + dp(40f)).toInt()
    fun minimumComparisonHeight(plotMinimumDp: Float = 96f) = kotlin.math.ceil(dp(plotMinimumDp) + line * 3 + dp(24f)).toInt()
    private fun number(value: Float) = String.format(Locale.ROOT, "%.1f", value)
    private fun seconds(value: Long) = String.format(Locale.ROOT, "%.1f", value / 1000.0)
    private fun extent(text: String): Float { labels.getTextBounds(text, 0, text.length, ink)
        return maxOf(labels.measureText(text), ink.right.toFloat()) - minOf(0f, ink.left.toFloat()) }
    private fun column(series: MetricChartSeries): Float = ((0..4).map {
        number(series.range.low + (series.range.high - series.range.low) * it / 4)
    } + series.metric.unit).maxOf(::extent) + dp(16f)
    fun allAxes(width: Int, data: FourMetricChartData) = width - data.series.sumOf { column(it).toDouble() } - dp(16f) >= dp(96f)
    fun bounds(width: Int, height: Int, data: FourMetricChartData, focused: ChartMetric, comparison: Boolean): RectF {
        val selected = data.series.filter { comparison && it.metric == focused || !comparison }
        val all = !comparison && !liveMode && allAxes(width, data)
        val leftAxes = if (all) selected.filter { it.metric == ChartMetric.TEMPERATURE || it.metric == ChartMetric.PRESSURE } else selected.filter { it.metric == focused }
        val rightAxes = if (all) selected.filter { it.metric == ChartMetric.WEIGHT || it.metric == ChartMetric.WATER } else emptyList()
        val left = dp(8f) + leftAxes.sumOf { column(it).toDouble() }.toFloat()
        val right = width - dp(8f) - rightAxes.sumOf { column(it).toDouble() }.toFloat()
        return if (comparison) RectF(left, line * 1.5f + dp(12f), right, height - line * 1.5f - dp(12f))
            else if (liveMode) RectF(left, line * 2 + dp(12f), right, height - line * 1.5f - dp(12f))
            else RectF(left, line * 1.5f + dp(16f), right, height - line * 2.5f - dp(24f))
    }
    private data class Label(val text: String, val x: Float, val baseline: Float, val align: Paint.Align, val color: Int, val role: String)
    private fun glyph(label: Label): RectF {
        labels.getTextBounds(label.text, 0, label.text.length, ink)
        val advance = labels.measureText(label.text)
        val origin = label.x - when (label.align) { Paint.Align.CENTER -> advance / 2; Paint.Align.RIGHT -> advance; else -> 0f }
        return RectF(origin + ink.left, label.baseline + ink.top, origin + ink.right, label.baseline + ink.bottom)
    }
    private fun rect(value: RectF) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
    private fun color(value: Int) = String.format(Locale.ROOT, "#%08X", value)
    fun draw(canvas: Canvas, width: Int, height: Int, data: FourMetricChartData, focused: ChartMetric,
        selected: ChartMetric?, comparison: Boolean, inspectedTime: Long? = null): RectF {
        evidence = JSONArray()
        val box = bounds(width, height, data, focused, comparison)
        if (box.width() < dp(96f) || box.height() < line + dp(8f)) return RectF()
        val all = !comparison && !liveMode && allAxes(width, data)
        val series = if (comparison || !all) data.series.filter { it.metric == focused } else data.series
        val leftAxes = if (all) listOf(ChartMetric.TEMPERATURE, ChartMetric.PRESSURE).mapNotNull { m -> series.firstOrNull { it.metric == m } } else series
        val rightAxes = if (all) listOf(ChartMetric.WEIGHT, ChartMetric.WATER).mapNotNull { m -> series.firstOrNull { it.metric == m } } else emptyList()
        val rows = ((box.height() / (line + dp(if (liveMode) 18f else 8f))).toInt() + 1)
            .coerceIn(2, if (liveMode) 4 else 5)
        val texts = mutableListOf<Label>()
        val fm = labels.fontMetrics
        fun planAxis(item: MetricChartSeries, x: Float) {
            val metricInk = if (liveMode) style.liveMetric(item.metric) else style.metric(item.metric)
            texts += Label(item.metric.unit, x, dp(4f) - fm.ascent, Paint.Align.LEFT, metricInk, "axis-unit")
            for (row in 0 until rows) {
                val fraction = row.toFloat() / (rows - 1)
                texts += Label(number(item.range.low + (item.range.high - item.range.low) * fraction), x,
                    box.bottom - box.height() * fraction - (fm.ascent + fm.descent) / 2, Paint.Align.LEFT, metricInk, "axis-tick")
            }
        }
        var x = dp(8f)
        leftAxes.forEach { planAxis(it, x); x += column(it) }
        x = box.right + dp(8f)
        rightAxes.forEach { planAxis(it, x); x += column(it) }
        val timeBaseline = box.bottom + line / 2 + dp(8f) - fm.ascent
        texts += Label("0", box.left, timeBaseline, Paint.Align.LEFT, style.palette.muted, "time-tick")
        texts += Label(seconds(data.timeEndMs) + if (comparison || liveMode) " s" else "", box.right, timeBaseline, Paint.Align.RIGHT, style.palette.muted, "time-tick")
        val middle = seconds(data.timeEndMs / 2)
        if (box.width() / 2 - extent(middle) / 2 > maxOf(extent("0"), extent(seconds(data.timeEndMs))) + dp(8f))
            texts += Label(middle, box.centerX(), timeBaseline, Paint.Align.CENTER, style.palette.muted, "time-tick")
        if (!comparison && !liveMode) texts += Label(context.getString(R.string.live_time_axis), box.centerX(), height - dp(2f) - fm.descent, Paint.Align.CENTER, style.palette.muted, "time-unit")
        if (data.observedThroughMs == null && data.series.all { it.reference.isEmpty() }) {
            val empty = context.getString(R.string.chart_empty_title)
            if (extent(empty) <= box.width()) texts += Label(empty, box.centerX(), box.centerY() - (fm.ascent + fm.descent) / 2,
                Paint.Align.CENTER, style.palette.muted, "empty-notice")
        }
        val protections = texts.map { glyph(it).apply { inset(-dp(2f), -dp(2f)) } }
        protections.forEachIndexed { index, r -> evidence.put(JSONObject().put("operation", "textProtection").put("textIndex", index)
            .put("glyphBoundsPx",rect(glyph(texts[index]))).put("protectedBoundsPx", rect(r)).put("paddingDp", 2).put("paddingPx",dp(2f))) }
        evidence.put(JSONObject().put("operation", "plot").put("boundsPx", rect(box)).put("allAxes", all)
            .put("projectionTimeEndMs", data.timeEndMs).put("observedThroughMs", data.observedThroughMs))
        fun gridLine(id: String, a: Float, b: Float, c: Float, d: Float) {
            val horizontal = b == d
            var intervals = listOf(if (horizontal) a to c else b to d)
            protections.forEach { protected ->
                val r = RectF(protected).apply { inset(-grid.strokeWidth / 2, -grid.strokeWidth / 2) }
                if (if (horizontal) b >= r.top && b <= r.bottom else a >= r.left && a <= r.right) {
                    val low = if (horizontal) r.left else r.top; val high = if (horizontal) r.right else r.bottom
                    intervals = intervals.flatMap { (start, end) -> if (high <= start || low >= end) listOf(start to end)
                        else listOfNotNull((start to minOf(end, low)).takeIf { it.first < it.second }, (maxOf(start, high) to end).takeIf { it.first < it.second }) }
                }
            }
            val visible = JSONArray()
            intervals.forEach { (start, end) ->
                val coords = if (horizontal) listOf(start, b, end, d) else listOf(a, start, c, end)
                canvas.drawLine(coords[0], coords[1], coords[2], coords[3], grid); visible.put(JSONArray(coords))
                evidence.put(JSONObject().put("operation", "line").put("role", "grid").put("gridId", id).put("endpointsPx", JSONArray(coords)))
            }
            evidence.put(JSONObject().put("operation", "gridSegmentPlan").put("gridId", id).put("originalEndpointsPx", JSONArray(listOf(a,b,c,d)))
                .put("visibleSegmentsPx", visible).put("strokeWidthPx", grid.strokeWidth).put("strokeCap", grid.strokeCap.name))
        }
        for (row in 0 until rows) { val y = box.bottom - box.height() * row / (rows - 1); gridLine("h$row", box.left, y, box.right, y) }
        val verticalDivisions = if (liveMode) 2 else 4
        for (tick in 0..verticalDivisions) { val px = box.left + box.width() * tick / verticalDivisions; gridLine("v$tick", px, box.top, px, box.bottom) }
        canvas.drawLine(box.left, box.top, box.left, box.bottom, axis)
        evidence.put(JSONObject().put("operation", "line").put("role", "axis").put("endpointsPx", JSONArray(listOf(box.left,box.top,box.left,box.bottom))))
        canvas.drawLine(box.left, box.bottom, box.right, box.bottom, axis)
        evidence.put(JSONObject().put("operation", "line").put("role", "axis").put("endpointsPx", JSONArray(listOf(box.left,box.bottom,box.right,box.bottom))))
        fun px(ms: Long) = box.left + box.width() * (ms.toDouble() / data.timeEndMs).toFloat()
        fun py(value: Float, range: ChartRange) = box.bottom - box.height() * (value - range.low) / (range.high - range.low)
        canvas.save(); canvas.clipRect(box)
        fun segments(item: MetricChartSeries, historic: Boolean) {
            val values = if (historic) item.reference else item.current
            stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE
            stroke.color = if (liveMode) style.liveMetric(item.metric)
                else if (historic && !comparison) style.palette.muted else style.metric(item.metric)
            stroke.alpha = if (liveMode && historic) 135 else if (liveMode && selected != null && selected != item.metric) 190
                else if (historic && !comparison) 125 else 255
            stroke.strokeWidth = dp(if (liveMode && historic) 1.5f else if (liveMode) 2f
                else if (historic && !comparison) 1f else if (selected == item.metric && !comparison) 3f else 2f)
            stroke.strokeCap = if (liveMode || item.metric == ChartMetric.TEMPERATURE && !comparison) Paint.Cap.ROUND else Paint.Cap.BUTT
            val dash = when {
                liveMode && historic -> floatArrayOf(dp(5f), dp(4f))
                liveMode -> null
                comparison && historic -> floatArrayOf(dp(8f), dp(5f))
                historic -> null
                item.metric == ChartMetric.WATER && !comparison -> floatArrayOf(dp(9f), dp(5f))
                item.metric == ChartMetric.TEMPERATURE && !comparison -> floatArrayOf(dp(1f), dp(4f))
                else -> null
            }
            stroke.pathEffect = dash?.let { DashPathEffect(it, 0f) }
            for (segment in values) {
                val path = Path(); val vertices = JSONArray()
                segment.forEachIndexed { index, value -> val xx = px(value.elapsedMs); val yy = py(value.value, item.range)
                    if (index == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy); vertices.put(JSONArray(listOf(xx,yy))) }
                canvas.drawPath(path, stroke)
                evidence.put(JSONObject().put("operation", "path").put("role", "data").put("metric", item.metric.name).put("reference", historic)
                    .put("verticesPx", vertices).put("colorArgb", color(stroke.color)).put("alpha", stroke.alpha)
                    .put("strokeWidthPx", stroke.strokeWidth).put("dashIntervalsPx", dash?.let { JSONArray(it.toList()) } ?: JSONObject.NULL))
                if (liveMode && segment.size == 1) {
                    val value = segment.first()
                    val savedEffect = stroke.pathEffect
                    stroke.pathEffect = null; stroke.style = Paint.Style.FILL
                    canvas.drawCircle(px(value.elapsedMs), py(value.value, item.range), dp(2f), stroke)
                    evidence.put(JSONObject().put("operation", "circle").put("role", "single-observation")
                        .put("metric", item.metric.name).put("reference", historic)
                        .put("centerPx", JSONArray(listOf(px(value.elapsedMs), py(value.value, item.range)))).put("radiusPx", dp(2f)))
                    stroke.style = Paint.Style.STROKE; stroke.pathEffect = savedEffect
                }
                if (!liveMode && (segment.size == 1 || !historic && !comparison && item.metric == ChartMetric.WEIGHT)) {
                    val savedEffect = stroke.pathEffect; stroke.pathEffect = null; stroke.style = Paint.Style.FILL
                    segment.forEach { value -> canvas.drawCircle(px(value.elapsedMs), py(value.value, item.range), dp(2.5f), stroke)
                        evidence.put(JSONObject().put("operation", "circle").put("role", "data").put("metric", item.metric.name)
                            .put("centerPx", JSONArray(listOf(px(value.elapsedMs),py(value.value,item.range)))).put("radiusPx", dp(2.5f)).put("colorArgb",color(stroke.color))) }
                    stroke.style = Paint.Style.STROKE; stroke.pathEffect = savedEffect
                }
            }
        }
        if (comparison) series.forEach { segments(it,false); segments(it,true) }
        else {
            if (liveMode) data.series.forEach { segments(it, true) }
            else data.series.firstOrNull { it.metric == focused }?.let { segments(it,true) }
            data.series.sortedBy { it.metric == selected }.forEach { segments(it,false) }
        }
        if (!comparison) data.observedThroughMs?.let { time ->
            axis.pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f)
            canvas.drawLine(px(time), box.top, px(time), box.bottom, axis)
            evidence.put(JSONObject().put("operation", "line").put("role", "observed-through")
                .put("endpointsPx", JSONArray(listOf(px(time),box.top,px(time),box.bottom))).put("dashIntervalsPx",JSONArray(listOf(dp(3f),dp(3f)))))
            axis.pathEffect = null
        }
        inspectedTime?.let { time -> canvas.drawLine(px(time), box.top, px(time), box.bottom, axis)
            evidence.put(JSONObject().put("operation", "line").put("role", "inspection").put("endpointsPx", JSONArray(listOf(px(time),box.top,px(time),box.bottom)))) }
        canvas.restore()
        texts.forEachIndexed { index, text ->
            labels.color = text.color; labels.textAlign = text.align; labels.alpha = 255; labels.pathEffect = null
            canvas.drawText(text.text, text.x, text.baseline, labels)
            evidence.put(JSONObject().put("operation", "text").put("role", text.role).put("text", text.text).put("textIndex",index)
                .put("positionPx", JSONArray(listOf(text.x,text.baseline))).put("glyphBoundsPx",rect(glyph(text)))
                .put("textSizePx",labels.textSize).put("weight",400).put("fontAssetSha256",if (context.resources.configuration.locales[0].language == "zh") TaskProductVisualStyle.FONT_SHA else JSONObject.NULL)
                .put("colorArgb",color(labels.color)).put("alpha",labels.alpha).put("fakeBold",labels.isFakeBoldText)
                .put("paintTypefaceWeight", if (android.os.Build.VERSION.SDK_INT >= 28) labels.typeface.weight else JSONObject.NULL)
                .put("paintTypefaceItalic",labels.typeface.isItalic).put("paintTextScaleX",labels.textScaleX).put("paintTextSkewX",labels.textSkewX)
                .put("paintTypefaceIdentity",System.identityHashCode(labels.typeface))
                .put("typefaceSource","TaskTypography.face(context,400)")
                .put("matchesAssignedTaskTypeface",labels.typeface === TaskTypography.face(context,400))
                .put("fontIdentityStatus","candidate_asset_association_not_platform_glyph_identity")
                .put("fontMetrics",JSONObject().put("top",fm.top).put("ascent",fm.ascent).put("descent",fm.descent).put("bottom",fm.bottom).put("leading",fm.leading)))
        }
        return box
    }
}

/** A permanent atomic number/unit group. Updating it never changes its native baseline contract. */
internal class HistoryQuantity(context: Context, private val style: ExtractionHistoryVisualStyle,
    id: String, large: Boolean = true, focus: Boolean = false) : android.widget.LinearLayout(context) {
    val number = style.palette.text("—", if (focus) style.focus else if (large) TaskProductVisualStyle.largeValue else TaskProductVisualStyle.shareMetric, "$id/number")
    val unit = style.palette.text("", if (focus) style.focusUnit else TaskProductVisualStyle.auxiliary, "$id/unit", true)
    init {
        tag = id; orientation = HORIZONTAL; isBaselineAligned = true; gravity = android.view.Gravity.BOTTOM
        addView(number, LayoutParams(-2, -2)); addView(unit, LayoutParams(-2, -2).apply { marginStart = style.palette.dp(8) })
    }
    fun present(value: String, suffix: String?) {
        if (number.text.toString() != value) number.text = value
        unit.text = suffix.orEmpty(); unit.visibility = if (suffix == null) GONE else VISIBLE
        contentDescription = listOfNotNull(value, suffix).joinToString(" ")
    }
}

/** Natural-width display groups; they wrap as whole groups, never by breaking a number from its unit. */
internal class HistoryVisualFlow(context: Context, private val gap: Int, private val rowGap: Int = gap) : android.view.ViewGroup(context) {
    override fun generateDefaultLayoutParams() = LayoutParams(-2, -2)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        var x = 0; var y = 0; var row = 0
        for (index in 0 until childCount) {
            val view = getChildAt(index); if (view.visibility == GONE) continue
            view.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + view.measuredWidth > available) { y += row + rowGap; x = 0; row = 0 }
            x += view.measuredWidth + gap; row = maxOf(row, view.measuredHeight)
        }
        setMeasuredDimension(resolveSize(available, widthMeasureSpec), resolveSize(y + row, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var x = 0; var y = 0; var row = 0
        for (index in 0 until childCount) {
            val view = getChildAt(index); if (view.visibility == GONE) continue
            if (x > 0 && x + view.measuredWidth > width) { y += row + rowGap; x = 0; row = 0 }
            val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - x - view.measuredWidth else x
            view.layout(left, y, left + view.measuredWidth, y + view.measuredHeight)
            x += view.measuredWidth + gap; row = maxOf(row, view.measuredHeight)
        }
    }
}

/** Two permanent toolbar groups share one row only when both real natural widths can be honored. */
internal class HistoryToolbarRow(context: Context, private val gap: Int, private val rowGap: Int = gap,
    private val firstMinimum: () -> Int) : android.view.ViewGroup(context) {
    private var inline = false
    override fun generateDefaultLayoutParams() = LayoutParams(-2, -2)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        check(childCount == 2)
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val second = getChildAt(1)
        second.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), natural)
        inline = firstMinimum() + gap + second.measuredWidth <= available
        val first = getChildAt(0)
        first.measure(MeasureSpec.makeMeasureSpec(if (inline) available - gap - second.measuredWidth else available, MeasureSpec.EXACTLY), natural)
        setMeasuredDimension(available, resolveSize(if (inline) maxOf(first.measuredHeight, second.measuredHeight)
            else first.measuredHeight + rowGap + second.measuredHeight, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val first = getChildAt(0); val second = getChildAt(1)
        fun place(view: android.view.View, x: Int, y: Int) {
            val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - x - view.measuredWidth else x
            view.layout(left, y, left + view.measuredWidth, y + view.measuredHeight)
        }
        place(first, 0, 0)
        place(second, if (inline) first.measuredWidth + gap else 0, if (inline) (height - second.measuredHeight) / 2 else first.measuredHeight + rowGap)
    }
}
