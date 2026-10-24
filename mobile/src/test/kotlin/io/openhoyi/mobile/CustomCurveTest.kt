package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class CustomCurveTest {
    private class Store : CustomCurveStore.Storage {
        var value: String? = null
        var fail = false
        override fun read() = value
        override fun write(value: String) { if (fail) error("disk full"); this.value = value }
    }
    private fun doc(id: String = "draft-00000000-0000-0000-0000-000000000001") = CustomCurveDocument(id, "My curve", 93, 3600,
        CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
    private fun rejects(block: () -> Unit) = assertTrue(runCatching(block).isFailure)
    @Test fun documentRoundTripContainsOnlyParametersAndVersionsAreStrict() {
        val document = doc()
        val json = document.encode()
        assertEquals(document, CustomCurveDocument.decode(json))
        assertFalse(json.contains("notes")); assertFalse(json.contains("beanId")); assertFalse(json.contains("trace"))
        rejects { CustomCurveDocument.decode(json.replace("\"schema\":1", "\"schema\":2")) }
        rejects { CustomCurveDocument.decode(json.dropLast(1) + ",\"notes\":\"private\"}") }
        rejects { CustomCurveDocument.decode(" ".repeat(70_000) + json) }
        rejects { CustomCurveDocument.decode(json + " garbage") }
        assertEquals(CustomCurveDocument.ControlMode.FLOW_RAW, CustomCurveDocument.decode(document.copy(controlMode = CustomCurveDocument.ControlMode.FLOW_RAW).encode()).controlMode)
    }
    @Test fun validatesKnownRangesAndAtMostFourStages() {
        rejects { doc().copy(temperatureC = 74).encode() }
        rejects { doc().copy(targetHundredthsGram = -1).encode() }
        rejects { doc().copy(stages = List(5) { CustomCurveDocument.Stage(90, 400) }).encode() }
        rejects { doc().copy(stages = listOf(CustomCurveDocument.Stage(121, 400))).encode() }
        rejects { doc().copy(stages = listOf(CustomCurveDocument.Stage(90, 65536))).encode() }
        rejects { doc().copy(id = "capture-1").encode() }
    }
    @Test fun duplicateImportIsIdempotentAndConflictNeverOverwrites() {
        val disk = Store()
        val store = CustomCurveStore(disk)
        assertEquals(CustomCurveStore.ImportStatus.SAVED, store.importDocument(doc()).status)
        val original = disk.value
        assertEquals(CustomCurveStore.ImportStatus.ALREADY_EXISTS, store.importDocument(doc()).status)
        assertEquals(original, disk.value)
        assertEquals(CustomCurveStore.ImportStatus.CONFLICT, store.importDocument(doc().copy(name = "Other")).status)
        assertEquals(doc(), store.find(doc().id))
        val copy = store.saveCopy(doc().copy(name = "My curve"))
        assertNotEquals(doc().id, copy.id)
        assertEquals(2, store.list().size)
        assertEquals(2, CustomCurveStore(disk).list().size)
    }
    @Test fun editAndFailedPersistenceAreTransactional() {
        val disk = Store()
        val store = CustomCurveStore(disk)
        store.save(doc())
        disk.fail = true
        rejects { store.save(doc().copy(name = "Changed")) }
        rejects { store.saveCopy(doc()) }
        assertEquals(doc(), store.find(doc().id))
        disk.fail = false
        store.save(doc().copy(name = "Changed"))
        assertEquals("Changed", CustomCurveStore(disk).find(doc().id)!!.name)
    }
    @Test fun decimalInputsUseExactTenthsAndHundredths() {
        assertEquals(90, CurveDraftNumber.parse("9.0", 1))
        assertEquals(3650, CurveDraftNumber.parse("36.50", 2))
        assertEquals(93, CurveDraftNumber.parse("93", 0))
        assertEquals("9", CurveDraftNumber.format(90, 1))
        rejects { CurveDraftNumber.parse("9.01", 1) }
        rejects { CurveDraftNumber.parse("9e1", 1) }
    }
    @Test fun copyingVerifiedMetadataNeverCopiesItsIdentityOrExecutionProof() {
        val library = CurveLibrary(emptyList())
        val original = library.find("capture-1")!!
        val draft = CustomCurveDocument.fromLibraryItem(original)
        assertTrue(draft.id.startsWith("draft-"))
        assertEquals(90, draft.stages.first().target)
        assertEquals(350, draft.stages.first().waterTenthsMl)
        assertTrue(library.canStart(original))
        val flow = CustomCurveDocument.fromLibraryItem(library.find("capture-2")!!)
        assertEquals(CustomCurveDocument.ControlMode.FLOW_RAW, flow.controlMode)
    }
    @Test fun callersCannotMutateSavedStageLists() {
        val store = CustomCurveStore(Store())
        val stages = doc().stages.toMutableList()
        store.save(doc().copy(stages = stages))
        stages.clear()
        assertEquals(2, store.list().single().stages.size)
        rejects { (store.list().single().stages as MutableList).clear() }
    }
    @Test fun damagedStoreRejectsWithoutClearingAndDraftsNeverBecomeExecutable() {
        listOf("", "bad", "{}", "{\"storeVersion\":9,\"documents\":[]}").forEach {
            val disk = Store().apply { value = it }
            rejects { CustomCurveStore(disk) }
            assertEquals(it, disk.value)
        }
        val store = CustomCurveStore(Store())
        val library = CurveLibrary(emptyList(), draftsProvider = { store.items() })
        store.save(doc())
        val item = library.find(doc().id)!!
        assertNull(item.controlProfile); assertNull(item.factoryCurve)
        assertFalse(library.canStart(item)); assertNull(library.resolve(item.id, true))
        store.save(doc().copy(name = "Updated"))
        assertEquals("Updated", library.find(doc().id)!!.name)
    }
}
