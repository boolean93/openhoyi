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
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.PathInterpolator
import android.widget.Switch

/** Native checked/listener, keyboard, accessibility and drag semantics; only the ink is custom.
 * Transparent native drawables retain Switch's own measurement and gesture geometry. No reflection.
 */
internal class HoyiSwitch(context: Context, private val interactive: Boolean = true) : Switch(context) {
    private var ready = false
    private var position = 0f
    private var motion: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capsule = RectF()
    /** Touch and keyboard keep their hit target. Accessibility readback uses a separate view. */
    var readbackRetry: (() -> Unit)?=null
        set(value) {
            val accessibilityModeChanged = (field == null) != (value == null)
            if(value!=null) {
                if(touching)nativeGestureInvalid=true
                queuedNativeClick?.invalid=true
                keyboardPress?.toggle?.invalid = true
            }
            field=value
            if (accessibilityModeChanged) {
                importantForAccessibility = if (value == null) IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    else IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
    var authoritativeToggleIdentity:(() -> Any?)?=null
    var drawnToggleIdentity:Any?=null
        set(value) {
            if(field!=value) {
                if(touching)nativeGestureInvalid=true
                queuedNativeClick?.invalid=true
                keyboardPress?.toggle?.invalid = true
            }
            field=value
        }
    private var nativeGestureInvalid=false
    private class NativeClick(val down:Any?,var invalid:Boolean=false)
    private var queuedNativeClick:NativeClick?=null
    private var nativeTouchIdentity:Any?=null
    private var readbackGesture=false
    private var readbackTap:(()->Unit)?=null
    private class KeyboardPress(
        val keyCode: Int,
        val retry: (() -> Unit)?,
        val toggle: NativeClick,
        var cancelled: Boolean = false,
    )
    private var keyboardPress: KeyboardPress? = null

    // KeyEvent.isConfirmKey is hidden. Match the platform View confirmation keys.
    private fun isConfirmKey(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_NUMPAD_ENTER -> true
        else -> false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isConfirmKey(keyCode) || !event.hasNoModifiers() ||
            authoritativeToggleIdentity == null && readbackRetry == null) {
            return super.onKeyDown(keyCode, event)
        }
        if (event.repeatCount == 0) {
            val identity = drawnToggleIdentity
            keyboardPress = KeyboardPress(keyCode, readbackRetry, NativeClick(identity,
                !BoilerSwitchGestureGate.allows(identity, authoritativeToggleIdentity?.invoke(), identity, touching = true)))
        }
        if (keyboardPress?.retry == null) super.onKeyDown(keyCode, event)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isConfirmKey(keyCode)) return super.onKeyUp(keyCode, event)
        val press = keyboardPress ?: return if (authoritativeToggleIdentity == null && readbackRetry == null) {
            super.onKeyUp(keyCode, event)
        } else true
        if (press.keyCode != keyCode) return true
        keyboardPress = null
        if (press.cancelled || event.isCanceled || !event.hasNoModifiers() || !isEnabled) {
            isPressed = false
            return true
        }
        press.retry?.let { retry ->
            retry()
            return true
        }
        val toggling = press.toggle
        if (toggling.invalid || readbackRetry != null ||
            !BoilerSwitchGestureGate.allows(drawnToggleIdentity, authoritativeToggleIdentity?.invoke(),
                toggling.down, touching = true)) {
            isPressed = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        if (!focused) keyboardPress?.cancelled = true
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
    }

    override fun performClick():Boolean {
        // Android View may post its click after touch-up and after a new BLE report has rendered.
        val queued=queuedNativeClick
        if(queued!=null) {
            queuedNativeClick=null
            val current=authoritativeToggleIdentity
            if(queued.invalid || readbackRetry!=null || current==null ||
                !BoilerSwitchGestureGate.allows(drawnToggleIdentity,current(),queued.down,touching=true))return false
            return super.performClick()
        }
        val retry=readbackRetry
        if(retry!=null && isEnabled) {
            retry()
            return true
        }
        authoritativeToggleIdentity?.let { current ->
            val rendered=drawnToggleIdentity
            if(nativeGestureInvalid && touching || !BoilerSwitchGestureGate.allows(rendered,current(),nativeTouchIdentity,touching))return false
        }
        return super.performClick()
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (readbackRetry != null) {
            info.isClickable = false
            info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (readbackRetry != null && action == AccessibilityNodeInfo.ACTION_CLICK) return false
        return super.performAccessibilityAction(action, arguments)
    }

    private var touching = false
    // Scoped to the two reference-built Settings panels; the native drag geometry is unchanged.
    fun useSettingsPanelInk() { settingsPanelInk = true; invalidate() }
    private var settingsPanelInk = false
    private val palette = AppPalette(context)
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
    private var dialTrackWidth = 0
    private var dialTrackHeight = 0
    fun setDialDiameter(diameter: Int) {
        val w = (diameter * .128f).toInt().coerceAtMost(dp(46f).toInt()).coerceAtLeast(3)
        val h = (diameter * .070f).toInt().coerceAtLeast(2)
        if (w == dialTrackWidth && h == dialTrackHeight) return
        dialTrackWidth = w; dialTrackHeight = h
        fun transparent(width: Int) = GradientDrawable().apply {
            setColor(Color.TRANSPARENT); setSize(width, h)
        }
        thumbDrawable = transparent((h * .89f).toInt().coerceIn(1, w / 2))
        trackDrawable = transparent(w)
        switchMinWidth = w
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
        readbackGesture=false;readbackTap=null;nativeTouchIdentity=null;queuedNativeClick=null
        keyboardPress?.cancelled = true
        touching = false
        motion?.cancel(); position = if (isChecked) 1f else 0f
        super.onDetachedFromWindow()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {
            readbackTap=readbackRetry
            nativeTouchIdentity=drawnToggleIdentity
            nativeGestureInvalid=false
            val current=authoritativeToggleIdentity
            readbackGesture=readbackTap!=null || current!=null &&
                (nativeTouchIdentity==null || current()!=nativeTouchIdentity)
        }
        if(!readbackGesture && event.actionMasked!=MotionEvent.ACTION_DOWN && touching &&
            (nativeGestureInvalid || readbackRetry!=null || authoritativeToggleIdentity?.let { current ->
                !BoilerSwitchGestureGate.allows(drawnToggleIdentity,current(),nativeTouchIdentity,touching=true)
            }==true)) {
            // Freshness can change while a native drag is in progress. Cancel rather than
            // treating the same touch-up as either a toggle or a new read-only retry.
            val cancelled=MotionEvent.obtain(event)
            try {cancelled.action=MotionEvent.ACTION_CANCEL;super.onTouchEvent(cancelled)}
            finally {cancelled.recycle()}
            touching=false
            readbackGesture=true
        }
        if(readbackGesture) {
            when(event.actionMasked) {
                MotionEvent.ACTION_MOVE -> if(event.x<0 || event.x>=width || event.y<0 || event.y>=height)readbackTap=null
                MotionEvent.ACTION_POINTER_DOWN -> readbackTap=null
                MotionEvent.ACTION_UP -> {
                    val tap=readbackTap
                    readbackGesture=false;readbackTap=null
                    if(event.x>=0 && event.x<width && event.y>=0 && event.y<height)tap?.invoke()
                }
                MotionEvent.ACTION_CANCEL -> {readbackGesture=false;readbackTap=null}
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // Finish an interrupted transition before native hit-testing so invisible and
            // visible thumb positions agree when a second gesture starts.
            motion?.cancel(); position = if (isChecked) 1f else 0f; invalidate()
            touching = isEnabled
        }
        // Switch alone owns slop, thumb hit-testing, clamping, fling and cancellation.
        val click=if(event.actionMasked==MotionEvent.ACTION_UP && touching && authoritativeToggleIdentity!=null)
            NativeClick(nativeTouchIdentity,nativeGestureInvalid).also {queuedNativeClick=it} else null
        val handled = super.onTouchEvent(event)
        if(click!=null && queuedNativeClick===click) {
            // Clear after View's performClick post. Drag commits have no posted click.
            if(!post {if(queuedNativeClick===click)queuedNativeClick=null})queuedNativeClick=null
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            touching = false
            settle()
        }
        return handled
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fullInk = isEnabled && readbackRetry == null
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
        if (dialTrackHeight > 0) {
            val fill = if (dark) blend(0xff58716c.toInt(), 0xff187358.toInt(), position)
                else blend(0xffb0c4bc.toInt(), 0xff145442.toInt(), position)
            val radius = capsule.height() / 2
            paint.color = fill
            paint.alpha = if (fullInk) 255 else 140
            canvas.drawRoundRect(capsule, radius, radius, paint)
            val x = left + halfThumb + travel * if (rtl) 1f - position else position
            paint.color = Color.BLACK; paint.alpha = 28
            canvas.drawCircle(x, capsule.centerY() + dp(.5f), radius * .90f, paint)
            paint.color = 0xfffafaf7.toInt(); paint.alpha = if (fullInk) 255 else 215
            canvas.drawCircle(x, capsule.centerY(), radius * .89f, paint)
            paint.alpha = 255
            return
        }
        val fill = if (settingsPanelInk && !dark && palette.choice == AppThemePalette.CLASSIC)
            blend(0xffacafb0.toInt(), 0xff117a61.toInt(), position)
        else blend(palette.switchOff, palette.switchOn, position)
        paint.alpha = if (fullInk) 255 else 100
        // A fine dark upper lip and lighter lower face suggest a shallow inset, not elevation.
        paint.shader = LinearGradient(0f, top, 0f, capsule.bottom,
            intArrayOf(blend(fill, Color.BLACK, 0.12f), fill, blend(fill, Color.WHITE, 0.04f)),
            floatArrayOf(0f, 0.32f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(capsule, dp(11f), dp(11f), paint)
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(0.65f)
        paint.color = blend(fill, if (dark) Color.WHITE else Color.BLACK, 0.22f)
        paint.alpha = if (fullInk) 255 else 100
        canvas.drawRoundRect(capsule, dp(11f), dp(11f), paint)
        paint.style = Paint.Style.FILL
        val x = left + halfThumb + travel * if (rtl) 1f - position else position
        paint.color = Color.BLACK; paint.alpha = if (fullInk) 28 else 10
        canvas.drawCircle(x, top + dp(11.7f), dp(9.3f), paint)
        paint.shader = LinearGradient(0f, top + dp(2f), 0f, top + dp(20f),
            Color.WHITE, Color.parseColor("#E5EAE7"), Shader.TileMode.CLAMP)
        paint.alpha = if (fullInk) 255 else 130
        canvas.drawCircle(x, top + dp(11f), dp(9f), paint)
        paint.shader = null; paint.alpha = 255
    }
    private fun blend(a: Int, b: Int, amount: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * amount).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * amount).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * amount).toInt())
}
