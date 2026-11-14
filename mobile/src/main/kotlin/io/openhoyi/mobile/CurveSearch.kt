package io.openhoyi.mobile

import java.util.Locale

/** Local library search never changes a curve's wire eligibility. */
object CurveSearch {
    fun filter(items: List<CurveLibraryItem>, category: CurveCategoryFilter, query: String, customOnly: Boolean = false): List<CurveLibraryItem> =
        filter(items, category.legacyKey, query, customOnly)

    fun filter(items: List<CurveLibraryItem>, category: String, query: String, customOnly: Boolean = false): List<CurveLibraryItem> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return items.filter { item ->
            (!customOnly || item.customDocument != null) &&
                (category == "全部" || item.category == category) &&
                (needle.isEmpty() || item.name.lowercase(Locale.ROOT).contains(needle) ||
                    item.id.lowercase(Locale.ROOT).contains(needle))
        }
    }
}
