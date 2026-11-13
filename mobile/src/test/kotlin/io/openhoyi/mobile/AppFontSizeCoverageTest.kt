package io.openhoyi.mobile

import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.junit.Assert.*
import org.junit.Test

/** Limited source guard, not a layout/accessibility or lifecycle proof. See docs/app-typography.md. */
class AppFontSizeCoverageTest {
    private val source = File("src/main/kotlin")
    private val compatibilityHelper = "io/openhoyi/mobile/AppTextUnits.kt"
    private fun files() = source.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private fun xml(text: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(InputSource(StringReader(text)))
    private fun activities(text: String): List<String> {
        val document = xml(text)
        val names = mutableListOf<String>()
        for ((tag, attribute) in listOf("activity" to "name", "activity-alias" to "targetActivity")) {
            val nodes = document.getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                val name = (nodes.item(index) as Element).getAttributeNS(ANDROID, attribute)
                require(name.isNotBlank()) { "$tag missing android:$attribute" }
                names += name.removePrefix("io.openhoyi.mobile.").removePrefix(".")
            }
        }
        return names
    }
    @Test fun everyRegisteredActivityInheritsTheConfiguredContext() {
        val inheritance = files().flatMap { file ->
            Regex("(?:abstract\\s+)?class\\s+(\\w+)(?:\\([^)]*\\))?\\s*:\\s*(\\w+)")
                .findAll(codeOnly(file.readText())).map { it.groupValues[1] to it.groupValues[2] }.toList()
        }.toMap()
        val registered = activities(File("src/main/AndroidManifest.xml").readText())
        assertTrue(registered.isNotEmpty())
        for (name in registered) {
            var cursor = name
            val visited = mutableSetOf<String>()
            while (cursor != "ThemedActivity" && visited.add(cursor)) cursor = inheritance[cursor] ?: break
            assertEquals("$name bypasses app configuration, or its declaration needs parser review", "ThemedActivity", cursor)
        }
    }
    @Test fun manifestAttributeOrderFullNamesAndAliasesCannotHideAnEntry() {
        assertEquals(listOf("MainActivity", "OtherActivity", "MainActivity"), activities("""
            <manifest xmlns:android="$ANDROID"><application>
            <activity android:exported="false" android:name=".MainActivity" />
            <activity android:name="io.openhoyi.mobile.OtherActivity" />
            <activity-alias android:name=".Alias" android:targetActivity=".MainActivity" />
            </application></manifest>
        """))
    }
    private fun violations(name: String, text: String): List<String> {
        var code = codeOnly(text)
        // Only API<34 px -> sp compatibility. No blanket AppTextUnits file exemption.
        if (name == compatibilityHelper) code = code.replace(
            Regex("else\\s+pixels\\s*/\\s*resources\\.displayMetrics\\.scaledDensity\\b"), "")
        val rules = listOf(
            "system resources bypass configured context" to Regex("\\bResources\\s*\\.\\s*getSystem\\s*\\("),
            "linear scaledDensity conversion" to Regex("\\bscaledDensity\\b"),
            "dp text size" to Regex("\\btextSize\\s*=\\s*(?:\\w+\\.)?dp\\s*\\("),
            "density-based text size" to Regex("\\btextSize\\s*=[^;\\n]*\\bdensity\\b"),
        )
        return rules.filter { it.second.containsMatchIn(code) }.map { it.first }
    }
    @Test fun canvasHasNoLegacyLinearSpConversionsOrSystemResourceLookups() {
        for (file in files()) assertEquals(file.name, emptyList<String>(), violations(file.relativeTo(source).invariantSeparatorsPath, file.readText()))
        // The documented legacy exception must remain behind the API guard, not migrate to general code.
        val helper = codeOnly(File(source, compatibilityHelper).readText())
        assertTrue(Regex("if\\s*\\(Build.VERSION.SDK_INT\\s*>=\\s*34\\)\\s*TypedValue.deriveDimension\\([^;]*?\\)\\s*else\\s+pixels\\s*/\\s*resources.displayMetrics.scaledDensity")
            .containsMatchIn(helper))
        assertEquals(1, Regex("\\bscaledDensity\\b").findAll(helper).count())
    }
    @Test fun scannerRejectsCommonBypassesButAllowsLegitimatePixelsAndComments() {
        for (code in listOf("paint.textSize = dp(12f)", "textSize = style.dp(12)",
            "paint.textSize = 12 * density * config.fontScale", "val px = 12 * metrics.scaledDensity",
            "val r = android.content.res.Resources . getSystem ()"))
            assertTrue(code, violations("Example.kt", code).isNotEmpty())
        val quote = '"'
        val harmless = "// scaledDensity\n/* outer /* Resources.getSystem() */ textSize=dp(12) */\n" +
            "val label = ${quote}textSize=dp(12)${quote}; val raw = ${quote}${quote}${quote}scaledDensity${quote}${quote}${quote}\n" +
            "paint.textSize = AppTextUnits.pixels(resources, 12f); view.setTextSize(PX, paint.textSize)"
        assertEquals(emptyList<String>(), violations("Example.kt", harmless))
        assertTrue(violations(compatibilityHelper, "val another = metrics.scaledDensity").isNotEmpty())
        assertTrue(violations("other/AppTextUnits.kt", "else pixels / resources.displayMetrics.scaledDensity").isNotEmpty())
    }
    @Test fun scannerIgnoresNestedTemplateQuotesBracesAndCommentsWithoutSwallowingFollowingCode() {
        val quote = '"'
        val dollar = '$'
        val triple = "\"\"\""
        val nested = "${quote}${dollar}{if (ok) ${quote}scaledDensity${quote} else ${quote}safe${quote}}${quote}"
        val samples = listOf(
            "val s = $nested",
            "val s = ${quote}${dollar}{run { val braces = '{' to '}'; /* outer } /* { */ */ $nested }}${quote}",
            "val s = $triple${dollar}{run { // } scaledDensity\n ${quote}Resources.getSystem()${quote} }}$triple",
            "val s = ${quote}${dollar}{run { val raw = ${triple}textSize=dp(12) } $triple; raw }}${quote}",
        )
        for (sample in samples) {
            assertEquals(sample, emptyList<String>(), violations("Example.kt", sample))
            assertTrue(sample, violations("Example.kt", sample + "\npaint.textSize = dp(12f)").isNotEmpty())
            assertEquals(sample.count { it == '\n' }, codeOnly(sample).count { it == '\n' })
        }
    }
    private fun invalidTextDimensions(text: String): List<String> {
        val nodes = xml(text).getElementsByTagName("*")
        val invalid = mutableListOf<String>()
        fun check(value: String) {
            // References are intentionally allowed, not recursively certified by this guard.
            if (value.matches(Regex("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)[a-zA-Z]+")) && !value.endsWith("sp")) invalid += value
        }
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as Element
            for (attribute in listOf("textSize", "lineHeight")) {
                check(node.getAttributeNS(ANDROID, attribute).trim())
                if (node.tagName == "item" && node.getAttribute("name") == "android:$attribute") check(node.textContent.trim())
            }
        }
        return invalid
    }
    @Test fun literalXmlTextDimensionsUseSp() {
        File("src/main/res").walkTopDown().filter { it.isFile && it.extension == "xml" }.forEach {
            assertEquals(it.path, emptyList<String>(), invalidTextDimensions(it.readText()))
        }
    }
    @Test fun xmlGuardDistinguishesTextFromGeometryAndReferences() {
        assertEquals(listOf("16dp", "24px"), invalidTextDimensions("""
            <resources xmlns:android="$ANDROID"><style name="Example">
            <item name="android:textSize">16dp</item><item name="android:lineHeight">24px</item>
            </style><TextView android:textSize="16sp" android:lineHeight="@dimen/line"
            android:layout_height="48dp" /></resources>
        """))
    }
    /** Mask whole literals, including template expressions; those expressions need manual review. */
    private fun codeOnly(text: String): String {
        class Cursor {
            var at = 0
            fun comment(): Boolean {
                if (text.startsWith("//", at)) {
                    at = text.indexOf('\n', at).let { if (it < 0) text.length else it }
                } else if (text.startsWith("/*", at)) {
                    at += 2; var depth = 1
                    while (at < text.length && depth > 0) when {
                        text.startsWith("/*", at) -> { depth++; at += 2 }
                        text.startsWith("*/", at) -> { depth--; at += 2 }
                        else -> at++
                    }
                } else return false
                return true
            }
            fun literal(): Boolean {
                if (at >= text.length || (text[at] != '"' && text[at] != '\'')) return false
                val quote = text[at]
                val raw = text.startsWith("\"\"\"", at)
                at += if (raw) 3 else 1
                while (at < text.length) when {
                    !raw && text[at] == '\\' -> at = (at + 2).coerceAtMost(text.length)
                    raw && text.startsWith("\"\"\"", at) -> { at += 3; return true }
                    !raw && text[at] == quote -> { at++; return true }
                    quote == '"' && text.startsWith("\${", at) -> {
                        at += 2; var depth = 1
                        // Quotes, braces and comments inside an expression cannot terminate its outer string.
                        while (at < text.length && depth > 0) {
                            if (comment() || literal()) continue
                            when (text[at++]) { '{' -> depth++; '}' -> depth-- }
                        }
                    }
                    else -> at++
                }
                return true
            }
        }
        val output = text.toCharArray()
        val cursor = Cursor()
        while (cursor.at < text.length) {
            val start = cursor.at
            if (cursor.comment() || cursor.literal()) {
                for (at in start until cursor.at) if (output[at] != '\n') output[at] = ' '
            } else cursor.at++
        }
        return String(output)
    }
    private companion object { const val ANDROID = "http://schemas.android.com/apk/res/android" }
}
