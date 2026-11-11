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
    @Test fun explicitLimitUsesStrictVersionTwoAndLegacyDocumentsKeepVersionOne() {
        val legacy = doc()
        val explicit = legacy.copy(totalWaterLimitMl = 70)
        val oldJson = org.json.JSONObject(legacy.encode())
        val newJson = org.json.JSONObject(explicit.encode())
        assertEquals(1, oldJson.getInt("schema")); assertFalse(oldJson.has("totalWaterLimitMl"))
        assertEquals(2, newJson.getInt("schema")); assertEquals(70, newJson.getInt("totalWaterLimitMl"))
        assertEquals(explicit, CustomCurveDocument.decode(newJson.toString()))
        rejects { CustomCurveDocument.decode(newJson.put("schema", 1).toString()) }
        rejects { CustomCurveDocument.decode(oldJson.put("schema", 2).toString()) }
        for (value in listOf<Any>(org.json.JSONObject.NULL, "70", 70.5, -1, 0, 801)) {
            rejects { CustomCurveDocument.decode(org.json.JSONObject(explicit.encode()).put("totalWaterLimitMl", value).toString()) }
        }
        rejects { CustomCurveDocument.decode(org.json.JSONObject(explicit.encode()).put("unknown", 1).toString()) }
        assertNull(CustomCurveDocument.decode(legacy.encode()).totalWaterLimitMl)
    }
    @Test fun independentLimitBoundsKeepStageAllocationUnchanged() {
        val original = doc()
        assertEquals(original.stages, original.copy(totalWaterLimitMl = 55).also { it.validate() }.stages)
        original.copy(totalWaterLimitMl = 800).validate()
        val fractional = original.copy(stages = listOf(CustomCurveDocument.Stage(90, 551)))
        assertEquals(800, fractional.maximumSupportedWaterLimitMl)
        fractional.copy(totalWaterLimitMl = 1).validate()
        fractional.copy(totalWaterLimitMl = 56).validate()
        rejects { original.copy(totalWaterLimitMl = 0).validate() }
        val large = original.copy(stages = listOf(CustomCurveDocument.Stage(90, 9000)))
        large.copy(totalWaterLimitMl = 900).validate()
        large.copy(totalWaterLimitMl = 40).validate()
        rejects { large.copy(totalWaterLimitMl = 901).validate() }
    }
    @Test fun mixedStoreKeepsOldDocumentsAndPersistsEditedLimitAcrossReopenAndCopy() {
        val disk = Store(); val store = CustomCurveStore(disk)
        store.save(doc())
        val oldBytes = disk.value
        val reopened = CustomCurveStore(disk)
        assertEquals(oldBytes, disk.value)
        val changed = doc().copy(totalWaterLimitMl = 70)
        reopened.save(changed)
        assertEquals(changed, CustomCurveStore(disk).find(changed.id))
        val copy = reopened.saveCopy(changed)
        assertEquals(70, copy.totalWaterLimitMl); assertEquals(changed.stages, copy.stages)
        assertEquals(copy, CustomCurveStore(disk).find(copy.id))
        val legacySibling = doc("draft-00000000-0000-0000-0000-000000000002")
        reopened.save(legacySibling)
        val mixed = CustomCurveStore(disk)
        assertEquals(legacySibling, mixed.find(legacySibling.id))
        assertEquals(changed, mixed.find(changed.id))
        assertEquals(copy, mixed.find(copy.id))
        assertEquals(CustomCurveStore.ImportStatus.ALREADY_EXISTS, reopened.importDocument(changed).status)
        assertEquals(CustomCurveStore.ImportStatus.CONFLICT, reopened.importDocument(changed.copy(totalWaterLimitMl = 71)).status)
        reopened.save(changed.copy(totalWaterLimitMl = null))
        assertNull(CustomCurveStore(disk).find(changed.id)!!.totalWaterLimitMl)
    }
    @Test fun sharedTextAndFileImportIntoFreshRecipientPreserveExplicitAndLegacyRecipes() {
        val explicit = doc().copy(name = "共享曲线", totalWaterLimitMl = 40)
        for (source in listOf(explicit, explicit.copy(totalWaterLimitMl = null))) {
            val text = source.encode()
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val file = java.nio.file.Files.createTempFile("curve-recipient-", ".json")
            try {
                java.nio.file.Files.write(file, textBytes)
                val fileBytes = java.nio.file.Files.readAllBytes(file)
                assertArrayEquals(textBytes, fileBytes)
                assertEquals(text, String(fileBytes, Charsets.UTF_8))
                val fromText = CustomCurveDocument.decode(text)
                val fromFile = CustomCurveDocument.decode(String(fileBytes, Charsets.UTF_8))
                assertEquals(source, fromText); assertEquals(source, fromFile)
                val recipientDisk = Store()
                val recipient = CustomCurveStore(recipientDisk)
                assertTrue(recipient.list().isEmpty())
                assertEquals(CustomCurveStore.ImportStatus.SAVED, recipient.importDocument(fromFile).status)
                val reopened = requireNotNull(CustomCurveStore(recipientDisk).find(source.id))
                assertEquals(source, reopened)
                assertEquals(source.totalWaterLimitMl, reopened.totalWaterLimitMl)
                assertEquals(listOf(150, 400), reopened.stages.map { it.waterTenthsMl })
                val json = org.json.JSONObject(text)
                assertEquals(if (source.totalWaterLimitMl == null) 1 else 2, json.getInt("schema"))
                assertEquals(source.totalWaterLimitMl != null, json.has("totalWaterLimitMl"))
            } finally { java.nio.file.Files.deleteIfExists(file) }
        }
    }
    @Test fun legacyFingerprintIsStableAndExplicitLimitChangesRecipeIdentity() {
        val old = doc()
        assertEquals("d58f8b3be5be03843b32c65f88367dcdde57a40bdb077ce785450a1a3ff2aebf", CustomCurveFingerprint.of(old))
        val explicit = old.copy(totalWaterLimitMl = 55)
        assertNotEquals(CustomCurveFingerprint.of(old), CustomCurveFingerprint.of(explicit))
        assertNotEquals(CustomCurveFingerprint.of(explicit), CustomCurveFingerprint.of(explicit.copy(totalWaterLimitMl = 70)))
        assertEquals(CustomCurveFingerprint.of(explicit), CustomCurveFingerprint.of(explicit.copy(name = "renamed")))
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
        val profile = CurveCatalog.profiles[0]
        val original = CurveLibraryItem(profile.id, profile.name, "", "", profile)
        val draft = CustomCurveDocument.fromLibraryItem(original)
        assertTrue(draft.id.startsWith("draft-"))
        assertEquals(90, draft.stages.first().target)
        assertEquals(350, draft.stages.first().waterTenthsMl)
        assertEquals(profile.parameters.maximumWaterMl, draft.totalWaterLimitMl)
        assertFalse(library.canStart(original))
        val flowProfile = CurveCatalog.profiles[1]
        val flow = CustomCurveDocument.fromLibraryItem(
            CurveLibraryItem(flowProfile.id, flowProfile.name, "", "", flowProfile))
        assertEquals(CustomCurveDocument.ControlMode.FLOW, flow.controlMode)
        assertNotNull(flow.configuration)
        assertEquals(flowProfile.parameters.preinfusionSeconds, flow.configuration!!.preinfusionSeconds)
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
