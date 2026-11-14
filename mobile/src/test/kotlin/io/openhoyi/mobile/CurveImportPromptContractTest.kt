package io.openhoyi.mobile

import io.openhoyi.protocol.CustomCurveStartPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Tests the published examples against production code, not a second schema implementation. */
class CurveImportPromptContractTest {
    private fun document(name: String) = CustomCurveDocument.decode(
        File(requireNotNull(System.getProperty("hoyi.curve.prompt.dir")), "examples/$name.json").readText())
    private fun executable(doc: CustomCurveDocument) {
        assertEquals(doc, CustomCurveDocument.decode(doc.encode()))
        for (scale in listOf(false, true)) {
            val profile = requireNotNull(CustomCurveAdapter.profile(doc, scale))
            assertTrue(CustomCurveStartPolicy.permits(profile.parameters))
        }
    }
    @Test fun publishedExamplesAreExecutableAndRoundTripWithoutLoss() {
        executable(document("pressure")); executable(document("flow"))
        assertEquals(30, document("pressure").stages.first().target)
        assertEquals(3600, document("flow").targetHundredthsGram)
        assertFalse(document("pressure").configuration!!.pressureLogic)
        assertTrue(document("flow").configuration!!.pressureLogic)
    }
    @Test fun supportedModeCombinationsUseProductionPresentationAndExecution() {
        val pressure = document("pressure")
        for (logic in listOf(false, true)) {
            val p = pressure.copy(configuration = pressure.configuration!!.copy(pressureLogic = logic))
            executable(p)
            assertFalse(p.isFlowStage(0)); assertFalse(p.isFlowStage(1))
            val flow = document("flow")
            for (firstFlow in listOf(false, true)) {
                val f = flow.copy(configuration = flow.configuration!!.copy(pressureLogic = logic, firstSegmentFlowMode = firstFlow))
                executable(f)
                assertEquals(firstFlow, f.isFlowStage(0)); assertTrue(f.isFlowStage(1))
            }
        }
    }
    @Test fun importSuccessIsNotMisrepresentedAsExecutionSuccess() {
        val p = document("pressure")
        val one = p.copy(stages = p.stages.take(1), configuration = p.configuration!!.copy(
            inactiveStages = List(3) { CustomCurveDocument.Stage(0, 0) }))
        assertEquals(one, CustomCurveDocument.decode(one.encode()))
        assertNull(CustomCurveAdapter.profile(one, false))
        assertNull(CustomCurveAdapter.profile(p.copy(targetHundredthsGram = 3601), true))
        assertNull(CustomCurveAdapter.profile(p.copy(stages = listOf(p.stages[0].copy(target = 4), p.stages[1])), false))
        assertNull(CustomCurveAdapter.profile(p.copy(configuration = p.configuration!!.copy(firstDurationSeconds = 255)), false))
    }
    @Test fun rejectsMissingExtraKeysAndModeContradictions() {
        val p = document("pressure")
        for (raw in listOf(JSONObject(p.encode()).put("extra", 1).toString(),
            JSONObject(p.encode()).also { it.remove("totalWaterLimitMl") }.toString(),
            JSONObject(p.encode()).put("temperatureC", "92").toString())) {
            assertTrue(runCatching { CustomCurveDocument.decode(raw) }.isFailure)
        }
        assertTrue(runCatching { p.copy(configuration = p.configuration!!.copy(variableFlowLogic = true)).encode() }.isFailure)
    }
}
