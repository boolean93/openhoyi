package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class CurveSearchTest {
    private val items = listOf(
        CurveLibraryItem("factory-v3-001", "Turbo Shot", "深烘", "details", null),
        CurveLibraryItem("factory-v3-002", "经典意式", "中烘", "details", null),
        CurveLibraryItem("captured-1", "Turbo Light", "已采集验证", "details", null),
    )

    @Test fun searchCombinesCategoryAndNameWithoutMatchingHiddenDetails() {
        assertEquals(listOf("factory-v3-001", "captured-1"),
            CurveSearch.filter(items, "全部", " turbo ").map { it.id })
        assertEquals(listOf("factory-v3-001"),
            CurveSearch.filter(items, "深烘", "TURBO").map { it.id })
        assertEquals(emptyList<String>(),
            CurveSearch.filter(items, "中烘", "details").map { it.id })
    }
    @Test fun customIdentityIncludesCreatedImportedAndCopiedButNotNamedFactoryItems() {
        val store = CustomCurveStore(object : CustomCurveStore.Storage {
            private var value: String? = null
            override fun read() = value
            override fun write(value: String) { this.value = value }
        })
        val document = CustomCurveDocument("draft-00000000-0000-4000-8000-000000000001",
            "Turbo mine", 92, 1800, CustomCurveDocument.ControlMode.PRESSURE,
            listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
        store.save(document)
        val imported = document.copy(id = "draft-00000000-0000-4000-8000-000000000002", name = "Imported")
        store.importDocument(imported)
        val copied = store.saveCopy(document)
        val all = items + store.items()
        assertEquals(setOf(document.id, imported.id, copied.id),
            CurveSearch.filter(all, "全部", "", true).map { it.id }.toSet())
        assertEquals(setOf(document.id, copied.id),
            CurveSearch.filter(all, "全部", " TURBO ", true).map { it.id }.toSet())
        assertEquals(emptyList<CurveLibraryItem>(), CurveSearch.filter(all, "深烘", "", true))
        assertEquals(all, CurveSearch.filter(all, "全部", "", false))
        store.delete(imported.id, emptyMap())
        assertEquals(setOf(document.id, copied.id),
            CurveSearch.filter(items + store.items(), "全部", "", true).map { it.id }.toSet())
    }
}
