package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class CurveRecipePlanTest {
    private fun factory(id: String, flow: Boolean, targets: List<Int>, waters: List<Int>, limit: Int) =
        FactoryCurve(id, "light", id, "advice", "", limit, 0, 0, 10, 92,
            false, flow, targets.size, targets + List(4 - targets.size) { 0 },
            waters + List(4 - waters.size) { 0 }, 0)
    private fun item(curve: FactoryCurve) = CurveLibraryItem(curve.id, curve.id, "light", "", null, curve)

    @Test fun turboStagesKeepPressureFlowAndWaterAsDifferentQuantities() {
        val turbo = item(factory("factory-v3-003", true, listOf(20, 30), listOf(15, 135), 150))
        val plan = CurveRecipePlan.from(turbo)
        assertEquals(listOf(CurveStageParameters.Unit.PRESSURE_BAR,
            CurveStageParameters.Unit.FLOW_ML_PER_SECOND), plan.preview.stages.map { it.unit })
        assertEquals(listOf(20, 30), plan.preview.stages.map { it.target })
        assertEquals(listOf(150, 1350), plan.stageWaterTenthsMl)
        assertEquals(1500, plan.assignedWaterTenthsMl)
        assertEquals(150, plan.totalWaterLimitMl)
        assertEquals(10, plan.preinfusionSeconds)
        assertEquals(0, plan.firstStageDurationSettingSeconds)
        assertEquals(0, plan.targetWeightHundredthsGram)
    }

    @Test fun sharedSparklineScaleUsesWholeLibraryRatherThanFilteredSelection() {
        val low = item(factory("low", false, listOf(20, 30), listOf(5, 5), 10))
        val high = item(factory("high", true, listOf(150, 125), listOf(5, 5), 10))
        val scale = CurveStageParameters.SharedScale.from(listOf(low, high))
        assertEquals(16f, scale.pressureMaxBar)
        assertEquals(20f, scale.flowMaxMlPerSecond)
        assertEquals(12f, CurveStageParameters.SharedScale.from(listOf(low)).pressureMaxBar)
        assertEquals(6f, CurveStageParameters.SharedScale.from(listOf(low)).flowMaxMlPerSecond)
    }

    @Test fun capturedFirstSegmentFlagAndCustomRawNeverBecomePressureAssumptions() {
        val captured = CurveCatalog.profiles[0].copy(parameters = CurveCatalog.profiles[0].parameters.copy(
            variableFlowLogic = false, firstSegmentFlowMode = true))
        val plan = CurveRecipePlan.from(CurveLibraryItem("capture", "capture", "", "", captured))
        assertTrue(plan.preview.stages.all { it.unit == CurveStageParameters.Unit.PRESSURE_BAR })
        assertEquals(captured.parameters.firstDurationSeconds, plan.firstStageDurationSettingSeconds)
    }

    @Test fun profileWaterUsesTenthsAndKeepsLimitSeparateFromAssignedStages() {
        val source = CurveCatalog.profiles.first()
        val parameters = source.parameters.copy(segmentCount = 2, firstFlowTenths = 15,
            secondFlowTenths = 135, maximumWaterMl = 200)
        val profile = source.copy(parameters = parameters)
        val plan = CurveRecipePlan.from(CurveLibraryItem("profile", "profile", "", "", profile))
        assertEquals(listOf(15, 135), plan.stageWaterTenthsMl)
        assertEquals(150, plan.assignedWaterTenthsMl)
        assertEquals(200, plan.totalWaterLimitMl)
    }

    @Test fun customRawHasNoInventedLimitOrTimeAndZeroAllocationIsNotDrawable() {
        val raw = CustomCurveDocument(CustomCurveDocument.newId(), "raw", 92, 1800,
            CustomCurveDocument.ControlMode.FLOW_RAW,
            listOf(CustomCurveDocument.Stage(20, 0), CustomCurveDocument.Stage(30, 0)))
        val plan = CurveRecipePlan.from(CurveLibraryItem(raw.id, raw.name, "", "", null,
            customDocument = raw))
        assertEquals(listOf(0, 0), plan.stageWaterTenthsMl)
        assertEquals(0, plan.assignedWaterTenthsMl)
        assertNull(plan.totalWaterLimitMl)
        assertNull(plan.preinfusionSeconds)
        assertNull(plan.firstStageDurationSettingSeconds)
        assertEquals(1800, plan.targetWeightHundredthsGram)
        assertFalse(plan.preview.renderable)
        assertTrue(plan.preview.stages.all { it.unit == CurveStageParameters.Unit.RAW_UNKNOWN })
    }

    @Test fun oneAndFourStagePlansKeepEveryRealWaterValue() {
        val one = CurveRecipePlan.from(item(factory("one", false, listOf(0), listOf(0), 0)))
        val four = CurveRecipePlan.from(item(factory("four", true, listOf(0, 40, 60, 80),
            listOf(0, 1, 9, 90), 100)))
        assertEquals(1, one.preview.stages.size)
        assertEquals(listOf(0), one.stageWaterTenthsMl)
        assertEquals(0, one.assignedWaterTenthsMl)
        assertEquals(4, four.preview.stages.size)
        assertEquals(listOf(0, 10, 90, 900), four.stageWaterTenthsMl)
        assertEquals(1000, four.assignedWaterTenthsMl)
        assertEquals(100, four.totalWaterLimitMl)
    }
}
