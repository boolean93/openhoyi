package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class PendingRecipePresentationTest {
    private fun label(chart: String?, selected: String?, name: String?="Recipe", valid:Boolean=true,
        visible:Boolean=true, observed:Boolean=true) =
        PendingRecipePresentation.name(observed,chart,selected,name,valid,visible)
    @Test fun manualRecordShowsActualPendingSelection() { assertEquals("Recipe",label("manual","A")) }
    @Test fun differentNormalRecordAlsoShowsPendingSelection() { assertEquals("Recipe",label("A","B")) }
    @Test fun sameIdIsHiddenRegardlessOfName() { assertNull(label("A","A","Renamed recipe")) }
    @Test fun sameNameWithDifferentIdsIsStillDifferent() { assertEquals("Recipe",label("A","B","Recipe")) }
    @Test fun missingDeletedInvalidAndPreviewAreHidden() {
        assertNull(label("manual",null)); assertNull(label("manual","A",valid=false))
        assertNull(label(null,"A")); assertNull(label("manual","A",observed=false))
        assertNull(label("manual","A",name=""))
    }
    @Test fun stopControlNeverAdvertisesAnotherStart() { assertNull(label("manual","A",visible=false)) }
    @Test fun changedSelectionAndSlotsAreNotTakenFromHistory() {
        assertEquals("Slot B",label("A","B","Slot B"))
        assertEquals("Custom C",label("A","C","Custom C"))
        assertNull(label("C","C","Custom C"))
    }
}
