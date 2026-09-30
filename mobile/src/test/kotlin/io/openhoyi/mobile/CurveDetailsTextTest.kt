package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CurveDetailsTextTest {
    @Test fun defaultDetailsMatchEveryCanonicalItemWithAndWithoutFactoryProof() {
        val factory = File("src/main/assets/factory_curves_v3.tsv").inputStream().use(FactoryCurveCatalog::load)
        val proof = File("src/main/assets/factory_wire_v1.tsv").inputStream().use { temporary ->
            File("src/main/assets/factory_slot_wire_v1.tsv").inputStream().use { slots ->
                FactoryWireProof.load(temporary, factory, slots)
            }
        }
        val text = CurveDetailsText(DefaultStringResources::resolve)
        for (library in listOf(CurveLibrary(factory), CurveLibrary(factory, proof))) {
            assertEquals(103, library.items.size)
            for (item in library.items) {
                val before = library.resolve(item.id, true)
                assertEquals(item.id, item.details, text.render(item, library.canStart(item)))
                assertEquals(before, library.resolve(item.id, true))
            }
        }
    }

    @Test fun emptyTranslationDoesNotAlterEligibilityOrStoredDetails() {
        val library = CurveLibrary(emptyList())
        val item = library.items.first()
        val before = library.resolve(item.id, true)
        val details = item.details
        val text = CurveDetailsText { _, _ -> "" }
        assertEquals("", text.render(item, library.canStart(item)))
        assertTrue(library.canStart(item))
        assertEquals(before, library.resolve(item.id, true))
        assertEquals(details, item.details)
    }

    @Test fun unknownMetadataRetainsOriginalDetails() {
        val item = CurveLibraryItem("external", "name", "category", "raw details %1\$s", null)
        assertEquals(item.details, CurveDetailsText { _, _ -> "" }.render(item, false))
    }
}
