package io.openhoyi.mobile

/** Local shortcuts, not writes to the machine's stored recipes. */
object PresetSlots {
    const val EXTRA_SLOT = "io.openhoyi.mobile.PRESET_SLOT"
    fun requireSlot(slot: Int) { require(slot in 1..5) }
    fun key(slot: Int): String { requireSlot(slot); return "slot_$slot" }
    fun curveId(slot: Int, stored: String?): String {
        requireSlot(slot)
        // Preserve even a missing draft reference. It must never become another recipe.
        return stored?.takeIf { it.startsWith("draft-") || it.matches(Regex("factory-v3-(00[1-9]|0[1-9][0-9]|100)")) }
            ?: "factory-v3-00$slot"
    }
    sealed class Route {
        data class Ready(val curveId: String, val wireSlot: Int, val selectCurve: Boolean) : Route()
        object Missing : Route()
        object Unavailable : Route()
    }
    fun route(slot: Int, stored: String?, library: CurveLibrary): Route {
        val id = curveId(slot, stored)
        // Keep the original factory route, including its existing start-time guards.
        if (!id.startsWith("draft-")) return Route.Ready(id, slot, false)
        val item = library.find(id) ?: return Route.Missing
        if (item.customDocument == null || !library.canStart(item)) return Route.Unavailable
        return Route.Ready(id, 7, true)
    }
    fun canAssign(item: CurveLibraryItem, library: CurveLibrary): Boolean =
        (item.factoryCurve != null || item.customDocument != null) && library.canStart(item)
}
