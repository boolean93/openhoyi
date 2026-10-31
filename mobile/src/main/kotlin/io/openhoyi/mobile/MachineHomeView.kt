package io.openhoyi.mobile

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** Pure home layout: the machine image never contains telemetry or controls. */
internal class MachineHomeView(context: Context, private val spacious: Boolean) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val boilers = LinearLayout(context).apply {
        orientation = if (spacious) VERTICAL else HORIZONTAL
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (spacious) {
            addView(ImageView(context).apply {
                tag = "product/home/machine-hero"
                setImageResource(R.drawable.hoyi_machine)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                contentDescription = context.getString(R.string.home_machine_description)
            }, LayoutParams(0, -1, 45f).apply { marginEnd = dp(16) })
        }
        addView(boilers, LayoutParams(if (spacious) 0 else -1, if (spacious) -1 else -2,
            if (spacious) 55f else 0f))
    }

    fun addBoiler(card: View) {
        boilers.addView(card, if (spacious) LayoutParams(-1, 0, 1f).apply {
            if (boilers.childCount > 0) topMargin = dp(8)
        } else LayoutParams(0, -2, 1f).apply {
            if (boilers.childCount > 0) marginStart = dp(8)
        })
    }
}
