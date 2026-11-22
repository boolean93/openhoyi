package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class SettingsCategoryTest {
    @Test fun everyLegacyDeepLinkHasOneCategoryWithoutAliases() {
        val expected = mapOf(
            "devices" to SettingsCategory.DEVICE, "scale" to SettingsCategory.DEVICE,
            "machine" to SettingsCategory.MACHINE, "schedule" to SettingsCategory.MACHINE,
            "shortcuts" to SettingsCategory.BREWING, "chart" to SettingsCategory.BREWING, "feedback" to SettingsCategory.BREWING,
            "appearance" to SettingsCategory.APPEARANCE, "ai" to SettingsCategory.AI,
            "maintenance" to SettingsCategory.MAINTENANCE, "about" to SettingsCategory.MAINTENANCE)
        assertEquals(expected, SettingsCategory.entries.flatMap { category -> category.sections.map { it to category } }.toMap())
        assertEquals(expected.size, SettingsCategory.entries.sumOf { it.sections.size })
        assertNull(SettingsCategory.forSection("unknown"))
        expected.forEach { (section, category) -> assertEquals(category, SettingsCategory.forSection(section)) }
    }

    @Test fun narrowAndShortWidthCanKeepEveryCategoryVisible() {
        assertFalse(SettingsCategory.usesRail(320))
        assertFalse(SettingsCategory.usesRail(799))
        assertTrue(SettingsCategory.usesRail(800))
        assertTrue(SettingsCategory.usesRail(1010))
    }
}
