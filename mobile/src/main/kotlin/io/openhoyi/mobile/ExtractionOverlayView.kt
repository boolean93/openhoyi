package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
internal class ExtractionOverlayView(context: Context, private val visual: ExtractionHistoryVisualStyle? = null,
    private val embeddedHistory: Boolean = false) : LinearLayout(context) {
    private val productRenderer = visual?.let { ProductHistoryChartRenderer(context, it, liveMode = !embeddedHistory) }
    internal val visualEvidence get() = productRenderer?.evidence ?: org.json.JSONArray()
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
            liveTimeEnd = 30_000L
            rebuildProjection()
            // The Activity sets identity before replacing points; do not retain the old cup range.
            previousRanges = emptyMap()
            liveTimeEnd = 30_000L
        }
    var referenceLabel: String? = null
        set(value) { if (field != value) { field = value; updateDescription() } }
    var compactRunningExplanation = false
        set(value) { if (field != value) { field = value; updateDescription() } }
    private var fullExplanation = ""
    fun overviewExplanation(): String = listOf(fullExplanation,
        context.getString(R.string.task_chart_independent, metricName(focusMetric), focusMetric.unit))
        .filter { it.isNotBlank() }.joinToString("\n")

    private var inspectedTime: Long? = null
    private var inspectionDialog: AlertDialog? = null
    private var previousRanges: Map<ChartMetric, ChartRange> = emptyMap()
    private var data = FourMetricChartProjection.build(emptyList(), emptyList())
    private var selected: ChartMetric? = null
    private val focusMetric get() = selected ?: ChartMetric.PRESSURE
    private val buttons = linkedMapOf<ChartMetric, Button>()
    private val liveTiles = linkedMapOf<ChartMetric, android.widget.FrameLayout>()
    private val liveValues = linkedMapOf<ChartMetric, Pair<TextView, TextView>>()
    private val liveRanges = linkedMapOf<ChartMetric, TextView>()
    private val liveChart get() = visual != null && !embeddedHistory
    private var liveTimeEnd = 30_000L
    var unavailableLiveMetrics: Set<ChartMetric> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            if (liveChart) {
                buttons.forEach { (metric, button) -> updateLegendText(metric, button) }
                updateDescription()
            }
        }
    private val legend = LegendLayout(context)
    private var compactMode = false
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
    /** Only embedded History owns this same live TextView outside the plot header; no copied projection. */
    internal val embeddedExplanationView: TextView?
        get() = explanation.takeIf { visual != null && embeddedHistory }
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
        if (visual == null || !embeddedHistory) addView(explanation, LayoutParams(-1, -2))
        addView(referenceCaption, LayoutParams(-1, -2))
    }
    private val header = ScrollView(context).apply {
        if (visual != null && !embeddedHistory) tag = "product/extraction/chart-header"
        isFillViewport = false
        addView(headerContent, ViewGroup.LayoutParams(-1, -2))
    }
    private val plot = MetricPlot(context).apply { tag = "chart-plot" }

    init {
        orientation = VERTICAL
        setBackgroundColor(visual?.palette?.surface ?: context.getColor(R.color.mobile_surface))
        addView(header, LayoutParams(-1, -2))
        addView(plot, LayoutParams(-1, 0, 1f))
        visual?.palette?.let { style ->
            listOf(compactNotice, explanation, referenceCaption).forEach { style.apply(it, TaskProductVisualStyle.auxiliary, true) }
        }
        if (visual != null && embeddedHistory) {
            explanation.tag = "product/history/chart-explanation"
            legend.tag = "product/history/chart-legend"
        }
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
        // The notice replaces an unusable plot; only the native header scrolls.
        val cap = if (compact) height else height - plotMinimum
        header.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        val wanted = header.measuredHeight.coerceAtMost(cap)
        if (header.layoutParams.height != wanted) header.layoutParams.height = wanted
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun rebuildProjection() {
        data = FourMetricChartProjection.build(points, reference, previousRanges)
        if (liveChart) {
            liveTimeEnd = maxOf(liveTimeEnd, LiveChartGeometry.timeEnd(data.timeEndMs))
            data = data.copy(timeEndMs = liveTimeEnd)
        }
        previousRanges = data.series.associate { it.metric to it.range }
        if (data.series.none { it.metric == selected }) selected = null
        val metrics = if (embeddedHistory) data.series.map { it.metric } else ChartMetric.entries
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
                    setSingleLine(false); maxLines = Int.MAX_VALUE; ellipsize = null
                    compoundDrawablePadding = dp(4f).toInt()
                    setCompoundDrawablesRelative(MetricSwatch(metric), null, null, null)
                    setOnClickListener {
                        if (data.series.none { it.metric == metric }) return@setOnClickListener
                        selected = metric
                        updateSelection()
                        updateDescription()
                        plot.invalidate()
                    }
                }.also { button ->
                    if (liveChart) addLiveTile(metric, button) else legend.addView(button)
                }
            }
            // Stable instances preserve accessibility focus while samples arrive.
            visual?.palette?.let { style ->
                style.apply(button, TaskProductVisualStyle.auxiliary)
                button.minHeight = style.dp(48); button.minimumHeight = style.dp(48)
                button.setTextColor(metricColor(metric))
            }
            updateLegendText(metric, button)
        }
        // Weight may appear after the temperature button was created.
        metrics.forEachIndexed { index, metric ->
            val button = buttons.getValue(metric)
            val tile = liveTiles[metric] ?: button
            if (legend.indexOfChild(tile) != index) {
                legend.removeView(tile)
                legend.addView(tile, index)
            }
        }
        if (inspectedTime != null && points.none { it.elapsedMs == inspectedTime }) {
            inspectedTime = null
            inspectionDialog?.dismiss()
        }
        updateSelection()
        updateDescription()
        plot.isEnabled = points.isNotEmpty()
        plot.invalidate()
    }

    private fun displayUnit(metric: ChartMetric) = if (liveChart && metric == ChartMetric.WATER) "mL" else metric.unit

    private fun addLiveTile(metric: ChartMetric, button: Button) {
        val palette = requireNotNull(visual).palette
        val tile = android.widget.FrameLayout(context)
        val body = LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(dp(8f).toInt(), dp(4f).toInt(), dp(8f).toInt(), dp(4f).toInt())
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val name = palette.text(if (metric == ChartMetric.WEIGHT) context.getString(R.string.task_extraction_scale)
            else metricName(metric), TaskProductVisualStyle.Type(14, 400, 20), "product/extraction/metric/$metric/name")
        name.setTextColor(metricColor(metric)); name.setCompoundDrawablesRelative(MetricSwatch(metric), null, null, null)
        name.compoundDrawablePadding = dp(4f).toInt()
        body.addView(name)
        val quantity = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = true }
        val number = palette.text("—", TaskProductVisualStyle.Type(24, 500, 32), "product/extraction/metric/$metric/number")
        val unit = palette.text(displayUnit(metric), TaskProductVisualStyle.Type(14, 400, 20), "product/extraction/metric/$metric/unit")
        number.setTextColor(metricColor(metric)); unit.setTextColor(metricColor(metric))
        quantity.addView(number); quantity.addView(unit, LayoutParams(-2, -2).apply { marginStart = dp(4f).toInt() })
        body.addView(quantity)
        val range = palette.text("", TaskProductVisualStyle.Type(12, 400, 18), "chart-range:$metric", true)
        body.addView(range)
        button.setCompoundDrawablesRelative(null, null, null, null)
        button.text = ""; button.setPadding(0, 0, 0, 0)
        tile.addView(body, android.widget.FrameLayout.LayoutParams(-1, -2))
        tile.addView(button, android.widget.FrameLayout.LayoutParams(-1, -1))
        liveTiles[metric] = tile; liveValues[metric] = number to unit; liveRanges[metric] = range
        legend.addView(tile)
    }

    private fun updateLegendText(metric: ChartMetric, button: Button) {
        val series = data.series.firstOrNull { it.metric == metric }
        if (liveChart) {
            val latest = series?.current?.lastOrNull()?.lastOrNull()?.takeIf {
                it.elapsedMs == data.observedThroughMs && metric !in unavailableLiveMetrics }
            val value = latest?.let { format(it.value) } ?: "—"
            val range = series?.range?.let { "${format(it.low)}–${format(it.high)} ${displayUnit(metric)}" }
                ?: context.getString(R.string.task_chart_missing_scale)
            liveValues[metric]?.first?.let { if (it.text.toString() != value) it.text = value }
            liveRanges[metric]?.let { if (it.text.toString() != range) it.text = range }
            val name = if (metric == ChartMetric.WEIGHT) context.getString(R.string.task_extraction_scale) else metricName(metric)
            button.contentDescription = "$name $value ${displayUnit(metric)}; $range; " +
                (if (metric == ChartMetric.WEIGHT && latest == null) context.getString(R.string.task_chart_missing_scale) + "; " else "") +
                context.getString(R.string.task_chart_hint)
            button.isEnabled = series != null && (metric != ChartMetric.WEIGHT || latest != null)
            return
        }
        if (series == null) {
            val label = "${metricName(metric)} — ${metric.unit}\n${context.getString(R.string.task_chart_missing_scale)}"
            if (button.text.toString() != label) button.text = label
            button.contentDescription = label.replace('\n', ';')
            button.isEnabled = false
            return
        }
        button.isEnabled = true
        val range = series.range
        val latest = series.current.lastOrNull()?.lastOrNull()
            ?.takeIf { it.elapsedMs == data.observedThroughMs }
        val name = "${metricName(metric)} ${latest?.let { format(it.value) } ?: "—"} ${metric.unit}"
        val fullTitle = "$name\n${format(range.low)}–${format(range.high)} ${metric.unit}"
        val visibleTitle = if (embeddedHistory) "${metricName(metric)} ${metric.unit}" else fullTitle
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
                liveTiles[metric]?.background = requireNotNull(visual).palette.shape(
                    if (focused) requireNotNull(visual).palette.selected else android.graphics.Color.TRANSPARENT,
                    radius = 8)
                button.typeface = TaskTypography.face(context, if (visual != null) 400 else if (focused) 700 else 400)
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
        val referenceText = referenceNotes.joinToString(" · ")
        if (referenceCaption.text.toString() != referenceText) referenceCaption.text = referenceText
        referenceCaption.visibility = if (referenceNotes.isEmpty()) GONE else VISIBLE
        if (data.waterResetsMs.isNotEmpty() || data.referenceWaterResetsMs.isNotEmpty())
            notes += context.getString(R.string.task_chart_water_reset)
        if (hasGaps()) notes += context.getString(R.string.task_chart_gap)
        fullExplanation = notes.joinToString(" · ")
        val compact = compactRunningExplanation && visual != null && !embeddedHistory
        val warnings = notes.drop(1)
        val inline = if (liveChart) (listOf(context.getString(R.string.task_chart_hint)) + warnings).joinToString(" · ")
            else if (compact) warnings.joinToString(" · ") else fullExplanation
        if (explanation.text.toString() != inline) explanation.text = inline
        explanation.visibility = if (!liveChart && compact && warnings.isEmpty()) GONE else VISIBLE
        plot.contentDescription = (notes + context.getString(R.string.task_chart_inspect_hint) + context.getString(R.string.task_chart_hint) +
            context.getString(R.string.task_chart_independent, metricName(focusMetric), displayUnit(focusMetric)) +
            referenceNotes + data.series.map { series ->
            val latest = series.current.lastOrNull()?.lastOrNull()
                ?.takeIf { it.elapsedMs == data.observedThroughMs &&
                    (!liveChart || series.metric !in unavailableLiveMetrics) }
            val name = if (liveChart && series.metric == ChartMetric.WEIGHT)
                context.getString(R.string.task_extraction_scale) else metricName(series.metric)
            context.getString(R.string.task_chart_latest, name,
                latest?.let { format(it.value) } ?: "—", displayUnit(series.metric))
        }).joinToString("; ")
        // Do not group the native clickable legend into one accessibility node.
        contentDescription = null
    }

    override fun onDetachedFromWindow() {
        dismissInspection()
        super.onDetachedFromWindow()
    }

    /** Fragment show/hide does not detach this view, so the host closes inspection on pause. */
    fun dismissInspection() {
        inspectionDialog?.dismiss()
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
        inspectionDialog = AppDialog.show(context, context.getString(R.string.extraction_chart), body,
            AppDialog.Action(context.getString(android.R.string.ok), onClick = {})).also { dialog ->
                dialog.setOnDismissListener { inspectionDialog = null }
            }
    }

    private fun metricName(metric: ChartMetric) = context.getString(when (metric) {
        ChartMetric.PRESSURE -> R.string.task_chart_pressure
        ChartMetric.WATER -> R.string.task_chart_water
        ChartMetric.WEIGHT -> R.string.task_chart_weight
        ChartMetric.TEMPERATURE -> R.string.task_chart_temperature
    })
    private fun metricColor(metric: ChartMetric): Int {
        visual?.let { return if (embeddedHistory) it.metric(metric) else it.liveMetric(metric) }
        val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return Color.parseColor(when (metric) {
            ChartMetric.PRESSURE -> if (dark) "#80B4FF" else "#176BD1"
            ChartMetric.WATER -> if (dark) "#FFAE78" else "#C05A13"
            ChartMetric.WEIGHT -> if (dark) "#64DEA3" else "#168A55"
            ChartMetric.TEMPERATURE -> if (dark) "#A994FF" else "#654ACC"
        })
    }

    private inner class MetricSwatch(private val metric: ChartMetric) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2f) }
        init { setBounds(0, 0, dp(18f).toInt(), dp(12f).toInt()) }
        override fun draw(canvas: Canvas) {
            paint.color = metricColor(metric)
            paint.strokeCap = Paint.Cap.ROUND
            paint.pathEffect = if (!liveChart) null else when (metric) {
                ChartMetric.WATER -> DashPathEffect(floatArrayOf(dp(9f), dp(5f)), 0f)
                ChartMetric.TEMPERATURE -> DashPathEffect(floatArrayOf(dp(1f), dp(4f)), 0f)
                else -> null
            }
            canvas.drawLine(bounds.left.toFloat(), bounds.exactCenterY(), bounds.right.toFloat(), bounds.exactCenterY(), paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** Four readouts stay whole; actual text measurement decides one or two rows. */
    private inner class LegendLayout(context: Context) : ViewGroup(context) {
        private val gap get() = dp(4f).toInt()
        private var columns = 4
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec)
            val wide = ((available - gap * 3) / 4).coerceAtLeast(1)
            var singleRowFits = true
            fun wholeSingleLine(view: View): Boolean = when (view) {
                is TextView -> view.layout?.let { layout ->
                    layout.lineCount <= 1 && (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 }
                } ?: false
                is ViewGroup -> (0 until view.childCount).all { wholeSingleLine(view.getChildAt(it)) }
                else -> true
            }
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (liveChart) {
                    // Font scaling grows height normally. Only horizontal content fit decides wrapping.
                    // The body's natural width includes the name/swatch, atomic number+unit, range and padding.
                    val body = (child as android.widget.FrameLayout).getChildAt(0)
                    val natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    body.measure(natural, natural)
                    if (body.measuredWidth > wide || !wholeSingleLine(body)) singleRowFits = false
                } else {
                    child.measure(MeasureSpec.makeMeasureSpec(wide, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                    if ((child as Button).lineCount > 2 || child.measuredHeight > dp(64f)) singleRowFits = false
                }
            }
            columns = if (singleRowFits) 4 else 2
            val childWidth = ((available - gap * (columns - 1)) / columns).coerceAtLeast(1)
            var rowHeight = 0
            var totalHeight = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                rowHeight = maxOf(rowHeight, child.measuredHeight)
                if (i % columns == columns - 1 || i == childCount - 1) {
                    totalHeight += rowHeight + if (i == childCount - 1) 0 else gap
                    rowHeight = 0
                }
            }
            setMeasuredDimension(available, totalHeight)
        }
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            var y = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val column = i % columns
                val x = column * (child.measuredWidth + gap)
                val childLeft = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width - x - child.measuredWidth else x
                child.layout(childLeft, y, childLeft + child.measuredWidth, y + child.measuredHeight)
                if (column == columns - 1 || i == childCount - 1) {
                    val start = i - column
                    y += (start..i).maxOf { getChildAt(it).measuredHeight } + gap
                }
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
            val dynamic = productRenderer?.let {
                if (embeddedHistory) it.minimumEmbeddedHistoryHeight() else it.minimumAxisHeight()
            } ?: ceil((fm.descent - fm.ascent) * 4.5f + dp(32f)).toInt()
            return if (embeddedHistory) dynamic else maxOf(dynamic, ceil(dp(96f)).toInt())
        }
        private fun axisColumns() = data.series.associate { series ->
            val strings = (0..4).map { format(series.range.low + (series.range.high - series.range.low) * it / 4) } + series.metric.unit
            series.metric to (strings.maxOf { labels.measureText(it) } + dp(8f))
        }
        fun showsAllAxes(availableWidth: Int): Boolean {
            productRenderer?.let { return it.allAxes(availableWidth, data) }
            val gutters = axisColumns().values.sum() + dp(10f)
            return TaskLayoutPolicy.chartAxes((availableWidth / resources.displayMetrics.density).toInt(),
                ceil(gutters / resources.displayMetrics.density).toInt()) == TaskChartAxisMode.ALL_SCALES
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            productRenderer?.let {
                plotBounds.set(it.draw(canvas, width, height, data, focusMetric, selected, false, inspectedTime))
                return
            }
            val fm = labels.fontMetrics
            val lineHeight = fm.descent - fm.ascent
            val outer = dp(5f)
            val axis = data.series.first { it.metric == focusMetric }
            val left = outer + axisColumns().getValue(focusMetric)
            val right = width - outer - dp(5f)
            val top = lineHeight * 2 + dp(12f)
            val bottom = height - lineHeight * 1.5f - dp(12f)
            // Parent measurement exposes the native notice instead of a too-short plot.
            // Also reject overlapping ticks if this child is ever drawn independently.
            plotBounds.setEmpty()
            if (right <= left || bottom - top < lineHeight + dp(8f)) return
            plotBounds.set(left, top, right, bottom)
            val rows = ((bottom - top) / (lineHeight + dp(18f))).toInt().plus(1).coerceIn(2, 4)
            labels.textAlign = Paint.Align.LEFT
            val columnX = outer
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
            drawAxis(axis, columnX)
            for (row in 0 until rows) {
                val py = bottom - (bottom - top) * row / (rows - 1)
                canvas.drawLine(left, py, right, py, grid)
            }
            labels.color = context.getColor(R.color.chart_label)
            val zero = "0"; val end = seconds(data.timeEndMs) + " s"; val middle = seconds(data.timeEndMs / 2)
            val xMiddle = (left + right) / 2
            val timeY = bottom + lineHeight / 2 + dp(4f) - fm.ascent
            labels.textAlign = Paint.Align.LEFT; canvas.drawText(zero, left, timeY, labels)
            labels.textAlign = Paint.Align.RIGHT; canvas.drawText(end, right, timeY, labels)
            if (xMiddle - labels.measureText(middle) / 2 > left + labels.measureText(zero) + dp(8f) &&
                xMiddle + labels.measureText(middle) / 2 < right - labels.measureText(end) - dp(8f)) {
                labels.textAlign = Paint.Align.CENTER; canvas.drawText(middle, xMiddle, timeY, labels)
            }
            fun x(ms: Long) = left + (right - left) * (ms.toDouble() / data.timeEndMs).toFloat()
            fun y(value: Float, range: ChartRange) = bottom - (bottom - top) * (value - range.low) / (range.high - range.low)
            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            fun drawSeries(series: MetricChartSeries, historic: Boolean) {
                val segments = if (historic) series.reference else series.current
                stroke.color = metricColor(series.metric)
                stroke.alpha = if (historic) 135 else if (selected == null || selected == series.metric) 255 else 190
                stroke.strokeWidth = dp(if (historic) 1.5f else 2f)
                stroke.pathEffect = if (historic) DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f) else null
                stroke.strokeCap = Paint.Cap.ROUND
                for (segment in segments) {
                    path.reset()
                    segment.forEachIndexed { index, value ->
                        if (index == 0) path.moveTo(x(value.elapsedMs), y(value.value, series.range))
                        else path.lineTo(x(value.elapsedMs), y(value.value, series.range))
                    }
                    canvas.drawPath(path, stroke)
                    if (segment.size == 1) {
                        stroke.style = Paint.Style.FILL
                        val point = segment.first()
                        canvas.drawCircle(x(point.elapsedMs), y(point.value, series.range), dp(2f), stroke)
                        stroke.style = Paint.Style.STROKE
                    }
                }
            }
            data.series.forEach { drawSeries(it, true) }
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
