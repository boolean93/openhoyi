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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!spacious || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        // The image keeps its normal 360dp presence, while real boiler text may
        // need more space at large font scales. Both equal-height cards must fit
        // the taller card's natural content before the weighted layout runs.
        val availableWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - dp(16)).coerceAtLeast(0)
        val cardWidth = availableWidth * 55 / 100
        var naturalCardHeight = 0
        var verticalMargins = 0
        for (index in 0 until boilers.childCount) {
            val card = boilers.getChildAt(index)
            val params = card.layoutParams as LayoutParams
            card.measure(MeasureSpec.makeMeasureSpec((cardWidth - params.leftMargin - params.rightMargin).coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            naturalCardHeight = maxOf(naturalCardHeight, card.measuredHeight)
            verticalMargins += params.topMargin + params.bottomMargin
        }
        val desiredHeight = maxOf(dp(360), naturalCardHeight * boilers.childCount + verticalMargins + paddingTop + paddingBottom)
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
    }

    fun addBoiler(card: View) {
        boilers.addView(card, if (spacious) LayoutParams(-1, 0, 1f).apply {
            if (boilers.childCount > 0) topMargin = dp(8)
        } else LayoutParams(0, -2, 1f).apply {
            if (boilers.childCount > 0) marginStart = dp(8)
        })
    }
}
