package io.openhoyi.mobile

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** Pure home layout: the machine image never contains telemetry or controls. */
internal class MachineHomeView(
    context: Context, private val spacious: Boolean, private val onOpenAi: (() -> Unit)? = null,
) : LinearLayout(context) {
    private var machineImage: ImageView? = null
    private var landscapeHeightBudget: Int? = null

    fun setLandscapeHeightBudget(height: Int) {
        if (portrait || landscapeHeightBudget == height) return
        landscapeHeightBudget = height
        requestLayout()
    }
    private var inlinePortrait = false
    private var tightInlinePortrait = false
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
                configureAiEntry(this)
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
                configureAiEntry(this)
            }, LayoutParams(0, -1, 39f).apply { marginEnd = dp(16) })
        }
        addView(boilers, LayoutParams(if (spacious) 0 else -1, if (spacious) -1 else -2,
            if (spacious) 61f else 0f))
    }

    private fun configureAiEntry(image: ImageView) {
        val open = onOpenAi ?: return
        image.minimumWidth = dp(48)
        image.minimumHeight = dp(48)
        image.isFocusable = true
        image.contentDescription = context.getString(R.string.home_machine_description) + " · " + context.getString(R.string.ai_title)
        image.tooltipText = context.getString(R.string.ai_title)
        image.setOnClickListener { open() }
    }

    fun portraitDialLimit(): Int? = if (inlinePortrait)
        dp(HomePortraitBudget.inlineDialLimit(((width - paddingLeft - paddingRight) / density).toInt(), tightInlinePortrait)) else null

    fun setPortraitHeroHeight(height: Int) {
        if (!portrait || inlinePortrait) return
        machineImage?.let { image ->
            if (image.layoutParams.height != height) {
                image.layoutParams = image.layoutParams.apply { this.height = height }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (portrait && !spacious && HomePortraitBudget.compactTypography(
                resources.configuration.screenWidthDp, resources.configuration.screenHeightDp)) {
            val width = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
            val contentWidthDp = (width / density).toInt()
            tightInlinePortrait = HomePortraitBudget.tightInlineMachine(contentWidthDp, resources.configuration.fontScale)
            inlinePortrait = HomePortraitBudget.inlineMachine(contentWidthDp, resources.configuration.fontScale) || tightInlinePortrait
            for (index in 0 until boilers.childCount) {
                val card = boilers.getChildAt(index)
                val inset = dp(if (tightInlinePortrait) 4 else 8)
                card.setPadding(inset, card.paddingTop, inset, card.paddingBottom)
                (card.layoutParams as LayoutParams).apply {
                    marginStart = if (index == 0) 0 else dp(if (tightInlinePortrait) 4 else 8)
                    resolveLayoutDirection(card.layoutDirection)
                }
            }
            orientation = if (inlinePortrait) HORIZONTAL else VERTICAL
            machineImage?.let { image ->
                (image.layoutParams as LayoutParams).apply {
                    this.width = if (inlinePortrait) dp(HomePortraitBudget.machineWidth(contentWidthDp)) else -1
                    gravity = if (inlinePortrait) Gravity.TOP else -1
                    if (inlinePortrait) height = this.width + dp(40)
                    marginEnd = if (inlinePortrait) dp(if (tightInlinePortrait) 8 else 16) else 0
                    bottomMargin = if (inlinePortrait) 0 else dp(12)
                    resolveLayoutDirection(image.layoutDirection)
                }
            }
            (boilers.layoutParams as LayoutParams).apply {
                this.width = if (inlinePortrait) 0 else -1
                height = -2
                weight = if (inlinePortrait) 1f else 0f
            }
        }
        if (!spacious) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            if (!portrait && landscapeHeightBudget != null) {
                val height = maxOf(measuredHeight, landscapeHeightBudget ?: 0)
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            }
            return
        }
        // Use this view's real width after shell/navigation padding, not display width.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val budget = HomeLandscapeBudget.widths((width / density).toInt(), resources.configuration.fontScale)
        setPadding(dp(budget.outer), paddingTop, dp(budget.outer), paddingBottom)
        orientation = if (budget.stacked) VERTICAL else HORIZONTAL
        val innerWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val minimumCardWidth = dp((180 * resources.configuration.fontScale.coerceAtLeast(1f)).toInt() + 2 * budget.cardPadding + 4)
        val stackCards = budget.stacked && innerWidth < 2 * minimumCardWidth + dp(budget.cardGap)
        boilers.orientation = if (stackCards) VERTICAL else HORIZONTAL
        machineImage?.let { image ->
            (image.layoutParams as LayoutParams).apply {
                this.width = if (budget.stacked) innerWidth else dp(budget.image)
                height = if (budget.stacked) dp(180) else -1
                weight = 0f
                marginEnd = if (budget.stacked) 0 else dp(budget.imageGap)
                bottomMargin = if (budget.stacked) dp(8) else 0
                resolveLayoutDirection(image.layoutDirection)
            }
        }
        (boilers.layoutParams as LayoutParams).apply {
            this.width = if (budget.stacked) -1 else 0
            height = if (budget.stacked) -2 else -1
            weight = if (budget.stacked) 0f else 1f
        }
        val boilerWidth = if (budget.stacked) innerWidth else
            (innerWidth - dp(budget.image + budget.imageGap)).coerceAtLeast(0)
        val cardWidth = if (stackCards) boilerWidth else
            ((boilerWidth - dp(budget.cardGap) * (boilers.childCount - 1)) / boilers.childCount.coerceAtLeast(1)).coerceAtLeast(0)
        var naturalCardHeight = 0
        for (index in 0 until boilers.childCount) {
            val card = boilers.getChildAt(index)
            card.setPadding(dp(budget.cardPadding), card.paddingTop, dp(budget.cardPadding), card.paddingBottom)
            (card.layoutParams as LayoutParams).apply {
                this.width = if (stackCards) -1 else 0
                weight = if (stackCards) 0f else 1f
                marginStart = if (!stackCards && index > 0) dp(budget.cardGap) else 0
                topMargin = if (stackCards && index > 0) dp(8) else 0
                resolveLayoutDirection(card.layoutDirection)
            }
            card.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            naturalCardHeight = if (stackCards) naturalCardHeight + card.measuredHeight + (if (index > 0) dp(8) else 0)
                else maxOf(naturalCardHeight, card.measuredHeight)
        }
        val stackedImageHeight = if (budget.stacked) dp(188) else 0
        val desiredHeight = maxOf(landscapeHeightBudget ?: dp(340), naturalCardHeight + stackedImageHeight + paddingTop + paddingBottom)
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        machineImage?.apply {
            layoutParams.height = if (budget.stacked) dp(180) else (minOf(measuredHeight,dp(340))*.99f).toInt()
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
