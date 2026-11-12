package io.openhoyi.mobile

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.animation.PathInterpolator
import android.widget.Switch

/** Native checked/listener, keyboard, accessibility and drag semantics; only the ink is custom.
 * Transparent native drawables retain Switch's own measurement and gesture geometry. No reflection.
 */
internal class HoyiSwitch(context: Context) : Switch(context) {
    private var ready = false
    private var position = 0f
    private var motion: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capsule = RectF()
    private var touching = false
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    init {
        showText = false
        splitTrack = false
        thumbTintList = null; trackTintList = null
        thumbDrawable = spacer(20, 22)
        trackDrawable = spacer(40, 22)
        switchMinWidth = dp(40f).toInt()
        minimumWidth = dp(48f).toInt(); minWidth = minimumWidth
        minimumHeight = dp(48f).toInt(); minHeight = minimumHeight
        switchPadding = dp(16f).toInt()
        position = if (isChecked) 1f else 0f
        ready = true
    }
    private fun spacer(width: Int, height: Int) = GradientDrawable().apply {
        setColor(Color.TRANSPARENT); setSize(dp(width.toFloat()).toInt(), dp(height.toFloat()).toInt())
    }
    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        // A persistence listener may synchronously reject the change. Read the final native state.
        if (ready) {
            // Only custom ink animates. Native gesture geometry settles immediately.
            super.jumpDrawablesToCurrentState()
            settle()
        }
    }
    private fun settle() {
        motion?.cancel()
        val target = if (isChecked) 1f else 0f
        if (!isAttachedToWindow || !isLaidOut || !ValueAnimator.areAnimatorsEnabled()) {
            position = target; invalidate(); return
        }
        motion = ValueAnimator.ofFloat(position, target).apply {
            duration = 140L
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { position = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        if (ready) { motion?.cancel(); position = if (isChecked) 1f else 0f; invalidate() }
    }
    override fun onDetachedFromWindow() {
        touching = false
        motion?.cancel(); position = if (isChecked) 1f else 0f
        super.onDetachedFromWindow()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // Finish an interrupted transition before native hit-testing so invisible and
            // visible thumb positions agree when a second gesture starts.
            motion?.cancel(); position = if (isChecked) 1f else 0f; invalidate()
            touching = isEnabled
        }
        // Switch alone owns slop, thumb hit-testing, clamping, fling and cancellation.
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            touching = false
            settle()
        }
        return handled
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        // Native layout owns gravity, padding, RTL and integer density rounding too.
        capsule.set(trackDrawable.bounds)
        val left = capsule.left
        val top = capsule.top
        val halfThumb = thumbDrawable.intrinsicWidth / 2f
        val travel = capsule.width() - thumbDrawable.intrinsicWidth
        if (touching) {
            // Switch.draw has already laid out its transparent thumb from the authoritative
            // native drag position. Reading drawable bounds avoids duplicating private rules.
            val nativePosition = ((thumbDrawable.bounds.exactCenterX() - left - halfThumb) / travel).coerceIn(0f, 1f)
            position = if (rtl) 1f - nativePosition else nativePosition
        }
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val off = Color.parseColor(if (dark) "#485354" else "#CDD3D1")
        val on = Color.parseColor(if (dark) "#438C70" else "#61A98A")
        val fill = blend(off, on, position)
        paint.alpha = if (isEnabled) 255 else 100
        // A fine dark upper lip and lighter lower face suggest a shallow inset, not elevation.
        paint.shader = LinearGradient(0f, top, 0f, capsule.bottom,
            intArrayOf(blend(fill, Color.BLACK, 0.12f), fill, blend(fill, Color.WHITE, 0.04f)),
            floatArrayOf(0f, 0.32f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(capsule, dp(11f), dp(11f), paint)
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(0.65f)
        paint.color = blend(fill, if (dark) Color.WHITE else Color.BLACK, 0.22f)
        paint.alpha = if (isEnabled) 255 else 100
        canvas.drawRoundRect(capsule, dp(11f), dp(11f), paint)
        paint.style = Paint.Style.FILL
        val x = left + halfThumb + travel * if (rtl) 1f - position else position
        paint.color = Color.BLACK; paint.alpha = if (isEnabled) 28 else 10
        canvas.drawCircle(x, top + dp(11.7f), dp(9.3f), paint)
        paint.shader = LinearGradient(0f, top + dp(2f), 0f, top + dp(20f),
            Color.WHITE, Color.parseColor("#E5EAE7"), Shader.TileMode.CLAMP)
        paint.alpha = if (isEnabled) 255 else 130
        canvas.drawCircle(x, top + dp(11f), dp(9f), paint)
        paint.shader = null; paint.alpha = 255
    }
    private fun blend(a: Int, b: Int, amount: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * amount).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * amount).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * amount).toInt())
}
