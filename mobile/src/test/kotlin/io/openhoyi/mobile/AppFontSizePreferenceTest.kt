package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AppFontSizePreferenceTest {
    private class Disk(var value: String? = null) : AppFontSizePreference.Storage {
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
    @Test fun restoresOnlyCanonicalSizeAndUsesIndependentProcessAuthority() {
        for (language in AppFontSize.entries) {
            val disk = Disk(language.key)
            val preference = AppFontSizePreference(disk)
            assertEquals(language, preference.current)
            disk.value = if (language == AppFontSize.MEDIUM) AppFontSize.LARGE.key else AppFontSize.MEDIUM.key
            assertEquals(language, preference.current)
            assertEquals(1, disk.reads)
            assertEquals(0, disk.writes)
        }
        for (value in listOf(null, "", "SMALL", "normal", "unknown", "en"))
            assertEquals(AppFontSize.MEDIUM, AppFontSizePreference(Disk(value)).current)
    }
    @Test fun failedCommitOrExceptionNeverChangesCurrentSize() {
        for (throws in listOf(false, true)) {
            val disk = Disk(AppFontSize.MEDIUM.key).apply { result = false; throwOnWrite = throws }
            val preference = AppFontSizePreference(disk)
            assertEquals(AppFontSizePreference.Selection.SAVE_FAILED, preference.select(AppFontSize.LARGE))
            assertEquals(AppFontSize.MEDIUM, preference.current)
            assertEquals(AppFontSize.LARGE.key, disk.value)
            assertEquals(1, disk.reads)
            disk.result = true
            disk.throwOnWrite = false
            assertEquals(AppFontSizePreference.Selection.APPLIED, preference.select(AppFontSize.LARGE))
            assertEquals(AppFontSize.LARGE, preference.current)
            assertEquals(2, disk.writes)
        }
    }
    @Test fun unreadableStorageFallsBackWithoutWritingOrPublishingDuringCommit() {
        var writes = 0
        lateinit var preference: AppFontSizePreference
        preference = AppFontSizePreference(object : AppFontSizePreference.Storage {
            override fun read(): String? = throw IllegalStateException("unreadable preference")
            override fun write(tag: String): Boolean {
                writes++
                assertEquals(AppFontSize.MEDIUM, preference.current)
                assertEquals(AppFontSize.LARGE.key, tag)
                return true
            }
        })
        assertEquals(AppFontSize.MEDIUM, preference.current)
        assertEquals(0, writes)
        assertEquals(AppFontSizePreference.Selection.APPLIED, preference.select(AppFontSize.LARGE))
        assertEquals(AppFontSize.LARGE, preference.current)
        assertEquals(1, writes)
    }

    @Test fun successPersistsCanonicalKeyAndSameSizeDoesNotWriteAgain() {
        val disk = Disk()
        val preference = AppFontSizePreference(disk)
        assertEquals(AppFontSizePreference.Selection.UNCHANGED, preference.select(AppFontSize.MEDIUM))
        assertEquals(0, disk.writes)
        for (language in listOf(AppFontSize.SMALL, AppFontSize.LARGE)) {
            assertEquals(AppFontSizePreference.Selection.APPLIED, preference.select(language))
            assertEquals(language.key, disk.value)
            assertEquals(language, preference.current)
            assertEquals(AppFontSizePreference.Selection.UNCHANGED, preference.select(language))
        }
        assertEquals(2, disk.writes)
        assertEquals(AppFontSize.LARGE, AppFontSizePreference(disk).current)
    }
}
