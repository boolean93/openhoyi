package io.openhoyi.mobile

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Complete resource catalogs are staged data, not an enabled UI language. */
class LanguageCatalogTest {
    @Test fun everyCatalogMatchesSourceKeysAndFormatsUnderItsLocale() {
        val directory = File("../localization/catalog")
        val source = JSONObject(File(directory,"source.json").readText()).getJSONObject("strings")
        val keys = source.keys().asSequence().toSet()
        assertEquals(890,keys.size)
        val format = Regex("%([1-9][0-9]*)\\$([0-9]*)([sd])")
        for(language in AppLanguage.entries.filter { it != AppLanguage.CHINESE }) {
            val translated = JSONObject(File(directory,"${language.tag}.json").readText())
            assertEquals(language.tag,keys,translated.keys().asSequence().toSet())
            for(key in keys) {
                val resource = R.string::class.java.getField(key).getInt(null)
                val original = source.getString(key)
                assertEquals(key,DefaultStringResources.template(resource),original)
                val matches = format.findAll(original).toList()
                val arguments = Array<Any>(matches.maxOfOrNull { it.groupValues[1].toInt() } ?: 0) { "argument-${it+1}" }
                for(match in matches) {
                    val index = match.groupValues[1].toInt()-1
                    if(match.groupValues[3]=="d") arguments[index]=7
                }
                val value = translated.getString(key)
                assertTrue("${language.tag}/$key",value.isNotBlank())
                assertEquals("${language.tag}/$key",
                    format.findAll(original).map{it.value}.groupingBy{it}.eachCount(),
                    format.findAll(value).map{it.value}.groupingBy{it}.eachCount())
                val rendered = String.format(language.locale,value,*arguments)
                assertTrue("${language.tag}/$key",rendered.isNotBlank())
                for(index in arguments.indices) if(arguments[index] is String)
                    assertTrue("${language.tag}/$key argument ${index+1}",rendered.contains(arguments[index] as String))
                for(match in matches) if(match.groupValues[3]=="d") {
                    val numeric = String.format(language.locale,match.value,*arguments)
                    assertTrue("${language.tag}/$key numeric ${match.value}",rendered.contains(numeric))
                }
            }
        }
    }
}
