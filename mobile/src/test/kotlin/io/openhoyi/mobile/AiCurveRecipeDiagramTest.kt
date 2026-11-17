package io.openhoyi.mobile

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AiCurveRecipeDiagramTest {
    private fun item(d: CustomCurveDocument) = CurveLibraryItem(d.id, d.name, "", "", null, customDocument = d)
    private fun factoryItem(f: FactoryCurve) = CurveLibraryItem(f.id, f.name, "", "", null, f)
    private fun doc(count: Int = 2) = CustomCurveDocument(CustomCurveDocument.newId(), "Synthetic", 93, 3600,
        CustomCurveDocument.ControlMode.PRESSURE, List(count) { CustomCurveDocument.Stage(90 - it * 20, 100 + it * 10) })
    @Test fun realFactoryTwoThreeFourStageExamplesPreserveOnlyTheirOrderedTargetsAndSettings() {
        val file = File("src/main/assets/factory_curves_v3.tsv"); val before = file.readBytes()
        val all = file.inputStream().use(FactoryCurveCatalog::load)
        assertEquals(mapOf(2 to 20, 3 to 78, 4 to 2), all.groupingBy { it.segmentCount }.eachCount())
        for (id in listOf("factory-v3-001", "factory-v3-002", "factory-v3-086")) {
            val source = all.single { it.id == id }; val model = AiCurveRecipeDiagram.from(factoryItem(source))
            assertEquals(source.segmentCount, model.stages.size)
            assertEquals((1..source.segmentCount).toList(), model.stages.map { it.ordinal })
            assertEquals(source.targets.take(source.segmentCount), model.stages.map { it.targetTenths })
            assertEquals(source.segmentFlowMl.take(source.segmentCount).map { it * 10 }, model.stages.map { it.waterTenthsMl })
            assertEquals(source.temperatureC, model.temperatureC)
            assertEquals(source.preinfusionSeconds, model.preinfusionSeconds)
            assertEquals(source.firstDurationSeconds, model.firstDurationSeconds)
        }
        val flow = AiCurveRecipeDiagram.from(factoryItem(all.single { it.id == "factory-v3-086" }))
        assertEquals(listOf(CurveStageParameters.Unit.PRESSURE_BAR) + List(3) { CurveStageParameters.Unit.FLOW_ML_PER_SECOND }, flow.stages.map { it.unit })
        assertArrayEquals(before, file.readBytes())
    }
    @Test fun customTwoThreeFourStagesDoNotInventSamplesTimeOrIncludeInactiveSlots() {
        for (count in 2..4) {
            val original = doc(count); val encoded = original.encode(); val model = AiCurveRecipeDiagram.from(item(original))
            assertEquals(count, model.stages.size)
            assertEquals(original.stages.map { it.target }, model.stages.map { it.targetTenths })
            assertEquals(original.stages.map { it.waterTenthsMl }, model.stages.map { it.waterTenthsMl })
            assertNull(model.preinfusionSeconds); assertNull(model.firstDurationSeconds)
            assertEquals(encoded, original.encode())
        }
        val configured = doc().copy(configuration = CustomCurveDocument.Configuration(firstDurationSeconds = 7, preinfusionSeconds = 3,
            inactiveStages = listOf(CustomCurveDocument.Stage(100, 900), CustomCurveDocument.Stage(80, 800))))
        val model = AiCurveRecipeDiagram.from(item(configured))
        assertEquals(2, model.stages.size); assertEquals(4, configured.allStages.size)
        assertEquals(7, model.firstDurationSeconds); assertEquals(3, model.preinfusionSeconds)
        assertFalse(model.stages.any { it.waterTenthsMl == 900 || it.waterTenthsMl == 800 })
    }
    @Test fun legacyLegalOneStageAndFractionalWaterAreStillDisplayableWithoutNewHardLimits() {
        val legacy = doc(1).copy(stages = listOf(CustomCurveDocument.Stage(120, 1)))
        val roundtrip = CustomCurveDocument.decode(legacy.encode())
        val model = AiCurveRecipeDiagram.from(item(roundtrip))
        assertTrue(model.renderable); assertEquals(1, model.stages.size)
        assertEquals(120, model.stages.single().targetTenths); assertEquals(1, model.assignedWaterTenthsMl)
        assertTrue(model.summary(true).contains("Stage water sum: 0.1 mL"))
        assertNull(model.firstDurationSeconds)
    }
    @Test fun unitsHaveIndependentScalesAndFirstFlowFlagDoesNotBecomeAFakePressureStage() {
        val flow = doc().copy(controlMode = CustomCurveDocument.ControlMode.FLOW,
            stages = listOf(CustomCurveDocument.Stage(30, 100), CustomCurveDocument.Stage(25, 200)),
            configuration = CustomCurveDocument.Configuration(variableFlowLogic = true, firstDurationSeconds = 5,
                inactiveStages = listOf(CustomCurveDocument.Stage(0, 0), CustomCurveDocument.Stage(0, 0))))
        val mixed = AiCurveRecipeDiagram.from(item(flow))
        assertEquals(CurveStageParameters.Unit.PRESSURE_BAR, mixed.stages[0].unit)
        assertEquals(CurveStageParameters.Unit.FLOW_ML_PER_SECOND, mixed.stages[1].unit)
        assertEquals(3f / 12, mixed.targetFraction(mixed.stages[0])!!, 0.0001f)
        assertEquals(2.5f / 6, mixed.targetFraction(mixed.stages[1])!!, 0.0001f)
        val allFlow = AiCurveRecipeDiagram.from(item(flow.copy(configuration = flow.configuration!!.copy(firstSegmentFlowMode = true))))
        assertTrue(allFlow.stages.all { it.unit == CurveStageParameters.Unit.FLOW_ML_PER_SECOND })
        assertTrue(allFlow.summary(true).contains("Stage control: flow"))
    }
    @Test fun sharedPressureAxisTicksBoundRealTargetsWithoutAddingAFlowAxis() {
        val model = AiCurveRecipeDiagram.from(item(doc().copy(stages = listOf(CustomCurveDocument.Stage(120, 100), CustomCurveDocument.Stage(0, 100)))))
        assertEquals(listOf(0f, 3f, 6f, 9f, 12f), model.axisTicks(CurveStageParameters.Unit.PRESSURE_BAR))
        assertEquals(12f, model.axisMaximum(CurveStageParameters.Unit.PRESSURE_BAR)!!, 0f)
        assertNull(model.axisMaximum(CurveStageParameters.Unit.FLOW_ML_PER_SECOND))
        assertTrue(model.axisTicks(CurveStageParameters.Unit.FLOW_ML_PER_SECOND).isEmpty())
        assertEquals(1f, model.targetFraction(model.stages[0])!!, 0f)
        assertEquals(0f, model.targetFraction(model.stages[1])!!, 0f)
    }
    @Test fun mixedAndPureFlowAxesRemainSeparateAndNeverRelabelPressureLogicAsAUnit() {
        val flow = doc().copy(controlMode = CustomCurveDocument.ControlMode.FLOW,
            stages = listOf(CustomCurveDocument.Stage(30, 100), CustomCurveDocument.Stage(100, 200)),
            configuration = CustomCurveDocument.Configuration(variableFlowLogic = true, pressureLogic = true, firstDurationSeconds = 5,
                inactiveStages = listOf(CustomCurveDocument.Stage(0, 0), CustomCurveDocument.Stage(0, 0))))
        val mixed = AiCurveRecipeDiagram.from(item(flow))
        assertEquals(listOf(0f, 3f, 6f, 9f, 12f), mixed.axisTicks(CurveStageParameters.Unit.PRESSURE_BAR))
        assertEquals(listOf(0f, 2.5f, 5f, 7.5f, 10f), mixed.axisTicks(CurveStageParameters.Unit.FLOW_ML_PER_SECOND))
        val pure = AiCurveRecipeDiagram.from(item(flow.copy(configuration = flow.configuration!!.copy(firstSegmentFlowMode = true))))
        assertNull(pure.axisMaximum(CurveStageParameters.Unit.PRESSURE_BAR))
        assertEquals(10f, pure.axisMaximum(CurveStageParameters.Unit.FLOW_ML_PER_SECOND)!!, 0f)
        val raw = AiCurveRecipeDiagram.from(item(doc().copy(controlMode = CustomCurveDocument.ControlMode.FLOW_RAW)))
        assertNull(raw.axisMaximum(CurveStageParameters.Unit.RAW_UNKNOWN))
        assertTrue(raw.axisTicks(CurveStageParameters.Unit.RAW_UNKNOWN).isEmpty())
    }
    @Test fun unknownUnitsDoNotGetInventedBarOrGeometryAndStoppingIsOnlyConditionalSettings() {
        val raw = doc().copy(controlMode = CustomCurveDocument.ControlMode.FLOW_RAW)
        val unknown = AiCurveRecipeDiagram.from(item(raw))
        assertFalse(unknown.renderable); assertNull(unknown.targetFraction(unknown.stages.first()))
        val known = AiCurveRecipeDiagram.from(item(doc()))
        assertTrue(known.summary(true).contains("Actual stopping depends on"))
        assertTrue(known.summary(false).contains("实际结束条件取决于"))
        assertTrue(known.summary(true).contains("First-stage duration setting: not set"))
    }
}
