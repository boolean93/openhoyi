package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiCurveRecipeChangesTest {
    private fun doc() = CustomCurveDocument(CustomCurveDocument.newId(), "Synthetic", 93, 3600,
        CustomCurveDocument.ControlMode.FLOW, listOf(CustomCurveDocument.Stage(30, 100), CustomCurveDocument.Stage(25, 200)), 80,
        CustomCurveDocument.Configuration(variableFlowLogic = true, preinfusionSeconds = 5, firstDurationSeconds = 5,
            inactiveStages = listOf(CustomCurveDocument.Stage(70, 80), CustomCurveDocument.Stage(60, 90))))
    @Test fun changedTargetsWaterAndUnitsUseExactBeforeAndAfterIncludingFirstFlow() {
        val before = doc(); val after = before.copy(stages = listOf(CustomCurveDocument.Stage(30, 100), CustomCurveDocument.Stage(28, 250)),
            configuration = before.configuration!!.copy(firstSegmentFlowMode = true))
        val changes = AiCurveRecipeChanges.from(before, after, true)
        val first = changes.single { it.stageOrdinal == 1 }
        assertTrue(first.before.contains("3 bar")); assertTrue(first.after.contains("3 mL/s"))
        val second = changes.single { it.stageOrdinal == 2 }
        assertTrue(second.before.contains("2.5 mL/s · 20 mL")); assertTrue(second.after.contains("2.8 mL/s · 25 mL"))
        assertFalse(changes.any { it.stageOrdinal == 3 || it.stageOrdinal == 4 })
    }
    @Test fun pressureLogicDoesNotChangeUnitAndPreinfusionAndFirstDurationStaySeparate() {
        val before = doc(); val after = before.copy(temperatureC = 94, configuration = before.configuration!!.copy(
            pressureLogic = true, preinfusionSeconds = 6, firstDurationSeconds = 8, compensationHundredthsGram = -50))
        val changes = AiCurveRecipeChanges.from(before, after, true)
        assertFalse(changes.any { it.stageOrdinal != null })
        assertEquals("5 s", changes.single { it.label == "Preinfusion setting" }.before)
        assertEquals("6 s", changes.single { it.label == "Preinfusion setting" }.after)
        assertEquals("8 s", changes.single { it.label == "First-stage duration setting" }.after)
        assertTrue(changes.any { it.label == "Pressure logic flag" })
        assertFalse(changes.any { it.label == "Control mode" })
    }
    @Test fun newlyEnabledLegacyStageHasNoFabricatedOldZeroAndInactiveChangesAreVisible() {
        val legacy = CustomCurveDocument(CustomCurveDocument.newId(), "Legacy", 93, 0,
            CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(90, 100), CustomCurveDocument.Stage(60, 100)))
        val added = legacy.copy(stages = legacy.stages + CustomCurveDocument.Stage(40, 50))
        val stage = AiCurveRecipeChanges.from(legacy, added, true).single { it.stageOrdinal == 3 }
        assertEquals("inactive (no stored parameters)", stage.before)
        val detail = AiCurveWritePresentation.recipe(legacy, true)
        assertTrue(detail.contains("Stage 3 (inactive): no stored parameters"))
        assertFalse(detail.contains("Stage 3 (inactive): 0 bar"))
        assertTrue(AiCurveWritePresentation.recipe(legacy, false).contains("第 3 段（停用）: 无已存参数"))
        assertTrue(stage.after.contains("active · 4 bar · 5 mL"))
        val before = doc(); val after = before.copy(configuration = before.configuration!!.copy(
            inactiveStages = listOf(CustomCurveDocument.Stage(75, 85), CustomCurveDocument.Stage(60, 90))))
        val hidden = AiCurveRecipeChanges.from(before, after, false).single { it.stageOrdinal != null }
        assertEquals(3, hidden.stageOrdinal); assertTrue(hidden.before.contains("停用")); assertTrue(hidden.after.contains("7.5 mL/s"))
    }
    @Test fun unchangedFieldsAreNotHighlightedAndOriginalSnapshotsRemainUntouched() {
        val before = doc(); val bytes = before.encode()
        assertTrue(AiCurveRecipeChanges.from(before, before, true).isEmpty())
        val renamed = before.copy(name = "My copy")
        val changes = AiCurveRecipeChanges.from(before, renamed, true)
        assertEquals(1, changes.size); assertEquals("Name", changes.single().label)
        assertEquals(bytes, before.encode()); assertEquals(before.stages, renamed.stages)
    }
}
