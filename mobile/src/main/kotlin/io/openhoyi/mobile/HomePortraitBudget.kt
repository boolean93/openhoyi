package io.openhoyi.mobile

/** Visual budget in dp, using the scroll viewport after device chrome and navigation. */
internal data class HomePortraitBudget(val dial: Int, val hero: Int) {
    companion object {
        // Space for the machine and two readable dials, including card padding and gaps.
        fun inlineMachine(contentWidth: Int, fontScale: Float): Boolean =
            contentWidth >= 112 + 16 + 8 + 40 + 2 * (144 * fontScale.coerceAtLeast(1f)).toInt()

        fun inlineLastCurve(contentWidth: Int, fontScale: Float): Boolean =
            contentWidth >= 96 + 44 + ((160 + 124) * fontScale.coerceAtLeast(1f)).toInt()

        fun fit(cardWidth: Int, viewportHeight: Int, fontScale: Float): HomePortraitBudget {
            // Keep narrow phones width-bound; broad, short windows must also be height-bound.
            // Larger text may consume more height and scroll, rather than compressing controls.
            val textScale = fontScale.coerceIn(1f, 1.5f)
            val preferred = (viewportHeight * .30f).toInt()
                .coerceIn((144 * textScale).toInt(), (184 * textScale).toInt())
            val dial = minOf(cardWidth.coerceAtLeast(0), preferred)
            val hero = (viewportHeight * .48f - dial).toInt().coerceIn(80, 156)
            return HomePortraitBudget(dial, hero)
        }
    }
}
