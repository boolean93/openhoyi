package io.openhoyi.mobile

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Exact production rebudgeting, shared with the isolated component container. No storage or actions. */
internal object HomeViewportBudget {
    fun install(root: View, content: LinearLayout, boilerRow: MachineHomeView,
        boilerDials: List<BoilerTemperatureDial>, phoneHome: Boolean, compactType: Boolean,
        spaciousHome: Boolean) {
        val resources = root.resources
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        run {
            val scroll=root.findViewWithTag<ScrollView>("product/home/page-scroll")
            val updateBudget=android.view.ViewTreeObserver.OnGlobalLayoutListener {
                val available=scroll.height
                if(available>0) {
                    // Weighted cards may differ by a pixel. Use one shared portrait budget so
                    // they cannot alternately resize the same hero and request another layout.
                    val portraitWidth = boilerDials.minOfOrNull { dial ->
                        val card = dial.parent as View
                        (card.width-card.paddingLeft-card.paddingRight-dp(4)).coerceAtLeast(0)
                    } ?: 0
                    val portraitBudget = if (phoneHome && compactType && portraitWidth > 0) HomePortraitBudget.fit(
                        (portraitWidth/resources.displayMetrics.density).toInt(),
                        (available/resources.displayMetrics.density).toInt(), resources.configuration.fontScale) else null
                    portraitBudget?.let { boilerRow.setPortraitHeroHeight(dp(it.hero)) }
                    // Reserve the measured header, quick curves, warnings and padding first.
                    // Content may be viewport-filled, so sum children rather than its height.
                    val otherHeight = content.paddingTop + content.paddingBottom +
                        (0 until content.childCount).sumOf { index ->
                            val child = content.getChildAt(index)
                            if (child === boilerRow || child.visibility == View.GONE) 0 else {
                                val params = child.layoutParams as LinearLayout.LayoutParams
                                child.height + params.topMargin + params.bottomMargin
                            }
                        } + (boilerRow.layoutParams as LinearLayout.LayoutParams).let { it.topMargin + it.bottomMargin }
                    val machineHeight = HomeLandscapeBudget.remainingHeight(available, otherHeight)
                    if (!phoneHome) boilerRow.setLandscapeHeightBudget(machineHeight)
                    boilerDials.forEach { dial ->
                        val parent=dial.parent as View
                        val cardWidth=(parent.width-parent.paddingLeft-parent.paddingRight-dp(4)).coerceAtLeast(0)
                        if (cardWidth == 0) return@forEach
                        val diameter = portraitBudget?.let { minOf(dp(it.dial), boilerRow.portraitDialLimit() ?: Int.MAX_VALUE) }
                            ?: if (spaciousHome) {
                                val supportingHeight = (parent.height - dial.height).coerceAtLeast(0)
                                dp(HomeLandscapeBudget.dial(
                                    (cardWidth / resources.displayMetrics.density).toInt(),
                                    (machineHeight / resources.displayMetrics.density).toInt(),
                                    (supportingHeight / resources.displayMetrics.density).toInt(),
                                    resources.configuration.fontScale))
                            } else minOf(dp(216), cardWidth.coerceAtLeast(dp(112)))
                        if(dial.layoutParams.width!=diameter) dial.layoutParams=(dial.layoutParams as LinearLayout.LayoutParams).apply {width=diameter;height=diameter}
                    }
                }
            }
            // A warning or active-cup transition can change children while the filled
            // viewport keeps identical outer bounds. Rebudget those layouts too.
            content.viewTreeObserver.addOnGlobalLayoutListener(updateBudget)
        }
    }
}
