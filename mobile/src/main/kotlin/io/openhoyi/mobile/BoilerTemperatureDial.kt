package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
    private val value: TextView, target: TextView) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val ink = Color.rgb(21,26,23)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headingSize=heading.textSize
    private val headingLine=heading.lineHeight
    private val valueLine=value.lineHeight
    private val valueMin=value.minimumHeight
    private val valueMaxSp=(value.autoSizeMaxTextSize/resources.displayMetrics.scaledDensity).toInt()
    private val targetSize=target.textSize
    private val targetLine=target.lineHeight
    private val words=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
    private val spacer=android.widget.Space(context)
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
            shape = GradientDrawable.OVAL; setColor(Color.WHITE)
            setStroke(density.toInt().coerceAtLeast(1),0xffedf0ee.toInt())
        }
        elevation = 2 * density
        for (field in listOf(heading, value)) {
            field.gravity = Gravity.CENTER; field.setTextColor(ink)
            words.addView(field, LinearLayout.LayoutParams(-1,-2))
        }
        // Preserve the title/current baselines while the target moves to the scale band.
        words.addView(spacer,LinearLayout.LayoutParams(1,target.lineHeight))
        addView(words, LayoutParams(-1,-1).apply {
            val inset=(25*density).toInt(); setMargins(inset,inset,inset,inset)
        })
        targetBand=TextView(context).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,target.textSize)
            typeface=target.typeface; includeFontPadding=false; gravity=Gravity.CENTER
            setTextColor(ink); setCompatibleLineHeight(target.lineHeight)
        }
        addView(targetBand,LayoutParams(-1,-2,Gravity.BOTTOM).apply {
            leftMargin=(24*density).toInt(); rightMargin=leftMargin; bottomMargin=(30*density).toInt()
        })
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
        val small=MeasureSpec.getSize(widthMeasureSpec)<180*density
        if(smallLabels!=small || small && lastSmallDiameter!=MeasureSpec.getSize(widthMeasureSpec)) {
            lastSmallDiameter=MeasureSpec.getSize(widthMeasureSpec)
            smallLabels=small
            val scaled=resources.displayMetrics.scaledDensity
            heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,if(small) 12*scaled else headingSize)
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
            heading.layoutParams=LinearLayout.LayoutParams(-1,if(small) (diameter*.145f).toInt() else -2)
            value.layoutParams=LinearLayout.LayoutParams(-1,if(small) (diameter*.26f).toInt() else -2)
            words.gravity=if(small) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
            words.layoutParams=LayoutParams(-1,if(small) (diameter*.405f).toInt() else -1).apply {
                val inset=((if(small) 12 else 25)*density).toInt()
                setMargins(inset,if(small) (diameter*.17f).toInt() else (25*density).toInt(),inset,if(small) 0 else inset)
            }
            spacer.layoutParams=LinearLayout.LayoutParams(1,if(small) 0 else targetLine)
            targetBand.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,if(small) 12*scaled else targetSize)
            if(small) {
                targetBand.setSingleLine(true);targetBand.setLineSpacing(0f,1f)
                targetBand.setAutoSizeTextTypeUniformWithConfiguration(10,12,1,android.util.TypedValue.COMPLEX_UNIT_SP)
            } else {
                targetBand.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                targetBand.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,targetSize)
                targetBand.setCompatibleLineHeight(targetLine)
            }
            targetBand.layoutParams=LayoutParams(-1,if(small) (diameter*.16f).toInt() else -2,Gravity.BOTTOM).apply {
                leftMargin=((if(small) 12 else 24)*density).toInt();rightMargin=leftMargin
                bottomMargin=if(small) (diameter*.246f).toInt() else (30*density).toInt()
            }
        }
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx=width/2f; val cy=height/2f; val radius=minOf(width,height)/2f-11*density
        val bounds=RectF(cx-radius,cy-radius,cx+radius,cy+radius)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=8*density; paint.strokeCap=Paint.Cap.ROUND
        paint.color=0xffe2e6e3.toInt(); canvas.drawArc(bounds,135f,270f,false,paint)
        val goal=targetTemperature; val current=currentTemperature
        if(current!=null && goal!=null && goal>0) {
            paint.color=0xff00c853.toInt()
            canvas.drawArc(bounds,135f,270f*(current/goal).coerceIn(0f,1f),false,paint)
        }
        paint.color=ink; paint.strokeWidth=1.5f*density
        for(degrees in listOf(180,225,270,315,360)) {
            val angle=Math.toRadians(degrees.toDouble())
            val outer=radius-7*density; val inner=outer-5*density
            canvas.drawLine(cx+cos(angle).toFloat()*inner,cy+sin(angle).toFloat()*inner,
                cx+cos(angle).toFloat()*outer,cy+sin(angle).toFloat()*outer,paint)
        }
        paint.style=Paint.Style.FILL; paint.textSize=(if(smallLabels==true) 9 else 10)*density*resources.configuration.fontScale
        val endpointX=if(smallLabels==true) .62f else .69f
        val endpointY=if(smallLabels==true) .98f else .85f
        paint.textAlign=Paint.Align.CENTER
        canvas.drawText("0",cx-radius*endpointX,cy+radius*endpointY,paint)
        canvas.drawText(goal?.toInt()?.toString() ?: "—",cx+radius*endpointX,cy+radius*endpointY,paint)
    }
}
