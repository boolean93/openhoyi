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

    /**
     * Retain the reference proportions, then lend spare width to any longer label in its actual
     * slot. If all titles cannot fit, keep the grid proportionate and allow native text reflow.
     */
    fun wideWidths(usableWidth: Int, weights: List<Float>, minimumWidths: List<Int>): List<Int> {
        val totalWeight = weights.sum()
        val initial = weights.map { (usableWidth * it / totalWeight).toInt() }.toMutableList()
        initial[initial.lastIndex] += usableWidth - initial.sum()
        if (minimumWidths.sum() > usableWidth) return initial
        var needed = initial.indices.sumOf { index -> (minimumWidths[index] - initial[index]).coerceAtLeast(0) }
        var spare = initial.indices.sumOf { index -> (initial[index] - minimumWidths[index]).coerceAtLeast(0) }
        return initial.mapIndexed { index, width ->
            val min = minimumWidths[index]
            if (width <= min) min else {
                val ownSpare = width - min
                val lend = if (spare == 0) 0 else (needed.toLong() * ownSpare / spare).toInt()
                spare -= ownSpare
                needed -= lend
                width - lend
            }
        }
    }

    fun showLegacy(binding: String?): Boolean = !binding.isNullOrBlank() && binding != PresetSlots.EMPTY
}
