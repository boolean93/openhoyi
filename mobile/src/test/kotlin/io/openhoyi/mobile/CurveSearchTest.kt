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
}
