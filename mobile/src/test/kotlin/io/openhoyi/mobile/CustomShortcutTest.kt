package io.openhoyi.mobile

import io.openhoyi.protocol.CoffeeCommands
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CustomShortcutTest {
    private class Disk : CustomCurveStore.Storage {
        var bytes: String? = null
        override fun read() = bytes
        override fun write(value: String) { bytes = value }
    }
    private fun doc() = CustomCurveDocument("draft-00000000-0000-4000-8000-000000000001", "My recipe", 92, 3600,
        CustomCurveDocument.ControlMode.PRESSURE,
        listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)), totalWaterLimitMl = 70)
    private fun library(store: CustomCurveStore) = CurveLibrary(emptyList(), draftsProvider = store::items,
        enableCustomPressureExecution = true)

    @Test fun importedAndReloadedReferenceUsesSavedRecipeAndWireSevenInAllFivePositions() {
        val disk = Disk()
        CustomCurveStore(disk).importDocument(CustomCurveDocument.decode(doc().encode()))
        val reloaded = CustomCurveStore(disk)
        val library = library(reloaded)
        val savedBytes = disk.bytes
        assertTrue(PresetSlots.canAssign(requireNotNull(library.find(doc().id)), library))
        for (slot in 1..5) {
            val route = PresetSlots.route(slot, doc().id, library) as PresetSlots.Route.Ready
            assertEquals(doc().id, route.curveId)
            assertEquals(7, route.wireSlot)
            assertTrue(route.selectCurve)
            for (scale in listOf(false, true)) {
                val profile = requireNotNull(library.resolve(route.curveId, scale, route.wireSlot))
                assertEquals(doc().id, profile.id) // history keeps actual recipe, not shortcut index
                assertTrue(library.validated(profile))
                assertTrue(library.permitsCustomStart(route.curveId, profile.parameters))
                assertEquals(7, CoffeeCommands.start(profile.parameters).frame.toByteArray()[1].toInt() and 7)
                assertEquals("0200070000", CoffeeCommands.stop(profile.parameters.slot).frame.hex())
                assertNull(library.resolve(route.curveId, scale, slot)) // no new device-slot permission
            }
        }
        assertEquals(savedBytes, disk.bytes) // resolving/navigating never writes the recipe
    }

    @Test fun editingUpdatesSameReferenceAndRevokesOldParameters() {
        val store = CustomCurveStore(Disk()); store.save(doc())
        val library = library(store)
        val old = requireNotNull(library.resolve(doc().id, false))
        val oldPreview = HomePresetPreview.from(library.find(doc().id), CurveStageParameters.SharedScale.from(library.items))
        assertNotNull(oldPreview)
        store.save(doc().copy(name = "Edited", temperatureC = 93, totalWaterLimitMl = 40))
        val route = PresetSlots.route(2, doc().id, library) as PresetSlots.Route.Ready
        val current = requireNotNull(library.resolve(route.curveId, false, route.wireSlot))
        assertEquals("Edited", current.name)
        assertNotNull(HomePresetPreview.from(library.find(doc().id), CurveStageParameters.SharedScale.from(library.items)))
        assertEquals(93, current.temperatureC)
        assertEquals(40, current.maximumWaterMl)
        assertFalse(library.validated(old))
        assertFalse(library.permitsCustomStart(route.curveId, old.parameters))
    }

    @Test fun missingAndUnstartableDocumentsNeverFallBackOrBecomeAssignable() {
        val store = CustomCurveStore(Disk()); val library = library(store)
        assertEquals(doc().id, PresetSlots.curveId(1, doc().id))
        assertEquals(PresetSlots.Route.Missing, PresetSlots.route(1, doc().id, library))
        store.save(doc().copy(controlMode = CustomCurveDocument.ControlMode.FLOW_RAW))
        assertEquals(PresetSlots.Route.Unavailable, PresetSlots.route(1, doc().id, library))
        assertFalse(PresetSlots.canAssign(requireNotNull(library.find(doc().id)), library))
        assertEquals(PresetSlots.Route.Missing, PresetSlots.route(1, "draft-corrupt", library))
    }

    @Test fun factoryShortcutsRetainTheirOriginalWireSlotsAndDefaults() {
        val library = CurveLibrary(emptyList())
        for (slot in 1..5) {
            assertEquals(PresetSlots.Route.Ready("factory-v3-00$slot", slot, false), PresetSlots.route(slot, null, library))
            assertEquals(PresetSlots.Route.Ready("factory-v3-100", slot, false), PresetSlots.route(slot, "factory-v3-100", library))
        }
    }

    @Test fun configuredFlowRecipesRetainTheirExistingSlotSevenFirmwareGate() {
        val factory = File("src/main/assets/factory_curves_v3.tsv").inputStream().use(FactoryCurveCatalog::load)
            .first { it.variableFlowLogic }
        val doc = requireNotNull(CustomCurveDocument.fromLibraryItem(
            CurveLibraryItem(factory.id, factory.name, "", "", null, factoryCurve = factory)))
        val store = CustomCurveStore(Disk()); store.importDocument(doc)
        val library = library(store)
        val route = PresetSlots.route(5, doc.id, library) as PresetSlots.Route.Ready
        for (scale in listOf(false, true)) {
            val profile = requireNotNull(library.resolve(route.curveId, scale, route.wireSlot))
            assertTrue(library.permitsCustomStart(doc.id, profile.parameters))
            assertEquals(7, profile.parameters.slot)
            assertFalse(library.permitsCustomStart(doc.id, profile.parameters.copy(slot = 5)))
        }
    }
}
