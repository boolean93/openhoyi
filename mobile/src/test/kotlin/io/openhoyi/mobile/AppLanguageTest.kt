package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AppLanguageTest {
    @Test fun onlyChineseAndEnglishUseStableLocaleTags() {
        assertEquals(listOf("zh-Hans", "en"),
            AppLanguage.entries.map { it.tag })
        AppLanguage.entries.forEachIndexed { index, language ->
            assertEquals(index, language.legacyIndex)
            assertEquals(language, AppLanguage.restore(language.tag))
            assertEquals(language.tag, language.locale.toLanguageTag())
        }
    }

    @Test fun missingOrUnrecognizedPreferenceRetainsChineseDefault() {
        for (stored in listOf(null, "", "xx", "EN", "en-US", "0", " en", "zh-Hant")) {
            assertEquals(AppLanguage.CHINESE, AppLanguage.restore(stored))
        }
    }
    @org.junit.Test fun nativeNamesStayInLegacyOrderAndDoNotReplaceCanonicalTags() {
        org.junit.Assert.assertEquals(listOf("简体中文", "English"),
            AppLanguage.entries.map { it.nativeName })
        org.junit.Assert.assertEquals(2, AppLanguage.entries.map { it.tag }.toSet().size)
    }
}
