package io.openhoyi.mobile

/** Spend decorative space before shrinking the instrument; overflow remains scrollable. */
internal object HomeLandscapeBudget {
    data class Presets(val minimumHeight: Int, val textSp: Int, val previewHeight: Int)

    // Only enforce the touch floor. Preview and text determine natural card height;
    // reserving a taller minimum leaves unwanted blank space below one-line names.
    fun presets(viewportHeight: Int): Presets {
        val fraction = ((viewportHeight - 360) / 360f).coerceIn(0f, 1f)
        return Presets(48, 14 + (3 * fraction).toInt(),
            24 + (16 * fraction).toInt())
    }
    // Pixel inputs from the actual ScrollView, after shell/insets/stop-button layout.
    fun remainingHeight(viewport: Int, measuredOtherContent: Int): Int =
        (viewport - measuredOtherContent).coerceAtLeast(0)

    data class Widths(val outer: Int, val cardPadding: Int, val cardGap: Int,
        val imageGap: Int, val image: Int, val stacked: Boolean)

    fun widths(contentWidth: Int, fontScale: Float): Widths {
        val minimumDial = (180 * fontScale.coerceAtLeast(1f)).toInt()
        fun fit(outer: Int, padding: Int, gap: Int, imageGap: Int): Widths {
            val available = (contentWidth - 2 * outer - imageGap).coerceAtLeast(0)
            val boilerMinimum = 2 * (minimumDial + 2 * padding + 4) + gap
            val preferredImage = (available * .39f).toInt()
            val image = minOf(preferredImage, available - boilerMinimum)
            return Widths(outer, padding, gap, imageGap, image.coerceAtLeast(0), image < 160)
        }
        val roomy = fit(40, 16, 24, 16)
        if (!roomy.stacked && roomy.image == ((contentWidth - 96) * .39f).toInt()) return roomy
        val compact = fit(8, 8, 12, 12)
        return compact.copy(image = minOf(compact.image, ((contentWidth - 96).coerceAtLeast(0) * .39f).toInt()))
    }

    fun dial(cardWidth: Int, machineHeight: Int, supportingHeight: Int, fontScale: Float): Int {
        val minimum = (180 * fontScale.coerceAtLeast(1f)).toInt()
        val preferred = (machineHeight - supportingHeight).coerceIn(minimum, maxOf(216, minimum))
        return minOf(cardWidth.coerceAtLeast(0), preferred)
    }
}
