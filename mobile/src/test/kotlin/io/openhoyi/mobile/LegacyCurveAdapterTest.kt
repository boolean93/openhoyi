package io.openhoyi.mobile

import io.openhoyi.protocol.CoffeeCommands
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LegacyCurveAdapterTest {
    private fun factory() = File("src/main/assets/factory_curves_v3.tsv").inputStream().use(FactoryCurveCatalog::load)
    private fun proof(curves: List<FactoryCurve>) =
        File("src/main/assets/factory_wire_v1.tsv").inputStream().use { temp ->
            File("src/main/assets/factory_slot_wire_v1.tsv").inputStream().use { slots ->
                FactoryWireProof.load(temp, curves, slots)
            }
        }

    private fun legacy(curve: FactoryCurve, index: Int): LegacyCurve {
        val row = JSONObject().put("name", curve.name).put("category", curve.category)
            .put("flow", curve.flowMl).put("weight", curve.weightTenthsGram)
            .put("wdelta", curve.weightCompensationTenthsGram).put("time", curve.preinfusionSeconds)
            .put("temp", curve.temperatureC).put("press", curve.pressureLogic)
            .put("chart", curve.variableFlowLogic).put("seg", curve.segmentCount)
            .put("time1", curve.firstDurationSeconds)
        for (part in 1..4) {
            row.put("press$part", curve.targets[part - 1])
            row.put("flow$part", curve.segmentFlowMl[part - 1])
        }
        return LegacyCurve(index, curve.name, curve.category, true, curve.temperatureC,
            curve.flowMl, curve.weightTenthsGram, curve.segmentCount, row.toString())
    }

    @Test fun convertsAllFactoryShapedRowsToTheSameTwelveLegacyFrames() {
        val curves = factory()
        val oracle = proof(curves)
        for ((index, curve) in curves.withIndex()) for (slot in listOf(7, 1, 2, 3, 4, 5))
            for (scale in listOf(false, true)) {
                val profile = LegacyCurveAdapter.profile(legacy(curve, index), scale, slot)
                assertNotNull("${curve.id}/$slot/$scale", profile)
                assertEquals("${curve.id}/$slot/$scale", oracle.expected(curve.id, scale, slot),
                    CoffeeCommands.start(requireNotNull(profile).parameters).frame.hex())
            }
    }

    @Test fun incompleteOrAmbiguousRowsDoNotBecomeControlCandidates() {
        val source = legacy(factory().first(), 0)
        val row = JSONObject(source.rawJson)
        assertNull(LegacyCurveAdapter.profile(source.copy(rawJson = row.remove("temp").let { row.toString() }), false))
        row.put("temp", 93.5)
        assertNull(LegacyCurveAdapter.profile(source.copy(rawJson = row.toString()), false))
        row.put("temp", 93).put("chart", "true")
        assertNull(LegacyCurveAdapter.profile(source.copy(rawJson = row.toString()), false))
        assertNull(LegacyCurveAdapter.profile(source, false, 6))
        assertNull(LegacyCurveAdapter.profile(source.copy(rawJson = "null"), false))
    }

    @Test fun firstSegmentFlowModeMatchesAnIndependentOldEncoderVector() {
        val turbo = factory().first { it.name == "HOYI Turbo Shot" }
        val source = legacy(turbo, 2)
        val modified = source.copy(rawJson = JSONObject(source.rawJson)
            .put("seg1FlowMode", true).toString())
        val profile = requireNotNull(LegacyCurveAdapter.profile(modified, false))
        assertEquals("02D75C00968A141E00000096000546000000004A",
            CoffeeCommands.start(profile.parameters).frame.hex())
    }
}
