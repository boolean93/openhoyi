package io.openhoyi.mobile

enum class CurveSortMode {
    RECENT, MOST_USED;
    companion object {
        fun restore(value: String?): CurveSortMode = entries.firstOrNull { it.name == value } ?: RECENT
    }
}

object CurveOrdering {
    fun sort(items: List<CurveLibraryItem>, mode: CurveSortMode, ledger: CurveUsageLedger): List<CurveLibraryItem> {
        val stats = items.associate { it.id to ledger.stats(it.id) }
        val recency = compareByDescending<CurveLibraryItem> { stats.getValue(it.id).lastUsedAtMs ?: Long.MIN_VALUE }
        val order = if (mode == CurveSortMode.MOST_USED)
            compareByDescending<CurveLibraryItem> { stats.getValue(it.id).count }.then(recency)
        else recency
        return items.sortedWith(order.thenBy { it.id })
    }
}
