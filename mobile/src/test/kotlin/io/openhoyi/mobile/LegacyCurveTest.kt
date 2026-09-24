package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files

class LegacyCurveTest {
    private fun json(name: String = "我的配方") = """{"format":"openhoyi-legacy-curves-v1",
        "factoryVersion":3,"categoryConfig":{"labels":{"mine":"自定义分类"}},
        "items":[{"name":"$name","category":"mine","factory":false,"temp":93,"flow":70,
        "weight":360,"seg":2,"press1":9,"flow1":30,"press2":6,"flow2":40,
        "unknownFutureField":{"keep":true}}]}"""

    @Test fun retainsSourceRowsWithoutTreatingThemAsControlProfiles() {
        val bundle = LegacyCurveCodec.parse(json().toByteArray())
        assertEquals(3, bundle.factoryVersion)
        assertEquals("自定义分类", bundle.categoryLabels["mine"])
        val curve = bundle.curves.single()
        assertEquals("我的配方", curve.name)
        assertEquals("mine", curve.category)
        assertEquals(93, curve.temperatureC)
        assertTrue(curve.rawJson.contains("unknownFutureField"))
        assertFalse(curve.factory)
    }

    @Test fun rejectsWrongEnvelopeAndNonObjectRows() {
        assertThrows(IOException::class.java) { LegacyCurveCodec.parse(json().replace("-v1", "-v2").toByteArray()) }
        assertThrows(IOException::class.java) { LegacyCurveCodec.parse(json().replace("\"items\":[{", "\"items\":[42,{ ").toByteArray()) }
        assertThrows(IOException::class.java) { LegacyCurveCodec.parse("{}".toByteArray()) }
    }

    @Test fun invalidReplacementDoesNotAlterImportedLibrary() {
        val directory = Files.createTempDirectory("legacy-curves-test").toFile()
        try {
            val store = LegacyCurveStore(directory.resolve("curves.json"))
            fun import(value: String) = store.import(ByteArrayInputStream(value.toByteArray()))
            assertTrue(import(json()).changed)
            assertFalse(import(json()).changed)
            assertThrows(IOException::class.java) { import("{" + "bad") }
            assertEquals("我的配方", store.load()?.curves?.single()?.name)
            assertTrue(import(json("新的配方")).changed)
            assertEquals("新的配方", store.load()?.curves?.single()?.name)
        } finally { directory.deleteRecursively() }
    }
}
