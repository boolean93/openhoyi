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
    private val number: TextView, private val unit: TextView) : FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private var pressure: Float? = null
    private var maximum = 12f
    private var paused = false
    internal var drawingEvidence = org.json.JSONArray()
        private set
    private val quantity = LinearLayout(context)
    private val phaseLabel = visual.palette.text("",TaskProductVisualStyle.Type(12,400,18),secondary=true)
    init {
        tag = "product/extraction/pressure-gauge"
        setWillNotDraw(false)
        phaseLabel.visibility = GONE
        contentDescription = context.getString(R.string.task_chart_pressure) + " · — bar · 0–12 bar"
        visual.palette.apply(number,TaskProductVisualStyle.Type(20,500,28))
        visual.palette.apply(unit,TaskProductVisualStyle.Type(12,400,18),true)
        number.setTextColor(visual.liveMetric(ChartMetric.PRESSURE)); unit.setTextColor(visual.liveMetric(ChartMetric.PRESSURE))
        quantity.apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = true; gravity = Gravity.CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        (number.parent as? android.view.ViewGroup)?.removeView(number)
        (unit.parent as? android.view.ViewGroup)?.removeView(unit)
        quantity.addView(number); quantity.addView(unit, LinearLayout.LayoutParams(-2,-2).apply { marginStart = visual.palette.dp(4) })
        addView(quantity, LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = visual.palette.dp(2) })
        addView(phaseLabel,LayoutParams(-2,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = visual.palette.dp(33) })
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
        drawingEvidence = org.json.JSONArray()
        val cx = width/2f; val cy = visual.palette.dp(43).toFloat()
        val radius = minOf(visual.palette.dp(42).toFloat(),width/2f-visual.palette.dp(2))
        val box = RectF(cx-radius,cy-radius,cx+radius,cy+radius)
        paint.strokeWidth = visual.palette.dp(1).toFloat(); paint.shader = null
        paint.color = visual.palette.muted
        canvas.drawArc(box,150f,240f,false,paint)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = visual.palette.muted; strokeWidth = visual.palette.dp(1).toFloat()
            textSize = visual.palette.sp(14); typeface = TaskTypography.face(context,400); textAlign = Paint.Align.CENTER
        }
        for (index in 0..12) {
            val angle = Math.toRadians((150f+240f*index/12).toDouble())
            val major = index%3==0
            val inner = radius-visual.palette.dp(if(major) 5 else 3)
            canvas.drawLine(cx+kotlin.math.cos(angle).toFloat()*inner,cy+kotlin.math.sin(angle).toFloat()*inner,
                cx+kotlin.math.cos(angle).toFloat()*radius,cy+kotlin.math.sin(angle).toFloat()*radius,tick)
            if (index%6==0) {
                val text = String.format(Locale.ROOT,"%.0f",maximum*index/12)
                val raw = android.graphics.Rect(); tick.getTextBounds(text,0,text.length,raw)
                val advance = tick.measureText(text)
                var x = when(index) { 0 -> visual.palette.dp(4)+advance/2; 12 -> width-visual.palette.dp(4)-advance/2; else -> cx }
                var baseline = (if(index==6) cy-radius*.62f else cy+radius*.52f) - (tick.fontMetrics.ascent+tick.fontMetrics.descent)/2
                fun ink() = RectF(x-advance/2+raw.left,baseline+raw.top,x-advance/2+raw.right,baseline+raw.bottom)
                val numberBox = valueInk(number); val unitBox = valueInk(unit)
                var glyph = ink()
                val collisions = listOf(numberBox,unitBox).filter { it.width()>0 && glyph.left<it.right && glyph.right>it.left }
                if (collisions.isNotEmpty()) baseline -= maxOf(0f,glyph.bottom-(collisions.minOf { it.top }-visual.palette.dp(3)))
                glyph = ink()
                if (glyph.left < 1f) x += 1f-glyph.left
                if (glyph.right > width-1f) x -= glyph.right-(width-1f)
                glyph = ink()
                if (glyph.top < 1f) baseline += 1f-glyph.top
                canvas.drawText(text,x,baseline,tick)
                glyph = ink()
                drawingEvidence.put(org.json.JSONObject().put("role","tick").put("text",text)
                    .put("boundsPx",org.json.JSONArray(listOf(glyph.left,glyph.top,glyph.right,glyph.bottom))))
            }
        }
        pressure?.let { value ->
            val color = visual.liveMetric(ChartMetric.PRESSURE)
            paint.strokeWidth = visual.palette.dp(2).toFloat()
            paint.shader = SweepGradient(cx,cy,intArrayOf(color and 0x00FFFFFF or 0x44000000,color,color),floatArrayOf(0f,.75f,1f))
            canvas.drawArc(box,150f,240f*(value/maximum).coerceIn(0f,1f),false,paint)
            paint.shader = null; paint.color = color; paint.strokeWidth = visual.palette.dp(1).toFloat()
            val angle = Math.toRadians((150f+240f*(value/maximum).coerceIn(0f,1f)).toDouble())
            val length = radius-visual.palette.dp(14)
            canvas.drawLine(cx,cy,cx+kotlin.math.cos(angle).toFloat()*length,cy+kotlin.math.sin(angle).toFloat()*length,paint)
            paint.style = Paint.Style.FILL; canvas.drawCircle(cx,cy,visual.palette.dp(2).toFloat(),paint); paint.style = Paint.Style.STROKE
        }
        for ((role,view) in listOf("number" to number,"unit" to unit)) {
            val bounds = valueInk(view)
            drawingEvidence.put(org.json.JSONObject().put("role",role)
                .put("boundsPx",org.json.JSONArray(listOf(bounds.left,bounds.top,bounds.right,bounds.bottom))))
        }

    }
    private fun valueInk(view: TextView): RectF {
        val layout = view.layout ?: return RectF()
        if (layout.lineCount == 0) return RectF()
        val raw = android.graphics.Rect(); val text = view.text.toString()
        view.paint.getTextBounds(text,0,text.length,raw)
        val x = quantity.left+view.left+view.compoundPaddingLeft+layout.getLineLeft(0)
        val baseline = (quantity.top+view.top+view.compoundPaddingTop+layout.getLineBaseline(0)).toFloat()
        return RectF(x+raw.left,baseline+raw.top,x+raw.right,baseline+raw.bottom)
    }

}
