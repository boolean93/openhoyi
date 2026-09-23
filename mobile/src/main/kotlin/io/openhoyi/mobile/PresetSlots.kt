package io.openhoyi.mobile

/** Local shortcut assignment. Each legacy slot carries the full curve in its start packet. */
object PresetSlots {
    const val EXTRA_SLOT = "io.openhoyi.mobile.PRESET_SLOT"
    fun requireSlot(slot: Int) { require(slot in 1..5) }
    fun key(slot: Int): String { requireSlot(slot); return "slot_$slot" }
    fun curveId(slot: Int, stored: String?): String {
        requireSlot(slot)
        return stored?.takeIf { it.matches(Regex("factory-v3-(00[1-9]|0[1-9][0-9]|100)")) }
            ?: "factory-v3-00$slot"
    }
}
