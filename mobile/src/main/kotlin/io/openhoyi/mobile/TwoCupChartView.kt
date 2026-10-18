package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.ceil

/** Two saved cups, one focused physical scale. Neither source predicts the other. */
internal class TwoCupChartView(context: Context) : FrameLayout(context) {
    var firstPoints: List<ShotPoint> = emptyList()
        set(value) { if (field != value) { field = value.toList(); project() } }
    var secondPoints: List<ShotPoint> = emptyList()
        set(value) { if (field != value) { field = value.toList(); project() } }
    var metric: ChartMetric = ChartMetric.PRESSURE
        set(value) { if (field != value) { field = value; refreshPresentation() } }
    var comparisonMessage: String? = null
        set(value) { if (field != value) { field = value; refreshPresentation() } }
    private var firstTimeline = ExtractionReference.align(emptyList())
    private var secondTimeline = ExtractionReference.align(emptyList())
    internal var projection = FourMetricChartProjection.build(emptyList(), emptyList())
        private set
    internal val focusedSeries get() = projection.series.firstOrNull { it.metric == metric }
    internal val plotBounds: RectF get() = if (plot.visibility == VISIBLE) plot.bounds() else RectF()
    internal val firstObservedOrigin get() = firstTimeline.observedOrigin
    internal val secondObservedOrigin get() = secondTimeline.observedOrigin
    private val plot = ComparisonPlot(context)
    private val noticeText = TextView(context).apply {
        textSize = 14f; setTextColor(context.getColor(R.color.mobile_text))
        setPadding(dp(8f).toInt(), dp(8f).toInt(), dp(8f).toInt(), dp(8f).toInt())
    }
    private val notice = ScrollView(context).apply {
        tag = "comparison-plot-notice"; addView(noticeText)
    }
    init {
        tag = "two-cup-chart"
        setBackgroundColor(context.getColor(R.color.mobile_surface))
        addView(plot, LayoutParams(-1, -1)); addView(notice, LayoutParams(-1, -1))
        refreshPresentation()
    }
    private fun project() {
        firstTimeline = ExtractionReference.align(firstPoints)
        secondTimeline = ExtractionReference.align(secondPoints)
        projection = FourMetricChartProjection.build(firstTimeline.points, secondTimeline.points)
        refreshPresentation()
    }
    private fun refreshPresentation() {
        val series = focusedSeries
        plot.contentDescription = listOf(context.getString(R.string.task_comparison_scale, metric.unit),
            "A: " + (series?.current?.flatten()?.takeIf { it.isNotEmpty() }?.joinToString { "${seconds(it.elapsedMs)} s: ${number(it.value)} ${metric.unit}" } ?: "—"),
            "B: " + (series?.reference?.flatten()?.takeIf { it.isNotEmpty() }?.joinToString { "${seconds(it.elapsedMs)} s: ${number(it.value)} ${metric.unit}" } ?: "—")
        ).joinToString("; ")
        requestLayout(); plot.invalidate()
    }
    fun minimumChartHeight(): Int = ceil(dp(96f) + plot.axisHeight()).toInt()
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val tooSmall = height < minimumChartHeight() || width - plot.leftGutter() - dp(8f) < dp(96f)
        val noWeight = metric == ChartMetric.WEIGHT && focusedSeries == null
        val showPlot = comparisonMessage == null && !tooSmall && !noWeight
        val plotVisibility = if (showPlot) VISIBLE else GONE
        val noticeVisibility = if (showPlot) GONE else VISIBLE
        if (plot.visibility != plotVisibility) plot.visibility = plotVisibility
        if (notice.visibility != noticeVisibility) notice.visibility = noticeVisibility
        val message = comparisonMessage ?: context.getString(if (noWeight)
            R.string.task_comparison_no_weight_both else R.string.task_comparison_short_chart)
        if (noticeText.text.toString() != message) noticeText.text = message
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    private inner class ComparisonPlot(context: Context) : View(context) {
        init { tag = "comparison-plot" }
        private val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TaskTypography.face(context)
            textSize = 12f * resources.displayMetrics.scaledDensity
            color = context.getColor(R.color.chart_label)
        }
        private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.chart_grid); strokeWidth = dp(1f)
        }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(2.5f)
        }
        private val path = Path()
        private val dash = DashPathEffect(floatArrayOf(dp(8f), dp(5f)), 0f)
        private val textBounds = Rect()
        private val line get() = labels.fontMetrics.descent - labels.fontMetrics.ascent
        private fun extent(text: String): Float {
            labels.getTextBounds(text, 0, text.length, textBounds)
            return maxOf(labels.measureText(text), textBounds.right.toFloat()) - minOf(0f, textBounds.left.toFloat())
        }
        fun leftGutter(): Float {
            val range = focusedSeries?.range
            val ticks = if (range == null) listOf(metric.unit) else (0..4).map {
                number(range.low + (range.high - range.low) * it / 4)
            } + metric.unit
            return ticks.maxOf(::extent) + dp(14f)
        }
        // One unit/time-label line plus half of the edge tick label on each side.
        fun axisHeight() = line * 3f + dp(16f)
        fun bounds() = RectF(leftGutter(), line * 1.5f + dp(8f), width - dp(8f), height - line * 1.5f - dp(8f))
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val series = focusedSeries ?: return
            val box = bounds()
            if (box.height() < dp(96f) || box.width() < dp(96f)) return
            val fm = labels.fontMetrics
            labels.textAlign = Paint.Align.LEFT
            canvas.drawText(metric.unit, dp(5f), dp(4f) - fm.ascent, labels)
            val rows = ((box.height() / (line + dp(8f))).toInt() + 1).coerceIn(2, 5)
            for (row in 0 until rows) {
                val fraction = row.toFloat() / (rows - 1)
                val py = box.bottom - box.height() * fraction
                canvas.drawLine(box.left, py, box.right, py, grid)
                val value = number(series.range.low + (series.range.high - series.range.low) * fraction)
                labels.getTextBounds(value, 0, value.length, textBounds)
                canvas.drawText(value, dp(5f) - minOf(0f, textBounds.left.toFloat()), py - (fm.ascent + fm.descent) / 2, labels)
            }
            val tickY = box.bottom + line / 2 + dp(4f) - fm.ascent
            val start = "0"; val end = seconds(projection.timeEndMs) + " s"
            labels.textAlign = Paint.Align.LEFT; canvas.drawText(start, box.left, tickY, labels)
            labels.textAlign = Paint.Align.RIGHT; canvas.drawText(end, box.right, tickY, labels)
            val middle = seconds(projection.timeEndMs / 2)
            if (box.width() / 2 - extent(middle) / 2 > maxOf(extent(start), extent(end)) + dp(8f)) {
                labels.textAlign = Paint.Align.CENTER; canvas.drawText(middle, box.centerX(), tickY, labels)
            }
            fun x(ms: Long) = box.left + box.width() * (ms.toDouble() / projection.timeEndMs).toFloat()
            fun y(value: Float) = box.bottom - box.height() * (value - series.range.low) / (series.range.high - series.range.low)
            canvas.save(); canvas.clipRect(box)
            fun drawSegments(segments: List<List<ChartValue>>, second: Boolean) {
                stroke.color = context.getColor(R.color.chart_pressure)
                stroke.pathEffect = if (second) dash else null
                for (segment in segments) {
                    path.reset()
                    segment.forEachIndexed { index, value ->
                        if (index == 0) path.moveTo(x(value.elapsedMs), y(value.value))
                        else path.lineTo(x(value.elapsedMs), y(value.value))
                    }
                    canvas.drawPath(path, stroke)
                    if (segment.size == 1) {
                        val value = segment.single(); val px = x(value.elapsedMs); val py = y(value.value)
                        stroke.style = Paint.Style.FILL; stroke.pathEffect = null
                        if (second) canvas.drawRect(px - dp(3f), py - dp(3f), px + dp(3f), py + dp(3f), stroke)
                        else canvas.drawCircle(px, py, dp(3f), stroke)
                        stroke.style = Paint.Style.STROKE; stroke.pathEffect = if (second) dash else null
                    }
                }
            }
            drawSegments(series.current, false); drawSegments(series.reference, true)
            canvas.restore()
        }
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun number(value: Float) = String.format(Locale.ROOT, "%.1f", value)
    private fun seconds(ms: Long) = String.format(Locale.ROOT, "%.1f", ms / 1000.0)
}
