package io.openhoyi.mobile

import android.content.Context
import android.graphics.Rect
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button

/** A relative change belongs to the device and observed target visible when it was pressed. */
internal class HomeBoilerTargetButton(context: Context, private val currentIdentity: () -> Any?) : Button(context) {
    private class Press(val identity: Any?, var valid: Boolean = true)
    private var touch: Press? = null
    private var key: Press? = null
    private var postedClick: Press? = null

    var renderedIdentity: Any? = null
        set(value) {
            if (field != value) {
                touch?.valid = false
                key?.valid = false
                postedClick?.valid = false
                field = value
            }
            isEnabled = value != null
        }

    private fun capturePress() = Press(renderedIdentity.takeIf { it == currentIdentity() })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) touch = capturePress()
        val released = if (event.actionMasked == MotionEvent.ACTION_UP) touch else null
        if (released != null) postedClick = released
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) touch = null
        if (released != null && postedClick === released) {
            // View posts performClick before this cleanup; a cancelled press posts no click.
            if (!post { if (postedClick === released) postedClick = null }) postedClick = null
        }
        return handled
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (BoilerSwitchGestureGate.isConfirmKey(keyCode) && event.repeatCount == 0) key = capturePress()
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val handled = super.onKeyUp(keyCode, event)
        if (BoilerSwitchGestureGate.isConfirmKey(keyCode)) key = null
        return handled
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if (!focused) key?.valid = false
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
    }

    override fun performClick(): Boolean {
        val press = postedClick ?: touch ?: key
        postedClick = null
        if (!isEnabled || press?.valid == false || !BoilerSwitchGestureGate.allows(
                renderedIdentity, currentIdentity(), press?.identity, touching = press != null)) return false
        return super.performClick()
    }
}
