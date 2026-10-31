package io.openhoyi.mobile

import io.openhoyi.mobile.CurveStageParameters.Unit as StageUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveControlPreviewTest {
    private fun item(profile: CurveProfile) = CurveLibraryItem(profile.id, profile.name, "", "", profile)
    private fun item(factory: FactoryCurve) = CurveLibraryItem(factory.id, factory.name, "", "", null, factory)

    @Test fun capturedProfileUsesPerStageModeAndIgnoresPressureLogicForUnit() {
        val base = CurveCatalog.profiles[1]
        val mixed = CurveStageParameters.preview(item(base))
        assertEquals(listOf(StageUnit.PRESSURE_BAR, StageUnit.FLOW_ML_PER_SECOND, StageUnit.FLOW_ML_PER_SECOND),
            mixed.stages.map { it.unit })
        assertEquals(listOf(20, 38, 20), mixed.stages.map { it.target })
        assertEquals(6f, mixed.flowAxisMax)
        assertTrue(mixed.renderable && mixed.mixed)
        val firstFlow = base.copy(parameters = base.parameters.copy(firstSegmentFlowMode = true))
        assertTrue(CurveStageParameters.preview(item(firstFlow)).stages.all { it.unit == StageUnit.FLOW_ML_PER_SECOND })
        val pressureOnly = base.copy(parameters = base.parameters.copy(variableFlowLogic = false, firstSegmentFlowMode = true))
        assertTrue(CurveStageParameters.preview(item(pressureOnly)).stages.all { it.unit == StageUnit.PRESSURE_BAR })
    }

    @Test fun factoryMixedTurboTargetsKeepPressureThenActualFlow() {
        val factory = FactoryCurve("factory-v3-003", "dark", "HOYI Turbo Shot", "", "", 50, 0, 0,
            0, 92, true, true, 2, listOf(20, 30, 0, 0), listOf(500, 1000, 0, 0), 0)
        val preview = CurveStageParameters.preview(item(factory))
        assertEquals(listOf(CurveStageParameters.Stage(20, StageUnit.PRESSURE_BAR),
            CurveStageParameters.Stage(30, StageUnit.FLOW_ML_PER_SECOND)), preview.stages)
        assertTrue(preview.renderable && preview.mixed)
        assertEquals(6f, preview.flowAxisMax)
        assertEquals(listOf(20, 30), preview.stages.map { it.target }) // Water volumes never replace targets.
    }

    @Test fun rawDraftKeepsNumbersWithoutClaimingPhysicalUnits() {
        val raw = CustomCurveDocument("draft-test", "raw", 92, 0, CustomCurveDocument.ControlMode.FLOW_RAW,
            listOf(CustomCurveDocument.Stage(0, 300), CustomCurveDocument.Stage(255, 500)))
        val preview = CurveStageParameters.preview(CurveLibraryItem(raw.id, raw.name, "", "", null, customDocument = raw))
        assertEquals(listOf(0, 255), preview.stages.map { it.target })
        assertTrue(preview.stages.all { it.unit == StageUnit.RAW_UNKNOWN })
        assertFalse(preview.renderable)
    }

    @Test fun axisCoversWholeValidFlowRangeAndPressureZeroIsReal() {
        val base = CurveCatalog.profiles[1]
        val flow = base.copy(parameters = base.parameters.copy(firstSegmentFlowMode = true,
            target1 = 0, target2 = 255, target3 = 100))
        val preview = CurveStageParameters.preview(item(flow))
        assertTrue(preview.renderable)
        assertEquals(30f, preview.flowAxisMax)
        assertEquals(0, preview.stages.first().target)
    }
}
