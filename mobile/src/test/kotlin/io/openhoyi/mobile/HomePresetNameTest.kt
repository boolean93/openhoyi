package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class HomePresetNameTest {
    @Test fun layoutKeepsOrderAndTouchWidthsAcrossColumnTransitions() {
        for ((scale, minimum) in listOf(.9f to 80, 1f to 80, 1.1f to 88)) {
            for (lever in listOf(false, true)) {
                val slots = HomeDirectPresetSlots.visible(lever)
                val threshold = slots.size * minimum + (slots.size - 1) * 7
                assertEquals(2, HomeDirectPresetSlots.columns(threshold - 1, slots.size, false, scale))
                assertEquals(slots.size, HomeDirectPresetSlots.columns(threshold, slots.size, false, scale))
                for (width in listOf(48, 95, 180, 276, 480, 520, 680, 980)) {
                    val columns = HomeDirectPresetSlots.columns(width, slots.size, false, scale)
                    assertTrue((width - (columns - 1) * 7) / columns >= 48)
                    assertEquals(slots, slots.chunked(columns).flatten())
                    assertEquals(HomeDirectPresetSlots.LAST, slots.last())
                }
                assertEquals(2, HomeDirectPresetSlots.columns(322, slots.size, true, scale))
                assertEquals(1, HomeDirectPresetSlots.columns(165, slots.size, true, scale))
            }
        }
    }

    @Test fun onlyExplicitLegacyBindingsKeepTheExtraShortcut() {
        assertFalse(HomeDirectPresetSlots.showLegacy(null))
        assertFalse(HomeDirectPresetSlots.showLegacy(""))
        assertFalse(HomeDirectPresetSlots.showLegacy(PresetSlots.EMPTY))
        assertTrue(HomeDirectPresetSlots.showLegacy("factory-v3-005"))
        assertTrue(HomeDirectPresetSlots.showLegacy("draft-edited"))
    }

    @Test fun customNameWinsWithoutAddingSlotNumber() {
        assertEquals("晨间配方", HomePresetName.resolve(" 晨间配方 ", "原曲线", "未设置"))
    }
    @Test fun missingAndBlankNamesFallBack() {
        for (custom in listOf(null, "", " \n "))
            assertEquals("原曲线", HomePresetName.resolve(custom, "原曲线", "未设置"))
        assertEquals("未设置", HomePresetName.resolve(null, " ", "未设置"))
    }
    @Test fun longNamesRemainCompleteAndOrderIsStableWithLever() {
        val name = "Morning espresso 咖啡配方 ".repeat(8)
        assertEquals(name.trim(), HomePresetName.resolve(name, "原曲线", "未设置"))
        assertEquals(listOf(1, 2, 3, 4, HomeDirectPresetSlots.LAST), HomeDirectPresetSlots.visible(false))
        assertEquals(listOf(0, 1, 2, 3, 4, HomeDirectPresetSlots.LAST), HomeDirectPresetSlots.visible(true))
    }
}
