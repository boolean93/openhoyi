package io.openhoyi.mobile

import io.openhoyi.protocol.CoffeeCommands
import io.openhoyi.protocol.CustomPressureStartPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class CustomFlowCurveTest {
    private class Disk : CustomCurveStore.Storage {
        var bytes: String? = null
        override fun read() = bytes
        override fun write(value: String) { bytes = value }
    }
    private fun item(document: CustomCurveDocument) =
        CurveLibraryItem(document.id, document.name, "local-draft", "", null, customDocument = document)
    private fun curves() = File("src/main/assets/factory_curves_v3.tsv").inputStream().use(FactoryCurveCatalog::load)
    private fun copy(curve: FactoryCurve) = CustomCurveDocument.fromLibraryItem(
        CurveLibraryItem(curve.id, curve.name, curve.category, "", null, factoryCurve = curve))
    private fun knownFlow(): CustomCurveDocument = copy(curves().first { it.variableFlowLogic && it.weightTenthsGram > 0 })
    private fun rejects(action: () -> Unit) = assertTrue(runCatching(action).isFailure)
    private fun frame(document: CustomCurveDocument, scale: Boolean) =
        CoffeeCommands.start(requireNotNull(CustomCurveAdapter.profile(document, scale)).parameters).frame.hex()

    @Test fun followDocumentRoundtripPreservesModesAndHasSeparateFirmwarePermission() {
        val source=knownFlow()
        val follow=source.copy(controlMode=CustomCurveDocument.ControlMode.PRESSURE,
            stages=listOf(CustomCurveDocument.Stage(30,150),CustomCurveDocument.Stage(90,400)),totalWaterLimitMl=70,
            configuration=source.configuration!!.copy(pressureLogic=true,variableFlowLogic=false,firstSegmentFlowMode=false,
                inactiveStages=listOf(CustomCurveDocument.Stage(47,121),CustomCurveDocument.Stage(29,65001))))
        val restored=CustomCurveDocument.decode(follow.encode())
        assertEquals(follow,restored)
        val disk=Disk();CustomCurveStore(disk).save(restored)
        assertEquals(follow,CustomCurveStore(disk).find(follow.id))
        val parameters=requireNotNull(CustomCurveAdapter.profile(restored,false)).parameters
        assertTrue(parameters.pressureLogic);assertFalse(parameters.variableFlowLogic)
        assertEquals(47,parameters.target3);assertEquals(121,parameters.thirdFlowTenths)
        for(patch in listOf(2,3,4,5,6)) assertEquals(patch in 3..5,
            io.openhoyi.session.CustomCurveFirmwarePolicy.permits(parameters,io.openhoyi.session.CoffeeFirmware(1,1,patch)))
    }

    @Test fun schema3LsAndFirstFlowLibraryCapabilitiesKeepExactFirmwareGate() {
        val source=knownFlow()
        for(pressureLogic in listOf(false,true)) for(firstFlow in listOf(false,true)) {
            val doc=source.copy(stages=listOf(CustomCurveDocument.Stage(if(firstFlow)25 else 30,150),
                CustomCurveDocument.Stage(35,400)),totalWaterLimitMl=70,
                configuration=source.configuration!!.copy(pressureLogic=pressureLogic,firstSegmentFlowMode=firstFlow,
                    inactiveStages=listOf(CustomCurveDocument.Stage(0,0),CustomCurveDocument.Stage(0,0))))
            assertEquals(3,JSONObject(doc.encode()).getInt("schema"))
            val restored=CustomCurveDocument.decode(doc.encode())
            val row=item(restored)
            val library=CurveLibrary(emptyList(),draftsProvider={listOf(row)},enableCustomPressureExecution=true)
            assertTrue(library.canStart(row))
            val parameters=requireNotNull(library.resolve(doc.id,false)).parameters
            assertTrue(library.permitsCustomStart(doc.id,parameters))
            for(patch in listOf(3,4,5,6)) {
                val firmware=io.openhoyi.session.CoffeeFirmware(1,1,patch)
                val uiMayStart=io.openhoyi.session.CoffeeFirmwareProfiles.permitsControl(0x110+patch) &&
                    io.openhoyi.session.CustomCurveFirmwarePolicy.permits(parameters,firmware)
                assertEquals("Ls=${!pressureLogic} firstFlow=$firstFlow patch=$patch",
                    patch in 3..5,uiMayStart)
            }
        }
    }

    @Test fun completeRecipesMatchIndependent126ApkOracleAfterShareAndStorage() {
        fun resource(name: String) = requireNotNull(javaClass.classLoader!!.getResourceAsStream(name)).bufferedReader().use { it.readText() }
        val input = resource("custom-flow-curves.json")
        val exportHash = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val lines = resource("custom-flow-wire.tsv").trimEnd().lines()
        assertTrue(lines.first().contains("apk-sha256=26133c2ac8ecf3d5ee7682bafa6555b1c78836749c937477b05214a88d8ea238"))
        assertTrue(lines.first().contains("source-sha256=4ed90ea99240b7f37373cdb5c9df0bf315103ca7aea6aaab6f37b2c58dd564d7"))
        assertTrue(lines.first().contains("encoder-sha256=5d120498919b7a467a70be6d522e9bb556ec3fc32f686854de8e20b3aa9ae301"))
        assertTrue(lines.first().endsWith("export-sha256=$exportHash"))
        val rows = JSONObject(input).getJSONArray("items")
        assertEquals(6, rows.length()); assertEquals(1 + rows.length() * 6, lines.size)
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val count = row.getInt("seg")
            val all = (1..4).map { CustomCurveDocument.Stage(row.getInt("press$it"), row.getInt("flow$it") * 10) }
            val source = CustomCurveDocument(CustomCurveDocument.newId(), row.getString("name"), row.getInt("temp"), row.getInt("weight") * 10,
                if (row.getBoolean("chart")) CustomCurveDocument.ControlMode.FLOW else CustomCurveDocument.ControlMode.PRESSURE,
                all.take(count), row.getInt("flow"), CustomCurveDocument.Configuration(row.getBoolean("press"), row.getBoolean("chart"),
                    row.getBoolean("seg1FlowMode"), row.getInt("time"), row.getInt("time1"), row.getInt("wdelta") * 10, all.drop(count)))
            val disk = Disk()
            CustomCurveStore(disk).importDocument(CustomCurveDocument.decode(source.encode()))
            val stored = requireNotNull(CustomCurveStore(disk).find(source.id))
            assertEquals(source, stored)
            val expected = lines[1 + index * 6].split('\t')
            assertEquals(index.toString(), expected[0]); assertEquals("7", expected[1])
            for (scale in listOf(false, true)) {
                assertEquals(row.getString("name"), expected[if (scale) 3 else 2], frame(stored, scale))
                assertEquals(if (scale) row.getInt("wdelta") * 10 else 0,
                    requireNotNull(CustomCurveAdapter.profile(stored, scale)).compensationHundredthsGram)
            }
        }
    }

    @Test fun allFactoryCopiesSaveReopenShareImportWithoutChangingOriginalFrames() {
        val factory = curves()
        val proof = File("src/main/assets/factory_wire_v1.tsv").inputStream().use { FactoryWireProof.load(it, factory) }
        assertEquals(100, factory.size)
        for (source in factory) {
            val draft = copy(source)
            assertEquals(3, JSONObject(draft.encode()).getInt("schema"))
            assertEquals(source.preinfusionSeconds, draft.configuration!!.preinfusionSeconds)
            assertEquals(source.firstDurationSeconds, draft.configuration!!.firstDurationSeconds)
            assertEquals(source.pressureLogic, draft.configuration!!.pressureLogic)
            val disk = Disk()
            CustomCurveStore(disk).save(draft)
            val reopened = requireNotNull(CustomCurveStore(disk).find(draft.id))
            assertEquals(draft, reopened)
            // Both text and file sharing transport the same UTF-8 document, not a wire frame.
            val text = reopened.encode()
            val fileText = String(text.toByteArray(Charsets.UTF_8), Charsets.UTF_8)
            assertEquals(reopened, CustomCurveDocument.decode(text))
            val recipientDisk = Disk()
            val recipient = CustomCurveStore(recipientDisk)
            assertEquals(CustomCurveStore.ImportStatus.SAVED,
                recipient.importDocument(CustomCurveDocument.decode(fileText)).status)
            val imported = requireNotNull(CustomCurveStore(recipientDisk).find(draft.id))
            assertEquals(draft, imported)
            for (scale in listOf(false, true)) {
                val original = FactoryCurveAdapter.profile(source, scale)
                val actual = requireNotNull(CustomCurveAdapter.profile(imported, scale))
                assertEquals(original.parameters, actual.parameters)
                assertEquals(original.targetHundredthsGram, actual.targetHundredthsGram)
                assertEquals(original.compensationHundredthsGram, actual.compensationHundredthsGram)
                assertEquals(proof.expected(source.id, scale), frame(imported, scale))
            }
        }
    }

    @Test fun firstFlowAndIndependentLogicTimingCompensationAndInactiveSlotsSurvive() {
        val original = knownFlow()
        val document = original.copy(stages = listOf(CustomCurveDocument.Stage(25, 150), CustomCurveDocument.Stage(35, 400)),
            totalWaterLimitMl = 70, targetHundredthsGram = 3400,
            configuration = original.configuration!!.copy(pressureLogic = false, firstSegmentFlowMode = true,
                preinfusionSeconds = 6, firstDurationSeconds = 7, compensationHundredthsGram = -30,
                inactiveStages = listOf(CustomCurveDocument.Stage(47, 121), CustomCurveDocument.Stage(29, 65001))))
        val disk = Disk()
        CustomCurveStore(disk).save(CustomCurveDocument.decode(document.encode()))
        val restored = requireNotNull(CustomCurveStore(disk).find(document.id))
        assertEquals(document, restored)
        val preview = CurveStageParameters.preview(restored)
        assertTrue(preview.renderable); assertTrue(preview.hasFlow); assertFalse(preview.mixed)
        assertTrue(preview.stages.all { it.unit == CurveStageParameters.Unit.FLOW_ML_PER_SECOND })
        val mixed = restored.copy(configuration = restored.configuration!!.copy(firstSegmentFlowMode = false))
        assertTrue(CurveStageParameters.preview(mixed).mixed)
        assertEquals(CurveStageParameters.Unit.PRESSURE_BAR, CurveStageParameters.preview(mixed).stages[0].unit)
        assertEquals(CurveStageParameters.preview(mixed), CurveStageParameters.preview(mixed.copy(
            configuration = mixed.configuration!!.copy(pressureLogic = true))))
        val noScale = requireNotNull(CustomCurveAdapter.profile(restored, false))
        val scale = requireNotNull(CustomCurveAdapter.profile(restored, true))
        assertEquals(70, noScale.maximumWaterMl); assertEquals(136, scale.maximumWaterMl)
        assertEquals(0, noScale.compensationHundredthsGram); assertEquals(-30, scale.compensationHundredthsGram)
        with(scale.parameters) {
            assertFalse(pressureLogic); assertTrue(variableFlowLogic); assertTrue(firstSegmentFlowMode)
            assertEquals(6, preinfusionSeconds); assertEquals(7, firstDurationSeconds)
            assertEquals(47, target3); assertEquals(29, target4)
            assertEquals(121, thirdFlowTenths); assertEquals(65001, fourthFlowTenths)
        }
        val fromCompleteProfile = CustomCurveDocument.fromLibraryItem(
            CurveLibraryItem(scale.id, scale.name, "", "", scale))
        assertEquals(restored.configuration, fromCompleteProfile.configuration)
        assertEquals(restored.allStages, fromCompleteProfile.allStages)
        assertEquals(scale.parameters, requireNotNull(CustomCurveAdapter.profile(fromCompleteProfile, true)).parameters)
    }

    @Test fun legacyPressureSchemasRemainCompatibleAndUnknownRawNeverGainsModes() {
        val old = CustomCurveDocument(CustomCurveDocument.newId(), "Legacy", 93, 3600,
            CustomCurveDocument.ControlMode.PRESSURE,
            listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
        for (document in listOf(old, old.copy(totalWaterLimitMl = 40))) {
            val decoded = CustomCurveDocument.decode(document.encode())
            assertNull(decoded.configuration)
            assertEquals(if (document.totalWaterLimitMl == null) 1 else 2, JSONObject(document.encode()).getInt("schema"))
            assertEquals(CustomPressureCurveAdapter.profile(document, true), CustomCurveAdapter.profile(decoded, true))
            assertTrue(CustomPressureStartPolicy.permits(requireNotNull(CustomCurveAdapter.profile(decoded, true)).parameters))
            val raw = CustomCurveDocument.decode(document.copy(controlMode = CustomCurveDocument.ControlMode.FLOW_RAW).encode())
            assertFalse(raw.isEditable); assertNull(raw.configuration); assertNull(CustomCurveAdapter.profile(raw, false))
            assertFalse(CurveStageParameters.preview(raw).renderable)
            assertTrue(CurveStageParameters.preview(raw).stages.all { it.unit == CurveStageParameters.Unit.RAW_UNKNOWN })
        }
    }

    @Test fun configuredJsonRejectsMissingMistypedContradictoryAndExtraFields() {
        val source = knownFlow().encode()
        fun changed(edit: (JSONObject) -> Unit) { rejects { CustomCurveDocument.decode(JSONObject(source).also(edit).toString()) } }
        changed { it.put("schema", 2) }
        changed { it.put("controlMode", "flow-raw") }
        changed { it.put("controlMode", "pressure") }
        changed { it.put("extra", 1) }
        changed { it.getJSONObject("configuration").remove("pressureLogic") }
        changed { it.getJSONObject("configuration").put("pressureLogic", "true") }
        changed { it.getJSONObject("configuration").put("preinfusionSeconds", 1.5) }
        changed { it.getJSONObject("configuration").put("extra", 1) }
        changed { it.getJSONObject("configuration").getJSONArray("inactiveStages").put(JSONObject().put("target", 0).put("waterTenthsMl", 0)) }
        changed { it.getJSONObject("configuration").put("variableFlowLogic", false).put("firstSegmentFlowMode", true) }
    }

    @Test fun storedConfigurationIsFrozenAndEveryConfigurationParameterChangesIdentity() {
        val original = knownFlow()
        val tails = original.configuration!!.inactiveStages.toMutableList()
        val doc = original.copy(configuration = original.configuration!!.copy(inactiveStages = tails))
        val store = CustomCurveStore(Disk()); store.save(doc)
        tails.clear()
        assertEquals(4 - doc.stages.size, store.find(doc.id)!!.configuration!!.inactiveStages.size)
        rejects { (store.find(doc.id)!!.configuration!!.inactiveStages as MutableList).clear() }
        val stable = store.find(doc.id)!!
        val c = stable.configuration!!
        val variants = listOf(c.copy(pressureLogic = !c.pressureLogic), c.copy(firstSegmentFlowMode = true),
            c.copy(preinfusionSeconds = c.preinfusionSeconds + 1), c.copy(firstDurationSeconds = c.firstDurationSeconds + 1),
            c.copy(compensationHundredthsGram = 10), c.copy(inactiveStages = c.inactiveStages.map { it.copy(target = 1) }))
        for (variant in variants) assertNotEquals(CustomCurveFingerprint.of(stable), CustomCurveFingerprint.of(stable.copy(configuration = variant)))
        assertEquals(CustomCurveFingerprint.of(stable), CustomCurveFingerprint.of(stable.copy(name = "Renamed")))
    }

    @Test fun configuredDispatchMatchesCurrentSavedRecipeAndRevokesEditedOrDeletedProfiles() {
        val original = knownFlow()
        var current = listOf(item(original))
        val library = CurveLibrary(emptyList(), draftsProvider = { current }, enableCustomPressureExecution = true)
        val before = requireNotNull(library.resolve(original.id, false))
        assertTrue(library.canStart(current.single()))
        assertTrue(library.permitsCustomStart(original.id, before.parameters))
        assertFalse(library.permitsCustomStart(null, before.parameters))
        assertFalse(library.permitsCustomPressureStart(original.id, before.parameters))
        assertFalse(library.permitsCustomStart(original.id, before.parameters.copy(target2 = before.parameters.target2 + 1)))
        assertFalse(library.permitsCustomStart(original.id, before.parameters.copy(slot = 1)))
        current = listOf(item(original.copy(configuration = original.configuration!!.copy(firstDurationSeconds = original.configuration!!.firstDurationSeconds + 1))))
        assertFalse(library.validated(before)); assertFalse(library.permitsCustomStart(original.id, before.parameters))
        assertTrue(library.permitsCustomStart(original.id, requireNotNull(library.resolve(original.id, false)).parameters))
        current = emptyList()
        assertFalse(library.validated(before)); assertFalse(library.permitsCustomStart(original.id, before.parameters))
    }
}
