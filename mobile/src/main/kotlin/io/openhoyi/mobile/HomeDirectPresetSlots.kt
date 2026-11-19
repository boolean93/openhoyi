package io.openhoyi.mobile

/** Layout identities only. Slot zero is the per-device journal; LAST is not a brew wire slot. */
internal object HomeDirectPresetSlots {
    const val LAST = 6
    fun visible(hasSentRecord: Boolean): List<Int> = if (hasSentRecord) (0..4).toList() + LAST else (1..4).toList() + LAST

    fun columns(availableWidthDp: Int, count: Int, portrait: Boolean, fontScale: Float): Int {
        // Under constrained windows labels can reflow, preserving both font and touch size.
        val minimum = ((if (portrait) 100 else 80) * fontScale.coerceAtLeast(1f)).toInt()
        val gap = 7
        val fit = ((availableWidthDp + gap) / (minimum + gap)).coerceAtLeast(1)
        return minOf(count, fit, if (portrait || fit < count) 2 else count)
    }

    fun showLegacy(binding: String?): Boolean = !binding.isNullOrBlank() && binding != PresetSlots.EMPTY
}
