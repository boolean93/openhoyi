package io.openhoyi.mobile

/** Slot zero is a per-device last-SENT journal card, never an empty assignment target.
 * Local shortcuts retain their original indices even when the journal card is absent.
 */
internal object HomeDirectPresetSlots {
    fun columns(availableWidthDp: Int, count: Int, portrait: Boolean, fontScale: Float): Int {
        val minimum = (96 * fontScale.coerceAtLeast(1f)).toInt()
        val fit = ((availableWidthDp + 10) / (minimum + 10)).coerceAtLeast(1)
        return minOf(count, fit, if (portrait) 2 else count)
    }
    fun visible(hasSentRecord: Boolean): List<Int> = if (hasSentRecord) (0..5).toList() else (1..5).toList()
}
