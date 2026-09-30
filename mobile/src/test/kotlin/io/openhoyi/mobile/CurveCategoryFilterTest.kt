package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class CurveCategoryFilterTest {
    @Test fun oldSavedCategoriesAndStableIdsRestoreTheSameFilter() {
        CurveCategoryFilter.entries.forEach { category ->
            assertEquals(category,CurveCategoryFilter.restore(category.name))
            assertEquals(category,CurveCategoryFilter.restore(category.legacyKey))
        }
        assertEquals(CurveCategoryFilter.ALL,CurveCategoryFilter.restore(null))
        assertEquals(CurveCategoryFilter.ALL,CurveCategoryFilter.restore("unrecognized-category"))
    }

    @Test fun restoredCategoryFiltersDataWithoutDependingOnDisplayText() {
        val items=listOf(
            CurveLibraryItem("dark-1","Turbo","深烘","details",null),
            CurveLibraryItem("light-1","Turbo","浅烘","details",null))
        assertEquals(listOf("dark-1"),CurveSearch.filter(items,CurveCategoryFilter.restore("DARK"),"Turbo").map{it.id})
        assertEquals(listOf("light-1"),CurveSearch.filter(items,CurveCategoryFilter.restore("浅烘"),"Turbo").map{it.id})
        assertEquals(listOf("dark-1","light-1"),CurveSearch.filter(items,CurveCategoryFilter.ALL,"").map{it.id})
    }
}
