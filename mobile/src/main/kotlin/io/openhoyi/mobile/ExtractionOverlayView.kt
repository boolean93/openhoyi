package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.ceil

/** A display-only, shared-time plot. Each metric retains its own physical range. */
internal class ExtractionOverlayView(context: Context) : LinearLayout(context) {
    var points: List<ShotPoint> = emptyList()
        set(value) { field = value; rebuildProjection() }
    var reference: List<ShotPoint> = emptyList()
        set(value) { field = value; rebuildProjection() }
    var traceIdentity: String? = null
        set(value) {
            if (field == value) return
            field = value
            previousRanges = emptyMap()
            rebuildProjection()
            // The Activity sets identity before replacing points; do not retain the old cup range.
            previousRanges = emptyMap()
        }
    var referenceLabel: String? = null
        set(value) { if (field != value) { field = value; updateDescription() } }

    private var previousRanges: Map<ChartMetric, ChartRange> = emptyMap()
    private var data = FourMetricChartProjection.build(emptyList(), emptyList())
    private var selected: ChartMetric? = null
    private val focusMetric get() = selected ?: ChartMetric.PRESSURE
    private val buttons = linkedMapOf<ChartMetric, Button>()
    private val legend = LegendLayout(context)
    private val explanation = TextView(context).apply {
        textSize = 11f
        setTextColor(context.getColor(R.color.chart_label))
        setPadding(dp(6f).toInt(), 0, dp(6f).toInt(), dp(3f).toInt())
    }
    private val referenceCaption = TextView(context).apply {
        tag = "chart-reference-caption"
        textSize = 11f
        setTextColor(context.getColor(R.color.chart_label))
        setPadding(dp(6f).toInt(), 0, dp(6f).toInt(), dp(3f).toInt())
        visibility = GONE
    }
    private val headerContent = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(legend, LayoutParams(-1, -2))
        addView(explanation, LayoutParams(-1, -2))
        addView(referenceCaption, LayoutParams(-1, -2))
    }
    private val header = ScrollView(context).apply {
        isFillViewport = false
        addView(headerContent, ViewGroup.LayoutParams(-1, -2))
    }
    private val plot = MetricPlot(context).apply { tag = "chart-plot" }

    init {
        orientation = VERTICAL
        addView(header, LayoutParams(-1, -2))
        addView(plot, LayoutParams(-1, 0, 1f))
        rebuildProjection()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Only the legend/notes scroll in genuinely short windows; the plot keeps physical axes.
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val fm = plot.labels.fontMetrics
        val plotMinimum = ceil((fm.descent - fm.ascent) * 5 + dp(32f)).toInt()
        val cap = (height - plotMinimum).coerceAtLeast(dp(48f).toInt())
        header.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        val wanted = header.measuredHeight.coerceAtMost(cap)
        if (header.layoutParams.height != wanted) header.layoutParams.height = wanted
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun rebuildProjection() {
        data = FourMetricChartProjection.build(points, reference, previousRanges)
        previousRanges = data.series.associate { it.metric to it.range }
        if (data.series.none { it.metric == selected }) selected = null
        val metrics = data.series.map { it.metric }
        buttons.keys.filter { it !in metrics }.toList().forEach { metric ->
            legend.removeView(buttons.remove(metric))
        }
        metrics.forEach { metric ->
            val button = buttons.getOrPut(metric) {
                Button(context).apply {
                    tag = "chart-metric:$metric"
                    textSize = 14f
                    isAllCaps = false
                    minWidth = dp(48f).toInt(); minimumWidth = minWidth
                    minHeight = dp(48f).toInt(); minimumHeight = minHeight
                    setPadding(dp(6f).toInt(), dp(3f).toInt(), dp(6f).toInt(), dp(3f).toInt())
                    compoundDrawablePadding = dp(5f).toInt()
                    setCompoundDrawablesRelative(MetricSwatch(metric), null, null, null)
                    setOnClickListener {
                        selected = metric
                        updateSelection()
                        updateDescription()
                        plot.invalidate()
                    }
                }.also { legend.addView(it) }
            }
            // Stable instances preserve accessibility focus while samples arrive.
            val range = data.series.first { it.metric == metric }.range
            val title = "${metricName(metric)} · ${metric.unit}\n${format(range.low)}–${format(range.high)} ${metric.unit}"
            if (button.text.toString() != title) button.text = title
            button.contentDescription = title.replace('\n', ';')
        }
        // Weight may appear after the temperature button was created.
        metrics.forEachIndexed { index, metric ->
            val button = buttons.getValue(metric)
            if (legend.indexOfChild(button) != index) {
                legend.removeView(button)
                legend.addView(button, index)
            }
        }
        updateSelection()
        updateDescription()
        plot.invalidate()
    }

    private fun updateSelection() {
        buttons.forEach { (metric, button) ->
            button.isSelected = selected == metric
            button.setTextColor(metricColor(metric))
            button.alpha = 1f
            button.typeface = android.graphics.Typeface.create(button.typeface,
                if (selected == metric) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    private fun hasGaps() = listOf(points, reference).any { samples ->
        samples.zipWithNext().any { (a, b) -> b.elapsedMs - a.elapsedMs > 1500 } ||
            samples.any { it.weightHundredthsGram == null } && samples.any { it.weightHundredthsGram != null } ||
            samples.any { it.brewing == false }
    }

    private fun updateDescription() {
        val notes = mutableListOf(
            context.getString(R.string.task_chart_hint),
            context.getString(R.string.task_chart_independent, metricName(focusMetric), focusMetric.unit),
            data.observedThroughMs?.let { context.getString(R.string.task_chart_observed, seconds(it)) }
                ?: context.getString(R.string.task_chart_waiting),
        )
        val referenceNotes = if (reference.isNotEmpty()) listOfNotNull(
            referenceLabel?.takeIf { it.isNotBlank() }, context.getString(R.string.task_chart_reference, metricName(focusMetric)),
        ) else emptyList()
        val referenceText = referenceNotes.joinToString("\n")
        if (referenceCaption.text.toString() != referenceText) referenceCaption.text = referenceText
        referenceCaption.visibility = if (referenceNotes.isEmpty()) GONE else VISIBLE
        if (data.waterResetsMs.isNotEmpty() || data.referenceWaterResetsMs.isNotEmpty())
            notes += context.getString(R.string.task_chart_water_reset)
        if (hasGaps()) notes += context.getString(R.string.task_chart_gap)
        val description = notes.joinToString("\n")
        if (explanation.text.toString() != description) explanation.text = description
        plot.contentDescription = (notes + referenceNotes + data.series.map { series ->
            val latest = series.current.lastOrNull()?.lastOrNull()
            context.getString(R.string.task_chart_latest, metricName(series.metric),
                latest?.let { format(it.value) } ?: "—", series.metric.unit)
        }).joinToString("; ")
        // Do not group the native clickable legend into one accessibility node.
        contentDescription = null
    }

    private fun metricName(metric: ChartMetric) = context.getString(when (metric) {
        ChartMetric.PRESSURE -> R.string.task_chart_pressure
        ChartMetric.WATER -> R.string.task_chart_water
        ChartMetric.WEIGHT -> R.string.task_chart_weight
        ChartMetric.TEMPERATURE -> R.string.task_chart_temperature
    })
    private fun metricColor(metric: ChartMetric) = context.getColor(when (metric) {
        ChartMetric.PRESSURE -> R.color.chart_pressure
        ChartMetric.WATER -> R.color.task_chart_water
        ChartMetric.WEIGHT -> R.color.task_chart_weight
        ChartMetric.TEMPERATURE -> R.color.task_chart_temperature
    })
    private fun effect(metric: ChartMetric) = when (metric) {
        ChartMetric.WATER -> DashPathEffect(floatArrayOf(dp(9f), dp(5f)), 0f)
        ChartMetric.TEMPERATURE -> DashPathEffect(floatArrayOf(dp(1f), dp(4f)), 0f)
        else -> null
    }

    private inner class MetricSwatch(private val metric: ChartMetric) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2f) }
        init { setBounds(0, 0, dp(22f).toInt(), dp(12f).toInt()) }
        override fun draw(canvas: Canvas) {
            paint.color = metricColor(metric); paint.pathEffect = effect(metric)
            canvas.drawLine(bounds.left.toFloat(), bounds.exactCenterY(), bounds.right.toFloat(), bounds.exactCenterY(), paint)
            if (metric == ChartMetric.WEIGHT) {
                paint.pathEffect = null; paint.style = Paint.Style.FILL
                canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), dp(3f), paint)
                paint.style = Paint.Style.STROKE
            }
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** Wrap by measured text, never by a fixed four-column percentage. */
    private inner class LegendLayout(context: Context) : ViewGroup(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec)
            var x = 0; var y = 0; var rowHeight = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                if (x > 0 && x + child.measuredWidth > available) { y += rowHeight; x = 0; rowHeight = 0 }
                x += child.measuredWidth
                rowHeight = maxOf(rowHeight, child.measuredHeight)
            }
            setMeasuredDimension(available, y + rowHeight)
        }
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            var x = 0; var y = 0; var rowHeight = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (x > 0 && x + child.measuredWidth > width) { y += rowHeight; x = 0; rowHeight = 0 }
                val childLeft = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width - x - child.measuredWidth else x
                child.layout(childLeft, y, childLeft + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth
                rowHeight = maxOf(rowHeight, child.measuredHeight)
            }
        }
        override fun generateDefaultLayoutParams() = ViewGroup.LayoutParams(-2, -2)
    }

    private inner class MetricPlot(context: Context) : View(context) {
        val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f * resources.displayMetrics.scaledDensity
            color = context.getColor(R.color.chart_label)
        }
        private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.chart_grid); strokeWidth = dp(1f)
        }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val path = Path()
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val fm = labels.fontMetrics
            val lineHeight = fm.descent - fm.ascent
            val columns = data.series.associate { series ->
                val strings = (0..4).map { format(series.range.low + (series.range.high - series.range.low) * it / 4) } + series.metric.unit
                series.metric to (strings.maxOf { labels.measureText(it) } + dp(8f))
            }
            val outer = dp(5f)
            val allGutters = columns.values.sum() + outer * 2
            val allAxes = TaskLayoutPolicy.chartAxes((width / resources.displayMetrics.density).toInt(),
                ceil(allGutters / resources.displayMetrics.density).toInt()) == TaskChartAxisMode.ALL_SCALES
            val axes = if (allAxes) data.series else data.series.filter { it.metric == focusMetric }
            val leftAxes = if (allAxes) axes.take(2) else axes
            val rightAxes = if (allAxes) axes.drop(2) else emptyList()
            val left = outer + leftAxes.sumOf { columns.getValue(it.metric).toDouble() }.toFloat()
            val right = width - outer - rightAxes.sumOf { columns.getValue(it.metric).toDouble() }.toFloat()
            val top = lineHeight * 1.5f + dp(5f)
            val bottom = height - lineHeight * 2.5f - dp(9f)
            if (right <= left || bottom <= top) return
            val rows = ((bottom - top) / (lineHeight + dp(8f))).toInt().plus(1).coerceIn(2, 5)
            labels.textAlign = Paint.Align.LEFT
            var columnX = outer
            fun drawAxis(series: MetricChartSeries, x: Float) {
                labels.color = metricColor(series.metric)
                labels.textAlign = Paint.Align.LEFT
                canvas.drawText(series.metric.unit, x, dp(3f) - fm.ascent, labels)
                for (row in 0 until rows) {
                    val fraction = row.toFloat() / (rows - 1)
                    val py = bottom - (bottom - top) * fraction
                    canvas.drawText(format(series.range.low + (series.range.high - series.range.low) * fraction),
                        x, py - (fm.ascent + fm.descent) / 2, labels)
                }
            }
            leftAxes.forEach { drawAxis(it, columnX); columnX += columns.getValue(it.metric) }
            columnX = right + dp(8f)
            rightAxes.forEach { drawAxis(it, columnX); columnX += columns.getValue(it.metric) }
            for (row in 0 until rows) {
                val py = bottom - (bottom - top) * row / (rows - 1)
                canvas.drawLine(left, py, right, py, grid)
            }
            labels.color = context.getColor(R.color.chart_label)
            val zero = "0"; val end = seconds(data.timeEndMs); val middle = seconds(data.timeEndMs / 2)
            val xMiddle = (left + right) / 2
            val timeY = bottom + lineHeight / 2 + dp(4f) - fm.ascent
            labels.textAlign = Paint.Align.LEFT; canvas.drawText(zero, left, timeY, labels)
            labels.textAlign = Paint.Align.RIGHT; canvas.drawText(end, right, timeY, labels)
            if (xMiddle - labels.measureText(middle) / 2 > left + labels.measureText(zero) + dp(8f) &&
                xMiddle + labels.measureText(middle) / 2 < right - labels.measureText(end) - dp(8f)) {
                labels.textAlign = Paint.Align.CENTER; canvas.drawText(middle, xMiddle, timeY, labels)
            }
            labels.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.live_time_axis), xMiddle, height - dp(2f) - fm.descent, labels)
            fun x(ms: Long) = left + (right - left) * (ms.toDouble() / data.timeEndMs).toFloat()
            fun y(value: Float, range: ChartRange) = bottom - (bottom - top) * (value - range.low) / (range.high - range.low)
            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            fun drawSeries(series: MetricChartSeries, historic: Boolean) {
                val segments = if (historic) series.reference else series.current
                stroke.color = if (historic) context.getColor(R.color.chart_label) else metricColor(series.metric)
                stroke.alpha = if (historic) 125 else if (selected == null || selected == series.metric) 255 else 210
                stroke.strokeWidth = dp(if (historic) 1f else if (selected == series.metric) 3f else 2f)
                stroke.pathEffect = if (historic) null else effect(series.metric)
                stroke.strokeCap = if (series.metric == ChartMetric.TEMPERATURE) Paint.Cap.ROUND else Paint.Cap.BUTT
                for (segment in segments) {
                    path.reset()
                    segment.forEachIndexed { index, value ->
                        if (index == 0) path.moveTo(x(value.elapsedMs), y(value.value, series.range))
                        else path.lineTo(x(value.elapsedMs), y(value.value, series.range))
                    }
                    canvas.drawPath(path, stroke)
                    if (segment.size == 1 || !historic && series.metric == ChartMetric.WEIGHT) {
                        stroke.pathEffect = null; stroke.style = Paint.Style.FILL
                        segment.forEach { canvas.drawCircle(x(it.elapsedMs), y(it.value, series.range), dp(if (historic) 1.5f else 2.5f), stroke) }
                        stroke.style = Paint.Style.STROKE
                        stroke.pathEffect = if (historic) null else effect(series.metric)
                    }
                }
            }
            // One focused historical metric avoids overlaying eight competing traces.
            data.series.firstOrNull { it.metric == focusMetric }?.let { drawSeries(it, true) }
            data.series.sortedBy { it.metric == selected }.forEach { drawSeries(it, false) }
            data.observedThroughMs?.let { canvas.drawLine(x(it), top, x(it), bottom, grid) }
            canvas.restore()
            if (points.isEmpty() && reference.isEmpty()) {
                val empty = context.getString(R.string.chart_empty_title)
                if (labels.measureText(empty) <= right - left) {
                    labels.textAlign = Paint.Align.CENTER
                    canvas.drawText(empty, xMiddle, (top + bottom) / 2 - (fm.ascent + fm.descent) / 2, labels)
                }
            }
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun format(value: Float) = String.format(Locale.ROOT, "%.1f", value)
    private fun seconds(ms: Long) = String.format(Locale.ROOT, "%.1f", ms / 1000.0)
}
