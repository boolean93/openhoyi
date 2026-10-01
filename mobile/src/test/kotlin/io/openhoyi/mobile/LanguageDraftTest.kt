package io.openhoyi.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Drafts are not Android resources until the complete catalog and layouts are verified. */
class LanguageDraftTest {
    private val keys = setOf("home_stop", "stop_processing", "stop_interrupted",
        "shot_safety_unknown_disconnected", "shot_safety_unknown", "shot_safety_active_disconnected",
        "notification_safety_channel", "notification_attention", "notification_check_machine", "device_initial_message")

    @Test fun allLegacyLanguagesHaveTheSameCompleteSafetyDraftSubset() {
        val directory = File("../localization/drafts/safety")
        val languages = AppLanguage.entries.filter { it != AppLanguage.CHINESE }
        assertEquals(languages.map { "${it.tag}.json" }.toSet(),
            directory.listFiles()?.filter { it.extension == "json" }?.map { it.name }?.toSet())
        for (language in languages) {
            val json = JSONObject(File(directory, "${language.tag}.json").readText())
            assertEquals(language.tag, keys, json.keys().asSequence().toSet())
            for (key in keys) {
                val id = R.string::class.java.getField(key).getInt(null)
                val original = DefaultStringResources.resolve(id, emptyArray())
                assertTrue(original.isNotBlank())
                val value = json.getString(key)
                assertTrue("${language.tag}/$key", value.isNotBlank())
                assertFalse("Unexpected format parameter in ${language.tag}/$key", value.contains('%'))
                assertFalse(value.contains('\u0000'))
            }
        }
    }

    @Test fun incompleteLanguageDraftsAreNotInstalledAsAutomaticAndroidFallbacks() {
        for (language in AppLanguage.entries.filter { it != AppLanguage.CHINESE }) {
            assertFalse(File("src/main/res/values-${language.tag}/strings.xml").exists())
        }
    }
}
