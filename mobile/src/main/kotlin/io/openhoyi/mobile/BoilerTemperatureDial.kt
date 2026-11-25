package io.openhoyi.mobile

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import androidx.core.content.res.ResourcesCompat
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.view.animation.LinearInterpolator
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The chosen instrument face, measured as fractions of its outside diameter. */
internal class BoilerTemperatureDial(context: Context, private val heading: TextView,
    private val value: TextView, private val target: TextView, private val pressure: TextView,
    private val power: HoyiSwitch, private val steam: Boolean,
    decrease: View? = null, increase: View? = null,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val style = TaskProductVisualStyle(context)
    private val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    internal val faceFill = if (dark) 0xff152322.toInt() else 0xfff5faf7.toInt()
    private val ink = if (dark) style.homeDialInk else 0xff102f2c.toInt()
    private val marks = if (dark) 0xff839f98.toInt() else 0xff90b1a6.toInt()
    private val ruleColor = if (dark) 0xff456360.toInt() else 0xffc9dfd6.toInt()
    private val stripFill = if (dark) 0xff254440.toInt() else 0xffe4f4ed.toInt()
    private val arcInk = if (dark) style.homeDialProgress else 0xff00bd69.toInt()
    private val decrease = decrease
    private val increase = increase
    private val steps = listOfNotNull(decrease, increase)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val icon = Path()
    private var currentTemperature: Float? = null
    private var lastReceivedTemperature: Float? = null
    private var lastSession: Any? = null
    private var warming: ValueAnimator? = null
    private var presented = false
    private var targetTemperature: Float? = null
    private val touch = (48 * density).roundToInt()

    init {
        setWillNotDraw(false)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(faceFill)
            setStroke(density.roundToInt().coerceAtLeast(1), ruleColor)
        }
        elevation = 2 * density
        for (field in listOf(heading, value, target, pressure)) {
            field.gravity = Gravity.CENTER
            field.setTextColor(ink)
            field.includeFontPadding = false
            field.setLineSpacing(0f, 1f)
            field.minimumHeight = 0
            field.minHeight = 0
            field.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
            addView(field)
        }
        heading.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        value.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        target.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        pressure.typeface = Typeface.create("sans-serif", Typeface.NORMAL)

        power.gravity = Gravity.CENTER
        power.setPaddingRelative(0, 0, 0, 0)
        addView(power)
        val buttonInk = if (dark) 0xffc9f2e5.toInt() else 0xff104c3e.toInt()
        for (step in steps) {
            step.background = RippleDrawable(
                ColorStateList.valueOf(if (dark) 0x44a4d4c7 else 0x33164e40),
                null,
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) },
            )
            step.minimumWidth = touch
            step.minimumHeight = touch
            step.setPadding(0, 0, 0, 0)
            step.isFocusable = true
            if (step is TextView) {
                step.gravity = Gravity.CENTER
                step.includeFontPadding = false
                step.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                step.setTextColor(ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(if (dark) 0xff8aa8a0.toInt() else 0xff63847a.toInt(), buttonInk),
                ))
            }
            addView(step)
        }
    }

    private val numeralFace by lazy { ResourcesCompat.getFont(context, R.font.task_chinese_bold) }

    fun formatReading(reading: String, unitStart: Int): CharSequence = SpannableString(reading).apply {
        // The existing bundled face has the reference's broad 1 foot, rounded 9 tail
        // and diagonal 2 contour. Restrict it to the digits/decimal, not the °C unit.
        numeralFace?.let { face ->
            setSpan(object : MetricAffectingSpan() {
                private fun apply(paint: TextPaint) {
                    paint.typeface = face
                    paint.textSize *= .95f
                    paint.textScaleX *= 1.03f
                }
                override fun updateMeasureState(paint: TextPaint) = apply(paint)
                override fun updateDrawState(paint: TextPaint) = apply(paint)
            }, 0, unitStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        setSpan(RelativeSizeSpan(.53f), unitStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** A null session means snap (including held/stale data), not a new ramp. */
    fun present(current: Float?, goal: Float?, session: Any? = null) {
        val old = lastReceivedTemperature
        if (presented && old == current && targetTemperature == goal && lastSession == session) return
        presented = true
        val start = currentTemperature
        val ramp = isAttachedToWindow && ValueAnimator.areAnimatorsEnabled() &&
            homeBoilerShouldAnimate(old, current, targetTemperature, goal, lastSession, session) && start != null
        warming?.cancel()
        warming = null
        lastSession = session
        lastReceivedTemperature = current
        val goalChanged = targetTemperature != goal
        targetTemperature = goal
        if (ramp) {
            warming = ValueAnimator.ofFloat(checkNotNull(start), checkNotNull(current)).apply {
                duration = 190
                interpolator = LinearInterpolator()
                addUpdateListener { renderTemperature(it.animatedValue as Float) }
                start()
            }
        } else {
            renderTemperature(current)
            if (goalChanged) invalidate()
        }
    }

    private fun renderTemperature(current: Float?) {
        currentTemperature = current
        val rendered = current?.let { String.format(Locale.ROOT, "%.1f °C", it) } ?: "—"
        if (value.text.toString() != rendered) {
            value.text = if (current == null) rendered else formatReading(rendered, rendered.indexOf(' '))
        }
        invalidate()
    }

    fun suspendMotion() {
        warming?.cancel()
        warming = null
        lastSession = null
        // Restart from actual after a page pause or a reattach, never from a stale ramp.
        renderTemperature(lastReceivedTemperature)
    }

    override fun onDetachedFromWindow() {
        suspendMotion()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val diameter = minOf(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(diameter, diameter)
        fun setTextSize(field: TextView, fraction: Float, minimumSp: Float = 0f) {
            val px = AppTextUnits.pixels(resources, maxOf(diameter * fraction / density, minimumSp))
            if (field.textSize != px) field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px)
        }
        fun measureField(field: TextView, width: Float, fraction: Float, minimumSp: Float) {
            setTextSize(field, fraction, minimumSp)
            field.measure(exact((diameter * width).roundToInt()), MeasureSpec.UNSPECIFIED)
        }
        measureField(heading, .35f, .057f, 9f)
        measureField(value, .81f, .181f, 19f)
        measureField(target, if (steps.isEmpty()) .66f else .38f, .055f, 9f)
        measureField(pressure, .59f, .063f, 9f)
        power.setDialDiameter(diameter)
        // Native Switch lays its ink against END even without a label. Keep the small
        // artwork centered within a full 48dp keyboard, touch and accessibility target.
        val endPadding = (touch - power.switchMinWidth).coerceAtLeast(0) / 2
        if (power.paddingEnd != endPadding) power.setPaddingRelative(0, 0, endPadding, 0)
        power.measure(exact(touch), exact(touch))
        for (step in steps) {
            if (step is TextView) setTextSize(step, .108f)
            step.measure(exact(touch), exact(touch))
        }
    }

    private fun exact(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        fun at(view: View, y: Float, x: Float = .5f) {
            val offset = (width * x - view.measuredWidth / 2f).roundToInt()
                .coerceIn(0, (width - view.measuredWidth).coerceAtLeast(0))
            val t = (height * y - view.measuredHeight / 2f).roundToInt()
            view.layout(offset, t, offset + view.measuredWidth, t + view.measuredHeight)
        }
        val actionY = .650f
        // In a narrow dial the hit rectangle can extend outside the visible mint
        // bar; keep it disjoint from the power hit rectangle above it.
        at(heading, .244f, .452f)
        at(power, minOf(.249f, actionY - touch.toFloat() / height.coerceAtLeast(1))
            .coerceAtLeast(touch / 2f / height.coerceAtLeast(1)), .712f)
        at(value, .444f)
        at(target, actionY)
        decrease?.let { at(it, actionY, .232f) }
        increase?.let { at(it, actionY, .768f) }
        at(pressure, .843f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = width.toFloat()
        if (d <= 0f) return
        val c = d / 2f
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = LinearGradient(0f, 0f, 0f, d,
            if (dark) intArrayOf(0xff1b302e.toInt(), faceFill) else intArrayOf(0xfffbfefa.toInt(), faceFill),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(c, c, d * .494f, paint)
        paint.shader = null
        val r = d * .459f
        bounds.set(c-r, c-r, c+r, c+r)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d * .026f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = style.homeDialTrack
        canvas.drawArc(bounds, 147f, 246f, false, paint)
        val fraction = homeBoilerArcFraction(currentTemperature, targetTemperature)
        if (fraction > 0f) {
            paint.color = arcInk
            canvas.drawArc(bounds, 147f, 246f * fraction, false, paint)
        }
        for (step in -12..12) {
            val angle = Math.toRadians(270.0 + step * 10)
            val major = step == 0 || kotlin.math.abs(step) == 5 || kotlin.math.abs(step) == 9
            val outer = d * .435f
            val inner = outer - d * (if (major) .024f else .015f)
            paint.color = if (major) ink else marks
            paint.strokeWidth = d * if (major) .005f else .003f
            val x = cos(angle).toFloat()
            val y = sin(angle).toFloat()
            canvas.drawLine(c + x * inner, c + y * inner, c + x * outer, c + y * outer, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = stripFill
        // Keep a wrapped target comfortably inside the strip at small diameters.
        val halfStrip = maxOf(d * .071f, target.measuredHeight / 2f + density)
        canvas.drawRoundRect(d * .143f, d * .65f - halfStrip, d * .857f, d * .65f + halfStrip,
            halfStrip, halfStrip, paint)

        paint.style = Paint.Style.STROKE
        paint.color = ruleColor
        paint.strokeWidth = d * .003f
        canvas.drawLine(d * .202f, d * .319f, d * .798f, d * .319f, paint)
        canvas.drawLine(d * .202f, d * .762f, d * .798f, d * .762f, paint)
        canvas.drawLine(d * .615f, d * .216f, d * .615f, d * .282f, paint)
        if (steps.isNotEmpty()) {
            canvas.drawLine(d * .309f, d * .61f, d * .309f, d * .69f, paint)
            canvas.drawLine(d * .691f, d * .61f, d * .691f, d * .69f, paint)
        }
        canvas.save()
        canvas.translate(d * .225f, d * .218f)
        canvas.scale(d * .072f / 100f, d * .064f / 100f)
        paint.color = ink
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
