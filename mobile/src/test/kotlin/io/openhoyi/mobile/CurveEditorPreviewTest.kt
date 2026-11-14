package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class CurveEditorPreviewTest {
    @Test fun addedInvalidSlotNeverClearsOtherStagesAndRecoveryUsesCurrentValues() {
        val raw = listOf("2" to "25", "4.5" to "35", "" to "")
        val model = CurveEditorPreview.parse(raw, true, false)
        assertTrue(model.invalid)
        assertEquals(listOf(20, 45, null), model.stages.map { it.target })
        assertEquals(CurveEditorPreview.Error.REQUIRED, model.stages[2].targetError)
        assertEquals(CurveEditorPreview.Error.REQUIRED, model.stages[2].waterError)
        val fixed = CurveEditorPreview.parse(raw.dropLast(1) + ("2.5" to "10"), true, false)
        assertFalse(fixed.invalid); assertEquals(700, fixed.totalWater)
        assertEquals(listOf(false, true, true), fixed.stages.map { it.flow })
    }
    @Test fun everyTargetAndWaterEditChangesTheModelAndDeletingRemovesItsErrors() {
        val initial = CurveEditorPreview.parse(listOf("3" to "15", "6" to "40"), false, false)
        assertNotEquals(initial, CurveEditorPreview.parse(listOf("4" to "15", "6" to "40"), false, false))
        assertNotEquals(initial, CurveEditorPreview.parse(listOf("3" to "25", "6" to "40"), false, false))
        val invalid = CurveEditorPreview.parse(listOf("3" to "15", "NaN" to "-2"), false, false)
        assertTrue(invalid.invalid); assertEquals(CurveEditorPreview.Error.NUMBER, invalid.stages[1].targetError)
        assertFalse(CurveEditorPreview.parse(listOf("3" to "15"), false, false).invalid)
    }
    @Test fun modeChangesRespectSharedSemanticsAndDoNotReinterpretInvalidTargets() {
        val raw = listOf("12" to "15", "12" to "40")
        assertFalse(CurveEditorPreview.parse(raw, false, false).invalid)
        val flow = CurveEditorPreview.parse(raw, true, false)
        assertTrue(flow.invalid); assertTrue(flow.stages.all { it.target == null })
        assertEquals(listOf(false, true), flow.stages.map { it.flow })
        assertEquals(listOf(true, true), CurveEditorPreview.parse(raw, true, true).stages.map { it.flow })
    }
    @Test fun malformedOverflowAndPartialDecimalsAreExplicitAndNeverZero() {
        for (bad in listOf("", " ", "12.", "1.23", "9999999999999999", "NaN", "-2")) {
            val model = CurveEditorPreview.parse(listOf(bad to bad), false, false)
            assertTrue(bad, model.invalid); assertNull(model.stages.single().target); assertNull(model.totalWater)
        }
        assertEquals(CurveEditorPreview.Error.RANGE, CurveEditorPreview.parse(listOf("13" to "0"), false, false).stages.single().targetError)
    }
}
