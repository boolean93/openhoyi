package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class HomePresetNameTest {
    @Test fun columnTransitionsKeepFiveAndSixSlotsAboveTouchWidth() {
        // Width is the actual inner grid width, after navigation and page padding.
        for ((scale, minimum) in listOf(.9f to 96, 1f to 96, 1.1f to 105)) {
            for (count in listOf(5, 6)) {
                for (columns in 2..count) {
                    val threshold = columns * minimum + (columns - 1) * 10
                    assertEquals(columns - 1, HomeDirectPresetSlots.columns(threshold - 1, count, false, scale))
                    assertEquals(columns, HomeDirectPresetSlots.columns(threshold, count, false, scale))
                    assertEquals(columns, HomeDirectPresetSlots.columns(threshold + 1, count, false, scale))
                }
                for (width in listOf(48, 95, 96, 105, 180, 276, 480, 520, 565, 626, 680, 980)) {
                    val columns = HomeDirectPresetSlots.columns(width, count, false, scale)
                    assertTrue(columns in 1..count)
                    assertTrue((width - (columns - 1) * 10) / columns >= 48)
                    assertEquals(HomeDirectPresetSlots.visible(count == 6),
                        HomeDirectPresetSlots.visible(count == 6).chunked(columns).flatten())
                }
            }
        }
        // A viewport narrower than 48dp cannot physically promise a 48dp visible target.
        // The policy still chooses one column; do not misreport this as touch acceptance.
        assertEquals(1, HomeDirectPresetSlots.columns(47, 6, false, 1.1f))
    }
    @Test fun narrowLandscapeWrapsSlotsBeforeTouchTargetsLoseWidth() {
        for (count in listOf(5, 6)) for (scale in listOf(.9f, 1f, 1.1f)) {
            val columns = HomeDirectPresetSlots.columns(276, count, false, scale)
            assertEquals(2, columns)
            assertTrue((276 - (columns - 1) * 10) / columns >= 48)
        }
        assertEquals(6, HomeDirectPresetSlots.columns(684, 6, false, 1f))
        assertEquals(2, HomeDirectPresetSlots.columns(1000, 5, true, 1f))
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
        assertEquals(listOf(1, 2, 3, 4, 5), HomeDirectPresetSlots.visible(false))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), HomeDirectPresetSlots.visible(true))
    }
}
