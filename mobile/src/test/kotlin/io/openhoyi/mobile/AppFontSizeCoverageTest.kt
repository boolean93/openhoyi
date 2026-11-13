package io.openhoyi.mobile

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Prevent future UI entry points and Canvas conversions silently bypassing app typography. */
class AppFontSizeCoverageTest {
    private val source = File("src/main/kotlin/io/openhoyi/mobile")
    @Test fun everyRegisteredActivityInheritsTheConfiguredContext() {
        val files = source.listFiles()!!.filter { it.extension == "kt" }
        val inheritance = files.flatMap { file ->
            Regex("(?:abstract\\s+)?class\\s+(\\w+)(?:\\([^)]*\\))?\\s*:\\s*(\\w+)")
                .findAll(file.readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        }.toMap()
        val activities = Regex("<activity\\s+android:name=\"\\.(\\w+)\"")
            .findAll(File("src/main/AndroidManifest.xml").readText()).map { it.groupValues[1] }.toList()
        assertTrue(activities.isNotEmpty())
        for (name in activities) {
            var cursor = name
            val visited = mutableSetOf<String>()
            while (cursor != "ThemedActivity" && visited.add(cursor)) cursor = inheritance[cursor] ?: break
            assertEquals("$name bypasses app configuration", "ThemedActivity", cursor)
        }
    }
    @Test fun canvasHasNoLegacyLinearSpConversionsOrSystemResourceLookups() {
        for (file in source.listFiles()!!.filter { it.extension == "kt" }) {
            val code = file.readText()
            assertFalse(file.name, code.contains("Resources.getSystem("))
            if (file.name != "AppTextUnits.kt") assertFalse(file.name, code.contains("scaledDensity"))
            assertFalse(file.name, Regex("textSize\\s*=.*density\\s*\\*.*fontScale").containsMatchIn(code))
        }
    }
}
