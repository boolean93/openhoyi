package io.openhoyi.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Group drafts remain source evidence; installed catalogs must be complete and preserve branding. */
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

    @Test fun installedResourcesCoverCompleteCatalogWithoutOverridingVariantName() {
        for (language in AppLanguage.entries.filter { it != AppLanguage.CHINESE }) {
            val file = File("src/main/res/values-${language.tag}/strings.xml")
            val nodes = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(file).getElementsByTagName("string")
            val keys = (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
            val catalog = JSONObject(File("../localization/catalog/${language.tag}.json").readText())
            assertEquals(885, keys.size)
            assertEquals(keys.size, keys.toSet().size)
            assertEquals(catalog.keys().asSequence().toSet() - "app_name", keys.toSet())
            assertFalse(keys.contains("app_name"))
        }
    }

    @Test fun basicControlsDraftsCoverEveryDeclaredKeyAndPreserveFormatArguments() {
        val directory = File("../localization/drafts/basic-controls")
        val expected = setOf("theme_dark_mode", "setting_brew_heating", "setting_steam_heating",
            "setting_light", "setting_on", "setting_off", "application_info", "application_version",
            "application_mode_mock", "application_mode_alpha", "application_package",
            "device_state_disconnected", "device_state_connecting", "device_state_discovering",
            "device_state_subscribing", "device_state_initializing", "device_state_synchronizing",
            "device_state_ready", "device_state_unsupported", "device_state_failed",
            "home_scan", "home_tare_initial", "home_tare_button", "home_connect_coffee_title",
            "home_connect", "home_scan_service_hint", "home_tare_prefix", "home_tare_idle",
            "home_tare_writing", "home_tare_waiting", "home_tare_confirmed", "home_tare_unknown",
            "home_connected", "home_coffee", "home_coffee_disconnect", "home_scale",
            "home_scale_disconnect", "home_password_hint", "home_password_error",
            "ui_tab_description", "ui_current_page_suffix", "ui_back", "ui_home", "ui_curves",
            "ui_extraction", "ui_history", "ui_settings")
        val languages = AppLanguage.entries.filter { it != AppLanguage.CHINESE }
        assertEquals(languages.map { "${it.tag}.json" }.toSet(),
            directory.listFiles()?.filter { it.extension == "json" }?.map { it.name }?.toSet())
        for (language in languages) {
            val json = JSONObject(File(directory, "${language.tag}.json").readText())
            assertEquals(language.tag, expected, json.keys().asSequence().toSet())
            for (key in expected) {
                val original = DefaultStringResources.template(R.string::class.java.getField(key).getInt(null))
                val value = json.getString(key)
                assertTrue("${language.tag}/$key", value.isNotBlank())
                assertFalse(value.contains('\u0000'))
                assertEquals("${language.tag}/$key", arguments(original), arguments(value))
            }
        }
    }

    private fun arguments(value: String): Map<String,Int> {
        val pattern = Regex("%[1-9][0-9]*\\$[sd]")
        val tokens = pattern.findAll(value).map { it.value }.toList()
        assertFalse("Unrecognized format: $value", pattern.replace(value, "").contains('%'))
        return tokens.groupingBy { it }.eachCount()
    }

    @Test fun draftFormatValidationAllowsReorderingButRejectsDifferentTypeOrCount() {
        assertEquals(arguments("%1\$s %2\$d"), arguments("%2\$d %1\$s"))
        assertNotEquals(arguments("%1\$s"), arguments("%1\$d"))
        assertNotEquals(arguments("%1\$s"), arguments("%1\$s %1\$s"))
    }
}
