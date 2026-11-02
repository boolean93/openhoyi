package io.openhoyi.mobile

import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button

/** Low emphasis without reducing the native button's touch target or keyboard feedback. */
internal fun Button.asTaskTextAction() {
    val activity = context as ThemedActivity
    setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(context.getColor(R.color.mobile_muted), context.getColor(R.color.mobile_accent))))
    val states = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused),
            HoyiUi.shape(activity, android.R.color.transparent, 8, R.color.mobile_accent))
        addState(intArrayOf(), HoyiUi.shape(activity, android.R.color.transparent, 8))
    }
    background = RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.mobile_accent_soft)), states,
        HoyiUi.shape(activity, android.R.color.white, 8))
}
