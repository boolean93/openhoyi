package io.openhoyi.mobile

import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CurveLibraryRetirementTest {
    @Test fun retiredCaptureIdsCannotBeSelectedResolvedOrStarted() {
        val factory = File("src/main/assets/factory_curves_v3.tsv").inputStream().use(FactoryCurveCatalog::load)
        val proof = File("src/main/assets/factory_wire_v1.tsv").inputStream().use { temp ->
            File("src/main/assets/factory_slot_wire_v1.tsv").inputStream().use { slots ->
                FactoryWireProof.load(temp, factory, slots)
            }
        }
        val library = CurveLibrary(factory, proof)
        assertEquals(factory.map { it.id }, library.items.map { it.id })
        for (profile in CurveCatalog.profiles) {
            assertNull(library.find(profile.id))
            assertNull(library.resolve(profile.id, false))
            assertNull(library.resolve(profile.id, true))
            assertFalse(library.canStart(CurveLibraryItem(profile.id, profile.name, "", "", profile)))
            assertFalse(library.validated(profile))
        }
        assertTrue(library.canStart(requireNotNull(library.find(factory.first().id))))
    }

    @Test fun retiredCapturesKeepTheirHistoricalRecordsAndUsageWithoutBecomingAnotherRecipe() {
        val shots = object : ShotHistory.Storage {
            var value = ""
            override fun read() = value
            override fun write(value: String) { this.value = value }
        }
        val uses = object : CurveUsageLedger.Storage {
            var value = ""
            override fun read() = value
            override fun write(value: String) { this.value = value }
        }
        val ledger = CurveUsageLedger(uses)
        val history = ShotHistory(shots, now = { 1000L }, newId = { "old-shot" },
            onObservedUse = { ledger.record(it.id, it.curveId, it.startedAtMs) })
        history.begin("capture-1")
        history.observeRunning()
        history.transition(ExtractionState.ENDED_OBSERVED, null, 2700)
        val before = shots.value
        assertEquals("capture-1", ShotHistory(shots, now = { 2000L }).entries.single().curveId)
        assertEquals(before, shots.value)
        val latest = CurveUsageLedger(uses).latest()
        assertEquals("capture-1", latest?.curveId)
        val library = CurveLibrary(emptyList())
        assertEquals(HomeLastCurve.Unavailable(HomeLastCurve.Reason.MISSING),
            HomeLastCurve.from(latest, library.items, null, library::canStart))
    }
}
