package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CurveDeletionTest {
    private class Disk : CustomCurveStore.Storage {
        var bytes: String? = null
        var fail = false
        override fun read() = bytes
        override fun write(value: String) { check(!fail) { "disk full" }; bytes = value }
    }
    private fun doc() = CustomCurveDocument("draft-00000000-0000-4000-8000-000000000001", "My recipe", 92, 3600,
        CustomCurveDocument.ControlMode.PRESSURE,
        listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
    private fun library(store: CustomCurveStore) = CurveLibrary(emptyList(), draftsProvider = store::items,
        enableCustomPressureExecution = true, presetBinding = store::presetBinding)

    @Test fun deletionPersistsNameButRevokesListShortcutsAndPreviouslyResolvedStart() {
        val disk = Disk(); val store = CustomCurveStore(disk); store.save(doc())
        val library = library(store)
        val profile = requireNotNull(library.resolve(doc().id, false))
        val ledger = CurveUsageLedger(object : CurveUsageLedger.Storage {
            override fun read() = ""
            override fun write(value: String) = Unit
        })
        ledger.record("shot", doc().id, 1)
        val refs = mapOf(1 to doc().id, 3 to PresetSlots.bind(doc().id))
        store.delete(doc().id, refs)
        assertNull(store.find(doc().id)); assertTrue(store.list().isEmpty()); assertTrue(library.items.isEmpty())
        assertTrue(CurveOrdering.recentUsed(library.items, ledger, 3).isEmpty())
        assertEquals(HomeLastCurve.Unavailable(HomeLastCurve.Reason.MISSING),
            HomeLastCurve.from(ledger.latest(), library.items, canStart = library::canStart))
        assertEquals(1L, ledger.stats(doc().id).count)
        assertFalse(library.validated(profile)); assertFalse(library.permitsCustomStart(doc().id, profile.parameters))
        val reloaded = CustomCurveStore(disk); val reopened = library(reloaded)
        assertEquals("My recipe", reloaded.historicalName(doc().id))
        for ((slot, raw) in refs) {
            assertEquals(PresetSlots.EMPTY, PresetSlots.curveId(slot, raw, reopened))
            assertEquals(PresetSlots.Route.Missing, PresetSlots.route(slot, raw, reopened))
        }
        assertEquals("factory-v3-002", PresetSlots.curveId(2, null, reopened))
        assertNull(reopened.resolve(doc().id, false))
    }
    @Test fun failedDeleteAndFailedUndoKeepEveryPublishedStateAndRevision() {
        val disk = Disk(); val store = CustomCurveStore(disk); store.save(doc())
        val library = library(store); val before = disk.bytes; val revision = store.revision
        disk.fail = true
        assertTrue(runCatching { store.delete(doc().id, mapOf(1 to doc().id)) }.isFailure)
        assertEquals(before, disk.bytes); assertEquals(revision, store.revision)
        assertNotNull(library.find(doc().id)); assertTrue(PresetSlots.route(1, doc().id, library) is PresetSlots.Route.Ready)
        disk.fail = false; val token = store.delete(doc().id, mapOf(1 to doc().id))
        val archived = disk.bytes; val archivedRevision = store.revision; disk.fail = true
        assertTrue(runCatching { store.undo(token) }.isFailure)
        assertEquals(archived, disk.bytes); assertEquals(archivedRevision, store.revision)
        assertNull(library.find(doc().id)); assertEquals(PresetSlots.Route.Missing, PresetSlots.route(1, doc().id, library))
    }
    @Test fun undoRestoresIdAndOldSlotButLeavesNewSameIdAndOtherBindingsAlone() {
        val store = CustomCurveStore(Disk()); store.save(doc()); val library = library(store)
        val old = PresetSlots.bind(doc().id)
        val token = store.delete(doc().id, mapOf(1 to old))
        val rebound = PresetSlots.bind(doc().id)
        val other = "factory-v3-020"
        assertTrue(store.undo(token)); assertEquals(doc(), store.find(doc().id))
        assertEquals(doc().id, PresetSlots.curveId(1, old, library))
        assertEquals(doc().id, PresetSlots.curveId(1, rebound, library))
        assertEquals(other, PresetSlots.curveId(1, other, library))
        assertFalse(store.undo(token))
    }
    @Test fun sameIdReimportRestoresWithoutRebindingClearedSlotsAndRevokesOldUndo() {
        val store = CustomCurveStore(Disk()); store.save(doc()); val library = library(store)
        val token = store.delete(doc().id, mapOf(1 to doc().id))
        assertEquals(CustomCurveStore.ImportStatus.CONFLICT, store.importDocument(doc().copy(name = "Other")).status)
        assertNull(store.find(doc().id))
        assertEquals(CustomCurveStore.ImportStatus.SAVED, store.importDocument(doc()).status)
        assertEquals(CustomCurveStore.ImportStatus.ALREADY_EXISTS, store.importDocument(doc()).status)
        assertEquals(PresetSlots.EMPTY, PresetSlots.curveId(1, doc().id, library))
        assertEquals(doc().id, PresetSlots.curveId(1, PresetSlots.bind(doc().id), library))
        store.save(doc().copy(name = "New"))
        assertFalse(store.undo(token)); assertEquals("New", store.find(doc().id)?.name)
        // Another delete/undo must not restore a slot already cleared by the first deletion.
        val next = store.delete(doc().id, mapOf(1 to doc().id))
        assertTrue(store.undo(next)); assertEquals(PresetSlots.EMPTY, PresetSlots.curveId(1, doc().id, library))
    }
    @Test fun archivedRawDocumentsAreDeletableButCannotBeResurrectedByAnOldEditor() {
        val store = CustomCurveStore(Disk()); val raw = doc().copy(controlMode = CustomCurveDocument.ControlMode.FLOW_RAW)
        store.save(raw); val token = store.delete(raw.id, emptyMap())
        assertNull(store.find(raw.id)); assertEquals(raw.name, store.historicalName(raw.id))
        assertTrue(runCatching { store.save(raw.copy(name = "Stale editor"), raw) }.isFailure)
        assertTrue(store.undo(token)); store.save(raw.copy(name = "New content"))
        assertTrue(runCatching { store.save(raw.copy(name = "Stale editor"), raw) }.isFailure)
        assertTrue(runCatching { store.delete(raw.id, emptyMap(), raw) }.isFailure)
        assertEquals("New content", store.find(raw.id)?.name)
        assertTrue(runCatching { store.delete("factory-v3-001", emptyMap()) }.isFailure)
    }
    @Test fun useGuardRunsAtCommitAndOnlyBlocksTheOwnedRecipe() {
        val disk = Disk(); val store = CustomCurveStore(disk); store.save(doc()); val before = disk.bytes
        assertTrue(runCatching { store.delete(doc().id, emptyMap()) { false } }.isFailure)
        assertEquals(before, disk.bytes)
        for (phase in 0..2) {
            assertTrue(CurveDeletionPolicy.inUse("mine", if (phase == 0) "mine" else null,
                "mine", phase == 1, "mine", phase == 2))
            assertFalse(CurveDeletionPolicy.inUse("other", "mine", "mine", true, "mine", true))
        }
        assertFalse(CurveDeletionPolicy.inUse("mine", null, "mine", false, "mine", false))
    }
    @Test fun versionOneMigratesOnWriteAndArchiveMetadataIsChecksummed() {
        val disk = Disk(); val docs = JSONArray().put(doc().toJson())
        disk.bytes = JSONObject().put("storeVersion", 1).put("documents", docs)
            .put("checksum", CurveDocumentJson.checksum(docs)).toString()
        val store = CustomCurveStore(disk); assertEquals(doc(), store.find(doc().id))
        store.delete(doc().id, mapOf(1 to doc().id))
        assertEquals(2, JSONObject(disk.bytes!!).getInt("storeVersion"))
        val saved = disk.bytes
        disk.bytes = JSONObject(saved!!).put("archived", JSONObject()).toString()
        assertTrue(runCatching { CustomCurveStore(disk) }.isFailure)
        disk.bytes = JSONObject(saved).put("clearedBindings", JSONObject()).toString()
        assertTrue(runCatching { CustomCurveStore(disk) }.isFailure)
    }
}
