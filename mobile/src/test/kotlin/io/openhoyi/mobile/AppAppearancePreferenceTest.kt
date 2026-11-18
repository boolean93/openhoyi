package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class AppAppearancePreferenceTest {
    private class Storage(var value: AppAppearance) : AppAppearancePreference.Storage {
        var reject = false
        var writes = 0
        override fun read() = value
        override fun write(previous: AppAppearance, value: AppAppearance): Boolean {
            assertEquals(this.value, previous)
            writes++
            if (reject) return false
            this.value = value
            return true
        }
    }

    @Test fun defaultsAndStableKeys() {
        assertEquals(AppThemePalette.CLASSIC, AppThemePalette.restore(null))
        assertEquals(AppThemePalette.CLASSIC, AppThemePalette.restore("unknown"))
        assertEquals(listOf("classic", "sea-salt", "coffee", "graphite"), AppThemePalette.entries.map { it.key })
        AppThemePalette.entries.forEach { assertEquals(it, AppThemePalette.restore(it.key)) }
    }

    @Test fun paletteAndDarkAreIndependentAndSurviveNewReader() {
        val storage = Storage(AppAppearance(dark = true))
        val preferences = AppAppearancePreference(storage)
        assertEquals(AppAppearance(dark = true), preferences.current)
        assertEquals(AppAppearancePreference.Selection.APPLIED, preferences.selectPalette(AppThemePalette.COFFEE))
        assertEquals(AppAppearance(true, AppThemePalette.COFFEE), preferences.current)
        assertEquals(AppAppearancePreference.Selection.APPLIED, preferences.selectDark(false))
        assertEquals(AppAppearance(false, AppThemePalette.COFFEE), AppAppearancePreference(storage).current)
        assertEquals(AppAppearancePreference.Selection.UNCHANGED, preferences.selectPalette(AppThemePalette.COFFEE))
        assertEquals(2, storage.writes)
    }

    @Test fun writeFailureKeepsBothAppliedChoices() {
        val initial = AppAppearance(true, AppThemePalette.GRAPHITE)
        val storage = Storage(initial).apply { reject = true }
        val preferences = AppAppearancePreference(storage)
        assertEquals(AppAppearancePreference.Selection.SAVE_FAILED, preferences.selectDark(false))
        assertEquals(AppAppearancePreference.Selection.SAVE_FAILED, preferences.selectPalette(AppThemePalette.SEA_SALT))
        assertEquals(initial, preferences.current)
    }
}
