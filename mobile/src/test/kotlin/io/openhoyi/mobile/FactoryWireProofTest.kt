package io.openhoyi.mobile

import io.openhoyi.protocol.CoffeeCommands
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FactoryWireProofTest {
    private fun factory() = File("src/main/assets/factory_curves_v3.tsv").inputStream().use(FactoryCurveCatalog::load)
    private fun proof(factory: List<FactoryCurve>) =
        File("src/main/assets/factory_wire_v1.tsv").inputStream().use { temp ->
            File("src/main/assets/factory_slot_wire_v1.tsv").inputStream().use { slots ->
                FactoryWireProof.load(temp, factory, slots)
            }
        }

    @Test fun twoHundredFramesMatchExtractedLegacyEncoder() {
        val curves = factory()
        val oracle = proof(curves)
        assertTrue(oracle.allowedFrames().contains(oracle.expected("factory-v3-001", false)))
        assertTrue(oracle.allowedFrames().contains(oracle.expected("factory-v3-001", true)))
        assertTrue(oracle.allowedFrames().size in 500..1200)
        for (curve in curves) for (scale in listOf(false, true)) {
            val profile = FactoryCurveAdapter.profile(curve, scale)
            assertTrue(curve.id, oracle.validated(profile))
            assertEquals(oracle.expected(curve.id, scale), CoffeeCommands.start(profile.parameters).frame.hex())
        }
        val espresso = curves.first()
        assertEquals(70, FactoryCurveAdapter.profile(espresso, false).maximumWaterMl)
        assertEquals(144, FactoryCurveAdapter.profile(espresso, true).maximumWaterMl)
        assertEquals(0, FactoryCurveAdapter.profile(espresso, false).targetHundredthsGram)
        assertEquals(3600, FactoryCurveAdapter.profile(espresso, true).targetHundredthsGram)
    }

    @Test fun fivePresetSlotsMatchLegacyEncoderForBothScaleModes() {
        val curves = factory()
        val oracle = proof(curves)
        for (curve in curves) for (slot in 1..5) for (scale in listOf(false, true)) {
            val profile = FactoryCurveAdapter.profile(curve, scale, slot)
            assertTrue("${curve.id}/$slot/$scale", oracle.validated(profile))
            assertEquals(oracle.expected(curve.id, scale, slot), CoffeeCommands.start(profile.parameters).frame.hex())
        }
        assertFalse(oracle.validated(FactoryCurveAdapter.profile(curves.first(), false, 1)
            .copy(parameters = FactoryCurveAdapter.profile(curves.first(), false, 1).parameters.copy(slot = 6))))
    }

    @Test fun mismatchedFrameOrSourceFailsClosed() {
        val curves = factory()
        val lines = File("src/main/assets/factory_wire_v1.tsv").readLines()
        assertThrows(IllegalArgumentException::class.java) {
            FactoryWireProof.load(lines.toMutableList().apply { this[1] = this[1].replace("02175C", "021F5C") }
                .joinToString("\n").byteInputStream(), curves)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FactoryWireProof.load(lines.toMutableList().apply { this[0] = this[0].replace("b55c8b", "a55c8b") }
                .joinToString("\n").byteInputStream(), curves)
        }
        val slotLines = File("src/main/assets/factory_slot_wire_v1.tsv").readLines()
        assertThrows(IllegalArgumentException::class.java) {
            File("src/main/assets/factory_wire_v1.tsv").inputStream().use {
                FactoryWireProof.load(it, curves, slotLines.toMutableList().apply {
                    this[1] = this[1].replace("02115C", "02125C")
                }.joinToString("\n").byteInputStream())
            }
        }
    }

    @Test fun libraryRequiresProofAndRejectsModifiedProfiles() {
        val curves = factory()
        val unproved = CurveLibrary(curves)
        assertFalse(unproved.canStart(requireNotNull(unproved.find("factory-v3-001"))))
        assertNull(unproved.resolve("factory-v3-001", false))
        val proved = CurveLibrary(curves, proof(curves))
        val item = requireNotNull(proved.find("factory-v3-001"))
        assertTrue(proved.canStart(item))
        for (mode in listOf(false, true)) {
            val profile = requireNotNull(proved.resolve(item.id, mode))
            assertTrue(proved.validated(profile))
            assertFalse(proved.validated(profile.copy(targetHundredthsGram = 1)))
            assertFalse(proved.validated(profile.copy(parameters = profile.parameters.copy(maximumWaterMl = 1))))
        }
    }
}
