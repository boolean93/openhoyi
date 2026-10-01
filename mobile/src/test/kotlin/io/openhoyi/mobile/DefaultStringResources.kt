package io.openhoyi.mobile

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** JVM tests read the same default resources used by Android, without a duplicated Chinese catalog. */
internal object DefaultStringResources {
    private val strings: Map<Int, String> by lazy {
        val file = listOf(File("src/main/res/values/strings.xml"), File("mobile/src/main/res/values/strings.xml"))
            .first { it.isFile }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("string")
        (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            val id = R.string::class.java.getField(node.attributes.getNamedItem("name").nodeValue).getInt(null)
            val raw = node.textContent
            val value = if (raw.startsWith('"') && raw.endsWith('"')) raw.substring(1, raw.length - 1) else raw
            id to value.replace("\\n", "\n")
        }
    }

    fun template(id: Int): String = strings.getValue(id)

    fun resolve(id: Int, args: Array<out Any?>): String =
        String.format(Locale.CHINA, template(id), *args)
}
