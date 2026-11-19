package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

/** Fresh telemetry and its real target share one bounded temperature scale. */
internal class BoilerTemperatureDial(context: Context, private val heading: TextView,
    private val value: TextView, target: TextView, private val power: HoyiSwitch) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val style = TaskProductVisualStyle(context)
    private val ink = style.homeDialInk
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headingSize=heading.textSize
    private val headingLine=heading.lineHeight
    private val valueLine=value.lineHeight
    private val valueMin=value.minimumHeight
    private val valueMaxSp=kotlin.math.round(AppTextUnits.sp(resources, value.autoSizeMaxTextSize.toFloat())).toInt()
    private val targetSize=target.textSize
    private val targetLine=target.lineHeight
    private val words=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
    private lateinit var targetBand:TextView
    private var smallLabels:Boolean?=null
    private var lastSmallDiameter=0
    private var currentTemperature: Float? = null
    private var targetTemperature: Float? = null
    private fun TextView.setCompatibleLineHeight(heightPx: Int) {
        if (android.os.Build.VERSION.SDK_INT >= 28) setLineHeight(heightPx)
        else {
            val metrics = this.paint.fontMetricsInt
            setLineSpacing((heightPx - metrics.descent + metrics.ascent).coerceAtLeast(0).toFloat(), 1f)
        }
    }
    fun present(current: Float?, goal: Float?) {
        if (currentTemperature == current && targetTemperature == goal) return
        currentTemperature = current; targetTemperature = goal; invalidate()
    }
    init {
        setWillNotDraw(false)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(style.surface)
            setStroke(density.toInt().coerceAtLeast(1),style.homeDialBorder)
        }
        elevation = 2 * density
        for (field in listOf(heading, value)) {
            field.gravity = Gravity.CENTER; field.setTextColor(ink)
            words.addView(field, LinearLayout.LayoutParams(-1,-2))
        }
        val touch = (48 * density).toInt()
        words.addView(power, LinearLayout.LayoutParams(touch,touch))
        addView(words, LayoutParams(-1,-1))
        targetBand=TextView(context).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,target.textSize)
            typeface=target.typeface; includeFontPadding=false; gravity=Gravity.CENTER
            setTextColor(ink); setCompatibleLineHeight(target.lineHeight)
        }
        words.addView(targetBand, LinearLayout.LayoutParams(-1,-2))
        fun projectTarget() {
            targetBand.text=target.text
            target.visibility=GONE
        }
        target.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {projectTarget()}
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        projectTarget()
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val diameterPx = MeasureSpec.getSize(widthMeasureSpec)
        val measuredTextBudget = diameterPx - (62 * density).toInt()
        val small = diameterPx < 180 * density || headingLine + valueLine + targetLine > measuredTextBudget
        if(smallLabels!=small || small && lastSmallDiameter!=MeasureSpec.getSize(widthMeasureSpec)) {
            lastSmallDiameter=MeasureSpec.getSize(widthMeasureSpec)
            smallLabels=small
            val compactHeadingPx=AppTextUnits.pixels(resources, 12f)
            heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,if(small) compactHeadingPx else headingSize)
            if(small) {
                heading.setSingleLine(true);heading.setLineSpacing(0f,1f)
                heading.setAutoSizeTextTypeUniformWithConfiguration(10,12,1,android.util.TypedValue.COMPLEX_UNIT_SP)
            } else {
                heading.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,headingSize)
                heading.setCompatibleLineHeight(headingLine)
            }
            value.setAutoSizeTextTypeUniformWithConfiguration(if(small) 14 else 16,if(small) 30 else valueMaxSp.coerceAtLeast(16),1,android.util.TypedValue.COMPLEX_UNIT_SP)
            value.minimumHeight=if(small) 0 else valueMin
            value.minHeight=if(small) 0 else valueMin
            if(small) value.setLineSpacing(0f,1f) else value.setCompatibleLineHeight(valueLine)
            val diameter=MeasureSpec.getSize(widthMeasureSpec)
            val touch = (48 * density).toInt()
            val textBudget = (diameter - touch - (14 * density).toInt()).coerceAtLeast(1)
            heading.layoutParams=LinearLayout.LayoutParams(-1,if(small) (textBudget*.28f).toInt() else -2)
            value.layoutParams=LinearLayout.LayoutParams(-1,if(small) (textBudget*.45f).toInt() else -2)
            words.gravity=Gravity.CENTER
            heading.translationY = 5 * density
            words.layoutParams=LayoutParams(-1,-1).apply {
                val inset=((if(small) 12 else 25)*density).toInt()
                setMargins(inset,0,inset,0)
            }
            targetBand.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,if(small) compactHeadingPx else targetSize)
            if(small) {
                targetBand.setSingleLine(true);targetBand.setLineSpacing(0f,1f)
                targetBand.setAutoSizeTextTypeUniformWithConfiguration(10,12,1,android.util.TypedValue.COMPLEX_UNIT_SP)
            } else {
                targetBand.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                targetBand.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,targetSize)
                targetBand.setCompatibleLineHeight(targetLine)
            }
            targetBand.layoutParams=LinearLayout.LayoutParams(-1,if(small) (textBudget*.27f).toInt() else -2)
        }
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx=width/2f; val cy=height/2f; val radius=minOf(width,height)/2f-11*density
        val bounds=RectF(cx-radius,cy-radius,cx+radius,cy+radius)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=8*density; paint.strokeCap=Paint.Cap.ROUND
        paint.color=style.homeDialTrack; canvas.drawArc(bounds,135f,270f,false,paint)
        val goal=targetTemperature; val current=currentTemperature
        if(current!=null && goal!=null && goal>0) {
            paint.color=style.homeDialProgress
            canvas.drawArc(bounds,135f,270f*(current/goal).coerceIn(0f,1f),false,paint)
        }
        paint.color=ink; paint.strokeWidth=1.5f*density
        for(degrees in listOf(180,225,270,315,360)) {
            val angle=Math.toRadians(degrees.toDouble())
            val outer=radius-7*density; val inner=outer-5*density
            canvas.drawLine(cx+cos(angle).toFloat()*inner,cy+sin(angle).toFloat()*inner,
                cx+cos(angle).toFloat()*outer,cy+sin(angle).toFloat()*outer,paint)
        }
        paint.style=Paint.Style.FILL; paint.textSize=AppTextUnits.pixels(resources, if(smallLabels==true) 9f else 10f)
        val endpointX=if(smallLabels==true) .62f else .69f
        val endpointY=if(smallLabels==true) .98f else .85f
        paint.textAlign=Paint.Align.CENTER
        canvas.drawText("0",cx-radius*endpointX,cy+radius*endpointY,paint)
        canvas.drawText(goal?.toInt()?.toString() ?: "—",cx+radius*endpointX,cy+radius*endpointY,paint)
    }
}
