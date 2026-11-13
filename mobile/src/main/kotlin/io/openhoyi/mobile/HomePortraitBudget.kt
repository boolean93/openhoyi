package io.openhoyi.mobile

/** Visual budget in dp, using the scroll viewport after device chrome and navigation. */
internal data class HomePortraitBudget(val dial: Int, val hero: Int) {
    companion object {
        // Space for the machine and two readable dials, including card padding and gaps.
        fun inlineMachine(contentWidth: Int, fontScale: Float): Boolean =
            inlineDialLimit(contentWidth) >= (144 * fontScale.coerceAtLeast(1f)).toInt() &&
            contentWidth >= machineWidth(contentWidth) + 16 + 8 + 40 + 2 * (144 * fontScale.coerceAtLeast(1f)).toInt()

        // A machine column comparable to one dial prevents the product becoming a tiny icon.
        fun machineWidth(contentWidth: Int): Int = (contentWidth * .34f).toInt().coerceIn(160, 260)

        fun inlineDialLimit(contentWidth: Int): Int = (machineWidth(contentWidth) * .82f).toInt()

        fun compactTypography(width: Int, height: Int): Boolean =
            width >= 480 && height > 0 && width.toFloat() / height in .8f..1.25f

        fun compactPreset(cardWidth: Int, fontScale: Float): Boolean =
            cardWidth >= (220 * fontScale.coerceAtLeast(1f)).toInt()

        fun inlineLastCurve(contentWidth: Int, fontScale: Float): Boolean =
            contentWidth >= 96 + 44 + ((160 + 124) * fontScale.coerceAtLeast(1f)).toInt()

        fun fit(cardWidth: Int, viewportHeight: Int, fontScale: Float): HomePortraitBudget {
            // Keep narrow phones width-bound; broad, short windows must also be height-bound.
            // Larger text may consume more height and scroll, rather than compressing controls.
            val textScale = fontScale.coerceIn(1f, 1.5f)
            val preferred = (viewportHeight * .30f).toInt()
                .coerceIn((144 * textScale).toInt(), (184 * textScale).toInt())
            val dial = minOf(cardWidth.coerceAtLeast(0), preferred)
            // Include the source image's transparent border so the visible machine remains
            // comparable to a dial even when large text requires a stacked layout.
            val hero = kotlin.math.ceil(dial * 1.2).toInt()
            return HomePortraitBudget(dial, hero)
        }
    }
}
