package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FactoryCurveCatalogTest {
    private fun catalog() = File("src/main/assets/factory_curves_v3.tsv").inputStream().use(FactoryCurveCatalog::load)

    @Test fun importsCompleteNormalizedFactoryCatalogWithoutMakingCommands() {
        val curves = catalog()
        assertEquals(100, curves.size)
        assertEquals(mapOf("dark" to 25, "medium" to 25, "light" to 25, "super" to 25),
            curves.groupingBy { it.category }.eachCount())
        assertEquals("Espresso深烘", curves.first().name)
        assertEquals("SOE浅烘", curves[1].name)
        assertEquals("超萃实验档A", curves.last().name)
        assertEquals("factory-v3-100", curves.last().id)
        assertTrue(curves.all { it.segmentFlowMl.take(it.segmentCount).sum() == it.flowMl })
        val library = CurveLibrary(curves)
        assertEquals(103, library.items.size)
        assertEquals(3, library.items.count { it.controlProfile != null })
        assertTrue(library.items.filter { it.id.startsWith("factory-") }.all { it.controlProfile == null })
        assertEquals("深烘", library.find("factory-v3-001")?.category)
        assertNull(CurveCatalog.find("factory-v3-001"))
    }

    @Test fun rejectsIncompleteOrModifiedMetadata() {
        val lines = File("src/main/assets/factory_curves_v3.tsv").readLines()
        assertThrows(IllegalArgumentException::class.java) {
            FactoryCurveCatalog.load(lines.dropLast(1).joinToString("\n").byteInputStream())
        }
        assertThrows(IllegalArgumentException::class.java) {
            FactoryCurveCatalog.load(lines.toMutableList().apply { this[1] = this[1].replace("factory-v3-001", "capture-1") }
                .joinToString("\n").byteInputStream())
        }
    }
}
