package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AppLanguagePreferenceTest {
    private class Disk(var value: String? = null) : AppLanguagePreference.Storage {
        var reads = 0
        var writes = 0
        var result = true
        var throwOnWrite = false
        override fun read(): String? { reads++; return value }
        override fun write(tag: String): Boolean {
            writes++
            // SharedPreferences can update its process cache even when disk commit fails.
            value = tag
            if (throwOnWrite) throw IllegalStateException("storage unavailable")
            return result
        }
    }
    @Test fun restoresOnlyCanonicalLanguageAndUsesIndependentProcessAuthority() {
        for (language in AppLanguage.entries) {
            val disk = Disk(language.tag)
            val preference = AppLanguagePreference(disk)
            assertEquals(language, preference.current)
            disk.value = AppLanguage.ARABIC.tag
            assertEquals(language, preference.current)
            assertEquals(1, disk.reads)
            assertEquals(0, disk.writes)
        }
        for (value in listOf(null, "", "EN", "ar-EG", "en-US", "unknown"))
            assertEquals(AppLanguage.CHINESE, AppLanguagePreference(Disk(value)).current)
    }
    @Test fun failedCommitOrExceptionNeverChangesCurrentLanguage() {
        for (throws in listOf(false, true)) {
            val disk = Disk(AppLanguage.CHINESE.tag).apply { result = false; throwOnWrite = throws }
            val preference = AppLanguagePreference(disk)
            assertEquals(AppLanguagePreference.Selection.SAVE_FAILED, preference.select(AppLanguage.ARABIC))
            assertEquals(AppLanguage.CHINESE, preference.current)
            assertEquals(AppLanguage.ARABIC.tag, disk.value)
            assertEquals(1, disk.reads)
            disk.result = true
            disk.throwOnWrite = false
            assertEquals(AppLanguagePreference.Selection.APPLIED, preference.select(AppLanguage.ARABIC))
            assertEquals(AppLanguage.ARABIC, preference.current)
            assertEquals(2, disk.writes)
        }
    }
    @Test fun unreadableStorageFallsBackWithoutWritingOrPublishingDuringCommit() {
        var writes = 0
        lateinit var preference: AppLanguagePreference
        preference = AppLanguagePreference(object : AppLanguagePreference.Storage {
            override fun read(): String? = throw IllegalStateException("unreadable preference")
            override fun write(tag: String): Boolean {
                writes++
                assertEquals(AppLanguage.CHINESE, preference.current)
                assertEquals(AppLanguage.ENGLISH.tag, tag)
                return true
            }
        })
        assertEquals(AppLanguage.CHINESE, preference.current)
        assertEquals(0, writes)
        assertEquals(AppLanguagePreference.Selection.APPLIED, preference.select(AppLanguage.ENGLISH))
        assertEquals(AppLanguage.ENGLISH, preference.current)
        assertEquals(1, writes)
    }

    @Test fun successPersistsCanonicalTagAndSameLanguageDoesNotWriteAgain() {
        val disk = Disk()
        val preference = AppLanguagePreference(disk)
        assertEquals(AppLanguagePreference.Selection.UNCHANGED, preference.select(AppLanguage.CHINESE))
        assertEquals(0, disk.writes)
        for (language in AppLanguage.entries.drop(1)) {
            assertEquals(AppLanguagePreference.Selection.APPLIED, preference.select(language))
            assertEquals(language.tag, disk.value)
            assertEquals(language, preference.current)
            assertEquals(AppLanguagePreference.Selection.UNCHANGED, preference.select(language))
        }
        assertEquals(7, disk.writes)
        assertEquals(AppLanguage.SPANISH, AppLanguagePreference(disk).current)
    }
}
