package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AppLanguageTest {
    @Test fun languagesMatchLegacyPickerOrderAndUseStableLocaleTags() {
        assertEquals(listOf("zh-Hans", "en", "ru", "th", "ar", "ja", "ko", "es"),
            AppLanguage.entries.map { it.tag })
        AppLanguage.entries.forEachIndexed { index, language ->
            assertEquals(index, language.legacyIndex)
            assertEquals(language, AppLanguage.restore(language.tag))
            assertEquals(language.tag, language.locale.toLanguageTag())
        }
        assertEquals(listOf(AppLanguage.ARABIC), AppLanguage.entries.filter { it.rightToLeft })
    }

    @Test fun missingOrUnrecognizedPreferenceRetainsChineseDefault() {
        for (stored in listOf(null, "", "xx", "EN", "en-US", "0", "../../ar", " en", "zh-Hant")) {
            assertEquals(AppLanguage.CHINESE, AppLanguage.restore(stored))
        }
    }
}
