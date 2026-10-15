package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class CurveOrderingTest {
    @Test fun mostUsedSortsByCountThenRecencyWhileRecentIgnoresCount() {
        val storage = object : CurveUsageLedger.Storage {
            var value = ""
            override fun read() = value
            override fun write(value: String) { this.value = value }
        }
        val ledger = CurveUsageLedger(storage)
        ledger.record("a1", "a", 100)
        ledger.record("a2", "a", 200)
        ledger.record("b1", "b", 300)
        val items = listOf("z", "b", "a", "c").map { CurveLibraryItem(it, it, "全部", "", null) }
        assertEquals(listOf("a", "b", "c", "z"), CurveOrdering.sort(items, CurveSortMode.MOST_USED, ledger).map { it.id })
        assertEquals(listOf("b", "a", "c", "z"), CurveOrdering.sort(items, CurveSortMode.RECENT, ledger).map { it.id })
        assertEquals(4, items.size)
    }
    @Test fun unknownPreferenceFallsBackToRecent() {
        assertEquals(CurveSortMode.RECENT, CurveSortMode.restore("not-a-mode"))
    }
}
