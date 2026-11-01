package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDialogBodyBudgetTest {
    @Test fun collapsedPanelStillReservesItsNativeHeightAndConverges() {
        val first=requireNotNull(settingsDialogBodyBudget(619,619,403,0,144,117))
        assertEquals(216,first.nonBodyNonButtonChromePx)
        assertEquals(144,first.nativeButtonReservePx)
        assertEquals(259,first.bodyMaximumHeightPx)
        assertTrue(first.minimumEditorBudgetSatisfiable)
        val stable=requireNotNull(settingsDialogBodyBudget(619,619,259,144,144,117))
        assertEquals(first,stable)
    }

    @Test fun growthOfActualSafeDecorRestoresOnlyBodyBudget() {
        val larger=requireNotNull(settingsDialogBodyBudget(1000,1000,640,144,144,117))
        assertEquals(640,larger.bodyMaximumHeightPx)
        assertEquals(216,larger.nonBodyNonButtonChromePx)
        assertEquals(144,larger.nativeButtonReservePx)
        // A taller visible display alone cannot create space outside an unchanged smaller decor.
        assertEquals(259,requireNotNull(settingsDialogBodyBudget(619,619,259,144,144,117)).bodyMaximumHeightPx)
    }

    @Test fun naturalButtonGrowthGetsReservedWithoutShrinkingButtons() {
        val larger=requireNotNull(settingsDialogBodyBudget(619,619,259,144,180,117))
        assertEquals(223,larger.bodyMaximumHeightPx)
        assertEquals(larger,settingsDialogBodyBudget(619,619,223,180,180,117))
    }

    @Test fun insufficientOrEmptySafeIntersectionDoesNotInventEditorSpace() {
        val short=requireNotNull(settingsDialogBodyBudget(400,619,259,144,144,117))
        assertEquals(40,short.bodyMaximumHeightPx)
        assertFalse(short.minimumEditorBudgetSatisfiable)
        val empty=requireNotNull(settingsDialogBodyBudget(0,619,259,144,144,117))
        assertEquals(0,empty.bodyMaximumHeightPx)
        assertFalse(empty.minimumEditorBudgetSatisfiable)
    }

    @Test fun unknownNativeStructureHasNoCertifiedBudget() {
        assertNull(settingsDialogBodyBudget(619,619,403,0,null,117))
    }

    @Test fun editorBoundaryIsExactAndLargeMeasurementsDoNotOverflow() {
        assertFalse(requireNotNull(settingsDialogBodyBudget(476,619,259,144,144,117)).minimumEditorBudgetSatisfiable)
        assertTrue(requireNotNull(settingsDialogBodyBudget(477,619,259,144,144,117)).minimumEditorBudgetSatisfiable)
        assertEquals(0,requireNotNull(settingsDialogBodyBudget(1,Int.MAX_VALUE,0,0,144,117)).bodyMaximumHeightPx)
    }
}
