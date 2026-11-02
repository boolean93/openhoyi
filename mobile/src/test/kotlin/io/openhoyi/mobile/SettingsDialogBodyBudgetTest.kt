package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDialogBodyBudgetTest {
    private fun natural(topAndMessage:Int=216,button:Int=144,
        body:List<SettingsDialogHeightLayer> = listOf(SettingsDialogHeightLayer(0,0),SettingsDialogHeightLayer(0,108)),
        outer:List<SettingsDialogHeightLayer> = listOf(SettingsDialogHeightLayer(0,0))) =
        SettingsDialogNaturalChrome(topAndMessage+button,button,body,outer)

    @Test fun independentNaturalChromeRecoversNonzeroBodyFromTheZeroHeightRegression() {
        val measured=natural()
        val result=requireNotNull(settingsDialogBodyBudget(619,measured,117))
        assertEquals(216,result.nonBodyNonButtonChromePx)
        assertEquals(144,result.nativeButtonReservePx)
        assertEquals(259,result.bodyMaximumHeightPx)
        assertTrue(result.minimumEditorBudgetSatisfiable)
        assertTrue(result.nativeConstraintsSatisfiable)
        // Free space and actual body collapse cannot enter this calculation or ratchet the cap.
        for(actualBody in listOf(403,259,0,117)) {
            assertEquals("actual body=$actualBody is diagnostic only",result,settingsDialogBodyBudget(619,measured,117))
        }
    }

    @Test fun customMinimumIsAMaxConstraintRatherThanExtraChrome() {
        assertEquals(259,requireNotNull(settingsDialogBodyBudget(619,natural(body=listOf(SettingsDialogHeightLayer(0,200))),117)).bodyMaximumHeightPx)
        val impossible=requireNotNull(settingsDialogBodyBudget(619,natural(body=listOf(SettingsDialogHeightLayer(0,300))),117))
        assertEquals(0,impossible.bodyMaximumHeightPx)
        assertFalse(impossible.nativeConstraintsSatisfiable)
        assertFalse(impossible.minimumEditorBudgetSatisfiable)
    }

    @Test fun nestedWrappersApplyMinimumAfterTheirOwnPaddingAndChildMargins() {
        val chain=listOf(SettingsDialogHeightLayer(10,100),SettingsDialogHeightLayer(20,200))
        val budget=requireNotNull(settingsDialogBodyBudget(619,natural(body=chain),117))
        assertEquals(229,budget.bodyMaximumHeightPx)
        assertEquals(246,budget.nonBodyNonButtonChromePx)
        assertTrue(budget.minimumEditorBudgetSatisfiable)
        // Inner min250 + outer decoration20 requires270: the custom allocation is only259.
        val impossible=requireNotNull(settingsDialogBodyBudget(619,natural(body=listOf(SettingsDialogHeightLayer(10,250),chain[1])),117))
        assertFalse(impossible.nativeConstraintsSatisfiable)
    }

    @Test fun realParentAndDecorPaddingMarginsAndDividersReduceAvailableBodyOnce() {
        val measured=natural(body=listOf(SettingsDialogHeightLayer(14,108)),
            outer=listOf(SettingsDialogHeightLayer(8,0),SettingsDialogHeightLayer(12,0)))
        val budget=requireNotNull(settingsDialogBodyBudget(619,measured,117))
        assertEquals(225,budget.bodyMaximumHeightPx)
        assertEquals(250,budget.nonBodyNonButtonChromePx)
        assertTrue(budget.minimumEditorBudgetSatisfiable)
        val outsideMin=natural(outer=listOf(SettingsDialogHeightLayer(0,620)))
        assertFalse(requireNotNull(settingsDialogBodyBudget(619,outsideMin,117)).nativeConstraintsSatisfiable)
    }

    @Test fun imeHiddenAndNaturalButtonGrowthRecomputeWithoutFeedback() {
        assertEquals(640,requireNotNull(settingsDialogBodyBudget(1000,natural(),117)).bodyMaximumHeightPx)
        assertEquals(223,requireNotNull(settingsDialogBodyBudget(619,natural(button=180),117)).bodyMaximumHeightPx)
        assertEquals(259,requireNotNull(settingsDialogBodyBudget(619,natural(),117)).bodyMaximumHeightPx)
    }

    @Test fun insufficientOrEmptyIntersectionNeverInvents52dpEditorSpace() {
        val short=requireNotNull(settingsDialogBodyBudget(400,natural(body=emptyList()),117))
        assertEquals(40,short.bodyMaximumHeightPx)
        assertFalse(short.minimumEditorBudgetSatisfiable)
        val empty=requireNotNull(settingsDialogBodyBudget(0,natural(),117))
        assertEquals(0,empty.bodyMaximumHeightPx)
        assertFalse(empty.nativeConstraintsSatisfiable)
    }

    @Test fun editorBoundaryIsExactAndUnknownStructureHasNoBudget() {
        assertNull(settingsDialogBodyBudget(619,null,117))
        assertFalse(requireNotNull(settingsDialogBodyBudget(476,natural(),117)).minimumEditorBudgetSatisfiable)
        assertTrue(requireNotNull(settingsDialogBodyBudget(477,natural(),117)).minimumEditorBudgetSatisfiable)
    }

    @Test fun deepLargeConstraintsDoNotOverflowOrInventSpace() {
        val huge=natural(body=listOf(SettingsDialogHeightLayer(Int.MAX_VALUE,Int.MAX_VALUE),SettingsDialogHeightLayer(10,0)))
        val budget=requireNotNull(settingsDialogBodyBudget(Int.MAX_VALUE,huge,117))
        assertEquals(0,budget.bodyMaximumHeightPx)
        assertEquals(Int.MAX_VALUE,budget.nonBodyNonButtonChromePx)
        assertFalse(budget.nativeConstraintsSatisfiable)
    }
}
