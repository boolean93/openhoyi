package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The chosen instrument face, measured as fractions of its outside diameter. */
internal class BoilerTemperatureDial(context: Context, private val heading: TextView,
    private val value: TextView, private val target: TextView, private val pressure: TextView,
    private val power: HoyiSwitch, private val steam: Boolean) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val style = TaskProductVisualStyle(context)
    private val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    internal val faceFill = if (dark) style.homePage else 0xfffcfaf7.toInt()
    private val ink = if (dark) style.homeDialInk else Color.rgb(10, 30, 19)
    private val marks = if (dark) Color.rgb(129, 155, 143) else Color.rgb(160, 182, 149)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val icon = Path()
    private var currentTemperature: Float? = null
    private var targetTemperature: Float? = null
    private val touch = (48 * density).roundToInt()

    init {
        setWillNotDraw(false)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = if (dark) intArrayOf(style.homeDialBorder, style.surface, style.homeDialBorder)
            else intArrayOf(Color.WHITE, 0xfff5f3ef.toInt(), Color.WHITE)
        }
        elevation = 3 * density
        for (field in listOf(heading, value, target, pressure)) {
            field.gravity = Gravity.CENTER; field.setTextColor(ink)
            field.includeFontPadding = false
            field.setLineSpacing(0f, 1f)
            field.minimumHeight = 0; field.minHeight = 0
            field.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
            addView(field)
        }
        heading.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        value.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        target.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        pressure.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        power.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        power.setPaddingRelative(0, 0, 0, 0)
        addView(power)
    }

    fun present(current: Float?, goal: Float?) {
        if (currentTemperature == current && targetTemperature == goal) return
        currentTemperature = current; targetTemperature = goal; invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val diameter = minOf(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(diameter, diameter)
        val factor = AppTextUnits.pixels(resources, 16f) / (16f * density)
        fun set(field: TextView, width: Float, height: Float, fraction: Float) {
            val px = diameter * fraction * factor
            if (field.textSize != px) field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px)
            field.measure(exact((diameter * width).roundToInt()), exact((diameter * height).roundToInt()))
        }
        set(heading, .70f, .10f, .066f)
        set(value, .77f, .20f, .162f)
        set(target, .64f, .10f, .057f)
        set(pressure, .62f, .10f, .057f)
        power.setDialDiameter(diameter)
        val bottomPadding = (diameter * .013f).roundToInt()
        if (power.paddingBottom != bottomPadding) power.setPaddingRelative(0, 0, 0, bottomPadding)
        power.measure(exact(touch), exact(touch))
    }

    private fun exact(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        fun at(view: android.view.View, y: Float) {
            val x = (width - view.measuredWidth) / 2
            val t = (height * y - view.measuredHeight / 2f).roundToInt()
            view.layout(x, t, x + view.measuredWidth, t + view.measuredHeight)
        }
        at(heading, .29f)
        at(value, .442f)
        at(target, .721f)
        val x = (width - power.measuredWidth) / 2
        power.layout(x, target.top - touch, x + power.measuredWidth, target.top)
        at(pressure, .855f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = width.toFloat()
        val c = d / 2f
        // Face, rim and arc: reference face is ~96%, arc center radius ~44%.
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, d,
            if (dark) intArrayOf(style.surface, faceFill) else intArrayOf(0xfffdfbf8.toInt(), faceFill),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(c, c, d * .481f, paint)
        paint.shader = null
        val r = d * .442f
        bounds.set(c-r, c-r, c+r, c+r)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = d * .034f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = style.homeDialTrack
        canvas.drawArc(bounds, 140f, 260f, false, paint)
        val goal = targetTemperature
        val current = currentTemperature
        if (current != null && goal != null && goal > 0) {
            paint.color = style.homeDialProgress
            canvas.drawArc(bounds, 140f, 260f * (current / goal).coerceIn(0f, 1f), false, paint)
        }
        for (step in -12..12) {
            val angle = Math.toRadians(270.0 + step * 10)
            val major = step % 4 == 0
            val outer = d * .409f
            val inner = outer - d * (if (major) .026f else .017f)
            paint.color = if (major) ink else marks
            paint.strokeWidth = d * if (major) .0055f else .0035f
            val x = cos(angle).toFloat(); val y = sin(angle).toFloat()
            canvas.drawLine(c + x * inner, c + y * inner, c + x * outer, c + y * outer, paint)
        }
        paint.color = marks; paint.strokeWidth = d * .004f
        canvas.drawLine(d * .255f, d * .782f, d * .745f, d * .782f, paint)
        canvas.save()
        canvas.translate(c - d * .055f, d * .148f)
        canvas.scale(d * .11f / 100f, d * .11f / 100f)
        paint.color = if (dark) style.homeDialProgress else 0xff08602b.toInt()
        paint.strokeWidth = 8f; paint.strokeJoin = Paint.Join.ROUND
        if (steam) {
            icon.reset()
            icon.moveTo(20f,53f); icon.cubicTo(1f,48f,6f,26f,25f,25f)
            icon.cubicTo(28f,-5f,70f,-3f,75f,25f); icon.cubicTo(94f,25f,101f,48f,81f,53f)
            canvas.drawPath(icon,paint)
            for (x in listOf(34f, 50f, 66f)) {
                icon.reset(); icon.moveTo(x,48f); icon.cubicTo(x-11f,62f,x+7f,69f,x-2f,90f)
                canvas.drawPath(icon,paint)
            }
        } else {
            canvas.drawRoundRect(20f, 12f, 69f, 66f, 9f, 9f, paint)
            icon.reset(); icon.moveTo(70f,22f); icon.cubicTo(100f,16f,99f,57f,70f,55f)
            canvas.drawPath(icon,paint)
            canvas.drawLine(11f, 83f, 87f, 83f, paint)
        }
        canvas.restore()
    }
}
