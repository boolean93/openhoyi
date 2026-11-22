package io.openhoyi.mobile

internal enum class SettingsCategory(val key: String, val sections: List<String>) {
    DEVICE("device", listOf("devices", "scale")),
    MACHINE("machine", listOf("machine", "schedule")),
    BREWING("brewing", listOf("shortcuts", "chart", "feedback")),
    APPEARANCE("appearance", listOf("appearance")),
    AI("ai", listOf("ai")),
    MAINTENANCE("maintenance", listOf("about", "maintenance"));

    companion object {
        fun forSection(key: String): SettingsCategory? = entries.firstOrNull { key in it.sections }
        fun usesRail(usableWidthDp: Int): Boolean = usableWidthDp >= 800
    }
}
