package io.openhoyi.mobile

import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** File-level compatibility against the real pre-deletion implementation, without APK/device writes. */
class CurveStoreUpgradeCompatibilityTest {
    private class Disk(val path: Path) : CustomCurveStore.Storage, CurveStoreV1Fixture.Storage {
        private val atomic = AtomicDocumentStorage(path)
        var writes = 0
            private set
        override fun read() = atomic.read()?.toString(Charsets.UTF_8)
        override fun write(value: String) { atomic.write(value.toByteArray(Charsets.UTF_8)); writes++ }
        fun version() = JSONObject(requireNotNull(read())).getInt("storeVersion")
    }
    private fun doc() = CustomCurveDocument("draft-00000000-0000-4000-8000-000000000001", "Existing curve", 92, 3600,
        CustomCurveDocument.ControlMode.PRESSURE,
        listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
    private fun library(store: CustomCurveStore) = CurveLibrary(emptyList(), draftsProvider = store::items,
        enableCustomPressureExecution = true, presetBinding = store::presetBinding)
    private fun withDisk(checks: (Disk) -> Unit) {
        val folder = Files.createTempDirectory("hoyi-curve-upgrade")
        try { checks(Disk(folder.resolve("custom_curves_v1.json"))) }
        finally { folder.toFile().deleteRecursively() }
    }

    @Test fun oldFileLoadsWithoutRewriteAndOrdinarySaveRetainsVersionOneCompatibility() = withDisk { disk ->
        val old = CurveStoreV1Fixture(disk)
        val editable = doc()
        val raw = editable.copy(id = "draft-00000000-0000-4000-8000-000000000002",
            name = "Imported raw", controlMode = CustomCurveDocument.ControlMode.FLOW_RAW)
        old.save(editable); old.save(raw)
        val before = Files.readAllBytes(disk.path); val writes = disk.writes
        val upgraded = CustomCurveStore(disk)
        assertEquals(listOf(editable, raw), upgraded.list())
        assertEquals(writes, disk.writes); assertArrayEquals(before, Files.readAllBytes(disk.path))
        val edited = editable.copy(name = "Edited after upgrade")
        upgraded.save(edited, editable)
        assertEquals(1, disk.version())
        assertEquals(listOf(edited, raw), CurveStoreV1Fixture(disk).list())
    }

    @Test fun persistedArchiveSurvivesRestartAndOldReaderRejectsWithoutRewritingIt() = withDisk { disk ->
        CurveStoreV1Fixture(disk).save(doc())
        val current = CustomCurveStore(disk)
        current.delete(doc().id, mapOf(2 to doc().id))
        assertEquals(2, disk.version())
        val before = Files.readAllBytes(disk.path); val writes = disk.writes
        val reopened = CustomCurveStore(disk)
        assertTrue(reopened.items().isEmpty()); assertEquals(doc().name, reopened.historicalName(doc().id))
        assertEquals(PresetSlots.EMPTY, PresetSlots.curveId(2, doc().id, library(reopened)))
        val failure = runCatching { CurveStoreV1Fixture(disk) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("original file retained"))
        assertEquals(writes, disk.writes); assertArrayEquals(before, Files.readAllBytes(disk.path))
        // A failed old-reader attempt must not prevent reopening with a v2-capable build.
        assertEquals(doc().name, CustomCurveStore(disk).historicalName(doc().id))
    }

    @Test fun restoringDocumentsByImportIsNotADowngradeMigrationBecauseEmptySlotMetadataRemains() = withDisk { disk ->
        CurveStoreV1Fixture(disk).save(doc())
        val current = CustomCurveStore(disk)
        val deletion = current.delete(doc().id, mapOf(2 to doc().id))
        assertEquals(CustomCurveStore.ImportStatus.SAVED, current.importDocument(doc()).status)
        assertEquals(doc(), current.find(doc().id))
        assertEquals(PresetSlots.EMPTY, PresetSlots.curveId(2, doc().id, library(current)))
        assertFalse(current.undo(deletion))
        assertEquals(2, disk.version())
        val before = Files.readAllBytes(disk.path)
        assertTrue(runCatching { CurveStoreV1Fixture(disk) }.isFailure)
        assertArrayEquals(before, Files.readAllBytes(disk.path))
    }
}
