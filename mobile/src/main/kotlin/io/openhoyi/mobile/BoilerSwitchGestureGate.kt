package io.openhoyi.mobile

import android.view.KeyEvent

/** An already-started native gesture cannot choose a new source/bit or upgrade a stale touch. */
internal object BoilerSwitchGestureGate {
    // KeyEvent.isConfirmKey is hidden. Match the platform View confirmation keys.
    fun isConfirmKey(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_NUMPAD_ENTER -> true
        else -> false
    }

    fun allows(rendered:Any?,current:Any?,down:Any?,touching:Boolean):Boolean =
        rendered!=null && rendered==current && (!touching || down==rendered)
}
