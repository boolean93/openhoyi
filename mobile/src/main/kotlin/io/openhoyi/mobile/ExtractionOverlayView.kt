package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.MotionEvent
import android.graphics.RectF
import android.widget.SeekBar
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
            inspectionDialog?.dismiss()
            inspectedTime = null
            previousRanges = emptyMap()
            rebuildProjection()
            // The Activity sets identity before replacing points; do not retain the old cup range.
            previousRanges = emptyMap()
        }
    var referenceLabel: String? = null
        set(value) { if (field != value) { field = value; updateDescription() } }

    private var inspectedTime: Long? = null
    private var inspectionDialog: AlertDialog? = null
    private var previousRanges: Map<ChartMetric, ChartRange> = emptyMap()
    private var data = FourMetricChartProjection.build(emptyList(), emptyList())
    private var selected: ChartMetric? = null
    private val focusMetric get() = selected ?: ChartMetric.PRESSURE
    private val buttons = linkedMapOf<ChartMetric, Button>()
    private val legend = LegendLayout(context)
    private var compactMode = false
    private var showLegendRanges = true
    private val compactNotice = TextView(context).apply {
        tag = "chart-compact-notice"
        textSize = 14f
        text = context.getString(R.string.task_chart_compact_notice)
        setTextColor(context.getColor(R.color.chart_label))
        setPadding(dp(6f).toInt(), dp(4f).toInt(), dp(6f).toInt(), dp(4f).toInt())
        visibility = GONE
    }
    private val explanation = TextView(context).apply {
        textSize = TaskVisualTokens.SUPPORTING_SP.toFloat()
        setTextColor(context.getColor(R.color.chart_label))
        setPadding(dp(TaskVisualTokens.RELATED_GAP_DP.toFloat()).toInt(), 0,
            dp(TaskVisualTokens.RELATED_GAP_DP.toFloat()).toInt(), dp(TaskVisualTokens.RELATED_GAP_DP.toFloat()).toInt())
    }
    private val referenceCaption = TextView(context).apply {
        tag = "chart-reference-caption"
        textSize = TaskVisualTokens.SUPPORTING_SP.toFloat()
        setTextColor(context.getColor(R.color.chart_label))
        setPadding(dp(TaskVisualTokens.RELATED_GAP_DP.toFloat()).toInt(), 0,
            dp(TaskVisualTokens.RELATED_GAP_DP.toFloat()).toInt(), dp(TaskVisualTokens.RELATED_GAP_DP.toFloat()).toInt())
        visibility = GONE
    }
    private val headerContent = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(compactNotice, LayoutParams(-1, -2))
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
        setBackgroundColor(context.getColor(R.color.mobile_surface))
        addView(header, LayoutParams(-1, -2))
        addView(plot, LayoutParams(-1, 0, 1f))
        rebuildProjection()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val plotMinimum = plot.minimumAxisHeight()
        val compact = height < plotMinimum + dp(48f).toInt()
        if (compact != compactMode) {
            compactMode = compact
            compactNotice.visibility = if (compact) VISIBLE else GONE
            plot.visibility = if (compact) GONE else VISIBLE
            // A newly necessary notice must not start below an old legend scroll offset.
            if (compact) header.scrollTo(0, 0)
        }
        val showRanges = compact || !plot.showsAllAxes(MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
        if (showLegendRanges != showRanges) {
            showLegendRanges = showRanges
            buttons.forEach { (metric, button) -> updateLegendText(metric, button) }
        }
        // The notice replaces an unusable plot; only the native header scrolls.
        val cap = if (compact) height else height - plotMinimum
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
                    backgroundTintList = null
                    background = legendBackground()
                    typeface = TaskTypography.face(context, 400)
                    gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                    elevation = 0f
                    stateListAnimator = null
                    setTextColor(metricColor(metric))
                    minWidth = dp(48f).toInt(); minimumWidth = minWidth
                    minHeight = dp(48f).toInt(); minimumHeight = minHeight
                    setPadding(dp(6f).toInt(), dp(3f).toInt(), dp(6f).toInt(), dp(3f).toInt())
                    compoundDrawablePadding = dp(TaskVisualTokens.RELATED_GAP_DP.toFloat()).toInt()
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
            updateLegendText(metric, button)
        }
        // Weight may appear after the temperature button was created.
        metrics.forEachIndexed { index, metric ->
            val button = buttons.getValue(metric)
            if (legend.indexOfChild(button) != index) {
                legend.removeView(button)
                legend.addView(button, index)
            }
        }
        if (inspectedTime != null && points.none { it.elapsedMs == inspectedTime }) {
            inspectedTime = null
            inspectionDialog?.dismiss()
        }
        updateSelection()
        updateDescription()
        plot.isEnabled = points.isNotEmpty()
        if (plot.width > 0 && showLegendRanges != (compactMode || !plot.showsAllAxes(plot.width))) requestLayout()
        plot.invalidate()
    }

    private fun updateLegendText(metric: ChartMetric, button: Button) {
        val series = data.series.first { it.metric == metric }
        val range = series.range
        val latest = series.current.lastOrNull()?.lastOrNull()
            ?.takeIf { it.elapsedMs == data.observedThroughMs }
        val name = "${metricName(metric)} ${latest?.let { format(it.value) } ?: "—"} ${metric.unit}"
        val fullTitle = "$name\n${format(range.low)}–${format(range.high)} ${metric.unit}"
        val visibleTitle = if (showLegendRanges) fullTitle else name
        if (button.text.toString() != visibleTitle) button.text = visibleTitle
        button.contentDescription = fullTitle.replace('\n', ';') + "; " + context.getString(R.string.task_chart_hint)
    }

    private fun legendBackground(): Drawable {
        fun fill(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(8f)
        }
        // Keep focus/press feedback without giving each legend entry a permanent card.
        val feedback = context.getColor(R.color.mobile_accent_soft)
        val content = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), fill(feedback))
            addState(intArrayOf(), fill(android.graphics.Color.TRANSPARENT))
        }
        return android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(feedback), content, fill(android.graphics.Color.WHITE))
    }

    private fun updateSelection() {
        buttons.forEach { (metric, button) ->
            // The initial pressure axis is focused without dimming other current curves.
            val focused = focusMetric == metric
            if (button.isSelected != focused) {
                button.isSelected = focused
                button.typeface = TaskTypography.face(context, if (focused) 700 else 400)
            }
        }
    }

    private fun hasGaps() = listOf(points, reference).any { samples ->
        samples.zipWithNext().any { (a, b) -> b.elapsedMs - a.elapsedMs > 1500 } ||
            samples.any { it.weightHundredthsGram == null } && samples.any { it.weightHundredthsGram != null } ||
            samples.any { it.brewing == false }
    }

    private fun updateDescription() {
        val notes = mutableListOf(
            data.observedThroughMs?.let { context.getString(R.string.task_chart_summary_observed, seconds(it)) }
                ?: context.getString(R.string.task_chart_summary_waiting),
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
        plot.contentDescription = (notes + context.getString(R.string.task_chart_inspect_hint) + context.getString(R.string.task_chart_hint) +
            context.getString(R.string.task_chart_independent, metricName(focusMetric), focusMetric.unit) +
            referenceNotes + data.series.map { series ->
            val latest = series.current.lastOrNull()?.lastOrNull()
                ?.takeIf { it.elapsedMs == data.observedThroughMs }
            context.getString(R.string.task_chart_latest, metricName(series.metric),
                latest?.let { format(it.value) } ?: "—", series.metric.unit)
        }).joinToString("; ")
        // Do not group the native clickable legend into one accessibility node.
        contentDescription = null
    }

    override fun onDetachedFromWindow() {
        inspectionDialog?.dismiss()
        super.onDetachedFromWindow()
    }

    private fun inspectSample() {
        if (points.isEmpty() || inspectionDialog != null) return
        // A live append must not move the observation under a user's finger.
        val samples = points.toList()
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20f).toInt(), dp(12f).toInt(), dp(20f).toInt(), dp(12f).toInt())
        }
        val readout = TextView(context).apply {
            tag = "chart-sample-readout"
            textSize = 18f
            setTextColor(context.getColor(R.color.mobile_text))
            setTextIsSelectable(true)
        }
        body.addView(ScrollView(context).apply { addView(readout) }, LayoutParams(-1, dp(220f).toInt()))
        val slider = SeekBar(context).apply {
            tag = "chart-sample-slider"
            max = samples.lastIndex
            isEnabled = samples.size > 1
            contentDescription = context.getString(R.string.task_chart_inspect_hint)
        }
        body.addView(slider, LayoutParams(-1, dp(48f).toInt()))
        fun show(index: Int) {
            val point = samples[index]
            inspectedTime = point.elapsedMs
            val title = context.getString(R.string.task_chart_sample_time,
                String.format(Locale.ROOT, "%.2f", point.elapsedMs / 1000.0))
            readout.text = (listOf(title) + ChartInspection.values(point).map { (metric, value) ->
                val displayed = value?.let { String.format(Locale.ROOT,
                    if (metric == ChartMetric.WEIGHT || metric == ChartMetric.TEMPERATURE) "%.2f" else "%.1f", it) + " " + metric.unit }
                    ?: context.getString(if (metric == ChartMetric.PRESSURE) R.string.task_chart_not_brewing else R.string.task_chart_missing_scale)
                "${metricName(metric)}: $displayed"
            }).joinToString("\n\n")
            if (android.os.Build.VERSION.SDK_INT >= 30) slider.stateDescription = title
            else slider.contentDescription = title + "; " + context.getString(R.string.task_chart_inspect_hint)
            plot.invalidate()
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { show(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        val initial = ChartInspection.nearestIndex(samples, inspectedTime ?: samples.last().elapsedMs)!!
        slider.progress = initial
        show(initial)
        inspectionDialog = AlertDialog.Builder(context).setTitle(R.string.extraction_chart).setView(body)
            .setPositiveButton(android.R.string.ok, null).create().also { dialog ->
                dialog.setOnDismissListener { inspectionDialog = null }
                dialog.show()
            }
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
        init { setBounds(0, 0, dp(32f).toInt(), dp(12f).toInt()) }
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

    /** Natural text wrapping, with at most two columns below the wide-window breakpoint. */
    private inner class LegendLayout(context: Context) : ViewGroup(context) {
        private val gap get() = dp(TaskVisualTokens.RELATED_GAP_DP.toFloat()).toInt()
        private val twoColumns get() = resources.configuration.screenWidthDp < 840

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec)
            val childWidth = if (twoColumns) ((available - gap) / 2).coerceAtLeast(1) else available
            var x = 0; var y = 0; var rowHeight = 0; var rowCount = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                if (rowCount > 0 && (x + child.measuredWidth > available || twoColumns && rowCount == 2)) {
                    y += rowHeight + gap; x = 0; rowHeight = 0; rowCount = 0
                }
                x += child.measuredWidth + gap
                rowHeight = maxOf(rowHeight, child.measuredHeight)
                rowCount++
            }
            setMeasuredDimension(available, y + rowHeight)
        }
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            var x = 0; var y = 0; var rowHeight = 0; var rowCount = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (rowCount > 0 && (x + child.measuredWidth > width || twoColumns && rowCount == 2)) {
                    y += rowHeight + gap; x = 0; rowHeight = 0; rowCount = 0
                }
                val childLeft = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width - x - child.measuredWidth else x
                child.layout(childLeft, y, childLeft + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth + gap
                rowHeight = maxOf(rowHeight, child.measuredHeight)
                rowCount++
            }
        }
        override fun generateDefaultLayoutParams() = ViewGroup.LayoutParams(-2, -2)
    }

    private inner class MetricPlot(context: Context) : View(context) {
        private val plotBounds = RectF()
        init {
            isFocusable = true
            setOnClickListener { inspectSample() }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP && plotBounds.contains(event.x, event.y) && points.isNotEmpty()) {
                val fraction = ((event.x - plotBounds.left) / plotBounds.width()).coerceIn(0f, 1f)
                val time = (fraction.toDouble() * data.timeEndMs).toLong()
                inspectedTime = ChartInspection.nearestIndex(points, time)?.let { points[it].elapsedMs }
            }
            return super.onTouchEvent(event)
        }
        val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TaskTypography.face(context)
            textSize = 11f * resources.displayMetrics.scaledDensity
            color = context.getColor(R.color.chart_label)
        }
        private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.chart_grid); strokeWidth = dp(1f)
        }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val path = Path()
        fun minimumAxisHeight(): Int {
            val fm = labels.fontMetrics
            // Four lines of axis margins + one tick height and >=8dp tick separation.
            return ceil((fm.descent - fm.ascent) * 5 + dp(32f)).toInt()
        }
        private fun axisColumns() = data.series.associate { series ->
            val strings = (0..4).map { format(series.range.low + (series.range.high - series.range.low) * it / 4) } + series.metric.unit
            series.metric to (strings.maxOf { labels.measureText(it) } + dp(8f))
        }
        fun showsAllAxes(availableWidth: Int): Boolean {
            val gutters = axisColumns().values.sum() + dp(10f)
            return TaskLayoutPolicy.chartAxes((availableWidth / resources.displayMetrics.density).toInt(),
                ceil(gutters / resources.displayMetrics.density).toInt()) == TaskChartAxisMode.ALL_SCALES
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val fm = labels.fontMetrics
            val lineHeight = fm.descent - fm.ascent
            val columns = axisColumns()
            val outer = dp(5f)
            val allAxes = showsAllAxes(width)
            val axes = if (allAxes) data.series else data.series.filter { it.metric == focusMetric }
            val leftAxes = if (allAxes) axes.take(2) else axes
            val rightAxes = if (allAxes) axes.drop(2) else emptyList()
            val left = outer + leftAxes.sumOf { columns.getValue(it.metric).toDouble() }.toFloat()
            val right = width - outer - rightAxes.sumOf { columns.getValue(it.metric).toDouble() }.toFloat()
            val top = lineHeight * 1.5f + dp(5f)
            val bottom = height - lineHeight * 2.5f - dp(9f)
            // Parent measurement exposes the native notice instead of a too-short plot.
            // Also reject overlapping ticks if this child is ever drawn independently.
            plotBounds.setEmpty()
            if (right <= left || bottom - top < lineHeight + dp(8f)) return
            plotBounds.set(left, top, right, bottom)
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
            inspectedTime?.let { time ->
                stroke.color = context.getColor(R.color.mobile_text)
                stroke.alpha = 255
                stroke.strokeWidth = dp(1.5f)
                stroke.pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f)
                canvas.drawLine(x(time), top, x(time), bottom, stroke)
                stroke.pathEffect = null
            }
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
