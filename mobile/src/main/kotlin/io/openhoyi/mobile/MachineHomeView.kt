package io.openhoyi.mobile

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** Pure home layout: the machine image never contains telemetry or controls. */
internal class MachineHomeView(context: Context, private val spacious: Boolean) : LinearLayout(context) {
    private var machineImage: ImageView? = null
    private var inlinePortrait = false
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val portrait = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    private val boilers = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    init {
        orientation = if(portrait) VERTICAL else HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if(portrait) {
            addView(ImageView(context).apply {
                machineImage=this
                tag="product/home/machine-hero"; setImageResource(R.drawable.hoyi_machine)
                scaleType=ImageView.ScaleType.FIT_CENTER
                contentDescription=context.getString(R.string.home_machine_description)
            },LayoutParams(-1,dp(156)).apply {bottomMargin=dp(12)})
        }
        if (spacious) setPadding(dp(40),0,dp(40),0)
        if (spacious) {
            addView(ImageView(context).apply {
                machineImage=this
                tag = "product/home/machine-hero"
                setImageResource(R.drawable.hoyi_machine)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                contentDescription = context.getString(R.string.home_machine_description)
            }, LayoutParams(0, -1, 39f).apply { marginEnd = dp(16) })
        }
        addView(boilers, LayoutParams(if (spacious) 0 else -1, if (spacious) -1 else -2,
            if (spacious) 61f else 0f))
    }

    fun setPortraitHeroHeight(height: Int) {
        if (!portrait || inlinePortrait) return
        machineImage?.let { image ->
            if (image.layoutParams.height != height) {
                image.layoutParams = image.layoutParams.apply { this.height = height }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (portrait && !spacious) {
            val width = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
            inlinePortrait = HomePortraitBudget.inlineMachine((width / density).toInt(), resources.configuration.fontScale)
            orientation = if (inlinePortrait) HORIZONTAL else VERTICAL
            machineImage?.let { image ->
                (image.layoutParams as LayoutParams).apply {
                    this.width = if (inlinePortrait) dp(112) else -1
                    if (inlinePortrait) height = dp(160)
                    marginEnd = if (inlinePortrait) dp(16) else 0
                    bottomMargin = if (inlinePortrait) 0 else dp(12)
                }
            }
            (boilers.layoutParams as LayoutParams).apply {
                this.width = if (inlinePortrait) 0 else -1
                height = -2
                weight = if (inlinePortrait) 1f else 0f
            }
        }
        if (!spacious || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        // The machine fits the real content viewport without scaling past its bounds; boiler text may
        // need more space at large font scales. Both equal-height cards must fit
        // the taller card's natural content before the weighted layout runs.
        val availableWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - dp(16)).coerceAtLeast(0)
        val cardWidth = availableWidth * 61 / 100 / 2
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
        val desiredHeight = maxOf(dp(340), naturalCardHeight + verticalMargins + paddingTop + paddingBottom)
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        machineImage?.apply {
            layoutParams.height=(minOf(measuredHeight,dp(340))*.99f).toInt()
            scaleX=1f
            scaleY=1f
            translationY=0f
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
    }

    fun addBoiler(card: View) {
        boilers.addView(card, LayoutParams(0, -2, 1f).apply {
            if (boilers.childCount > 0) marginStart = dp(if (spacious) 24 else 8)
        })
    }
}
