package io.openhoyi.mobile

/** Slot zero is a per-device last-SENT journal card, never an empty assignment target.
 * Local shortcuts retain their original indices even when the journal card is absent.
 */
internal object HomeDirectPresetSlots {
    fun visible(hasSentRecord: Boolean): List<Int> = if (hasSentRecord) (0..5).toList() else (1..5).toList()
}
