package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Display only. The arc has a physical range, not an ideal-pressure or safety zone. */
internal class LivePressureGauge(context: Context, private val visual: ExtractionHistoryVisualStyle,
    number: TextView, unit: TextView) : FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private var pressure: Float? = null
    private var maximum = 12f
    private var paused = false
    private val phaseLabel = visual.palette.text("",TaskProductVisualStyle.Type(12,400,18),secondary=true)
    init {
        tag = "product/extraction/pressure-gauge"
        setWillNotDraw(false)
        phaseLabel.visibility = GONE
        contentDescription = context.getString(R.string.task_chart_pressure) + " · — bar · 0–12 bar"
        val quantity = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = true; gravity = Gravity.CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        (number.parent as? android.view.ViewGroup)?.removeView(number)
        (unit.parent as? android.view.ViewGroup)?.removeView(unit)
        quantity.addView(number); quantity.addView(unit, LinearLayout.LayoutParams(-2,-2).apply { marginStart = visual.palette.dp(4) })
        addView(quantity, LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = visual.palette.dp(2) })
        addView(phaseLabel,LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = visual.palette.dp(28) })
    }
    fun present(value: Float?, max: Float, isPaused: Boolean) {
        val upper = maxOf(12f,max,value ?: 0f)
        if (pressure == value && maximum == upper && paused == isPaused) return
        pressure = value; maximum = upper; paused = isPaused
        phaseLabel.text = if (isPaused) context.getString(R.string.live_pressure_pause) else ""
        phaseLabel.visibility = if (isPaused) VISIBLE else GONE
        contentDescription = context.getString(R.string.task_chart_pressure) + " · " +
            (value?.let { String.format(Locale.ROOT,"%.1f bar",it) } ?: "— bar") + " · 0–$maximum bar" +
            if (isPaused) " · " + context.getString(R.string.live_pressure_pause_explanation) else ""
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width/2f; val cy = height*.40f
        val radius = minOf(width*.41f,height*.39f)
        val box = RectF(cx-radius,cy-radius,cx+radius,cy+radius)
        paint.strokeWidth = visual.palette.dp(1).toFloat(); paint.shader = null
        paint.color = visual.palette.muted
        canvas.drawArc(box,135f,270f,false,paint)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = visual.palette.muted; strokeWidth = visual.palette.dp(1).toFloat()
            textSize = visual.palette.sp(12); typeface = TaskTypography.face(context,400); textAlign = Paint.Align.CENTER
        }
        for (index in 0..12) {
            val angle = Math.toRadians((135f+270f*index/12).toDouble())
            val major = index%3==0
            val inner = radius-visual.palette.dp(if(major) 5 else 3)
            canvas.drawLine(cx+kotlin.math.cos(angle).toFloat()*inner,cy+kotlin.math.sin(angle).toFloat()*inner,
                cx+kotlin.math.cos(angle).toFloat()*radius,cy+kotlin.math.sin(angle).toFloat()*radius,tick)
            if (index%6==0) {
                val r = radius-visual.palette.dp(10)
                val baseline = cy+kotlin.math.sin(angle).toFloat()*r-(tick.fontMetrics.ascent+tick.fontMetrics.descent)/2
                canvas.drawText(String.format(Locale.ROOT,"%.0f",maximum*index/12),cx+kotlin.math.cos(angle).toFloat()*r,baseline,tick)
            }
        }
        pressure?.let { value ->
            val color = visual.liveMetric(ChartMetric.PRESSURE)
            paint.strokeWidth = visual.palette.dp(2).toFloat()
            paint.shader = SweepGradient(cx,cy,intArrayOf(color and 0x00FFFFFF or 0x44000000,color,color),floatArrayOf(0f,.75f,1f))
            canvas.drawArc(box,135f,270f*(value/maximum).coerceIn(0f,1f),false,paint)
            paint.shader = null; paint.color = color; paint.strokeWidth = visual.palette.dp(1).toFloat()
            val angle = Math.toRadians((135f+270f*(value/maximum).coerceIn(0f,1f)).toDouble())
            val length = radius-visual.palette.dp(12)
            canvas.drawLine(cx,cy,cx+kotlin.math.cos(angle).toFloat()*length,cy+kotlin.math.sin(angle).toFloat()*length,paint)
            paint.style = Paint.Style.FILL; canvas.drawCircle(cx,cy,visual.palette.dp(2).toFloat(),paint); paint.style = Paint.Style.STROKE
        }
    }
}
