package io.openhoyi.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiCurveWriteCoordinatorTest {
    private class Disk : CustomCurveStore.Storage {
        var value: String? = null
        var fail = false
        override fun read() = value
        override fun write(value: String) { check(!fail); this.value = value }
    }
    private val epoch = AiCurveWriteCoordinator.Epoch("session", 1)
    private fun doc() = CustomCurveDocument("draft-00000000-0000-0000-0000-000000000001", "Local", 93, 3600,
        CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(90, 400), CustomCurveDocument.Stage(60, 100)))
    private fun args(document: CustomCurveDocument = doc(), target: String? = null): String {
        val curve = JSONObject(document.encode()).also { it.remove("id") }
        return JSONObject().put("operation", if (target == null) "create" else "replace").put("curve", curve)
            .also { if (target != null) it.put("targetId", target) }.toString()
    }
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun proposalsNeverWriteAndNativeConfirmationIsSingleUse() {
        val disk = Disk(); val store = CustomCurveStore(disk); val coordinator = AiCurveWriteCoordinator(store)
        val p = coordinator.propose(args(), epoch)
        assertNull(disk.value); assertTrue(store.list().isEmpty()); assertNotEquals(doc().id, p.after.id)
        rejects { coordinator.propose(args(), epoch) }
        val saved = coordinator.confirm(p.token, epoch)
        assertEquals(saved, CustomCurveStore(disk).find(saved.id))
        rejects { coordinator.confirm(p.token, epoch) }
    }
    @Test fun cancelWrongTokenEpochAndRestoreCannotWrite() {
        val disk = Disk(); val store = CustomCurveStore(disk); val c = AiCurveWriteCoordinator(store)
        val p = c.propose(args(), epoch); c.cancel(); rejects { c.confirm(p.token, epoch) }
        val q = c.propose(args(), epoch); rejects { c.confirm("forged", epoch) }; rejects { c.confirm(q.token, epoch) }
        val r = c.propose(args(), epoch); rejects { c.confirm(r.token, epoch.copy(credential = 2)) }
        rejects { AiCurveWriteCoordinator(store).confirm(r.token, epoch) }; assertNull(disk.value)
    }
    @Test fun replacementUsesTrustedTargetAndRejectsABAAndDeletion() {
        val disk = Disk(); val store = CustomCurveStore(disk); store.save(doc()); val c = AiCurveWriteCoordinator(store)
        val p = c.propose(args(doc().copy(name = "New"), doc().id), epoch)
        store.save(doc().copy(name = "Temporary")); store.save(doc())
        rejects { c.confirm(p.token, epoch) }; assertEquals(doc(), store.find(doc().id))
        val q = c.propose(args(doc().copy(name = "New"), doc().id), epoch)
        store.delete(doc().id, emptyMap()); rejects { c.confirm(q.token, epoch) }
    }
    @Test fun successfulReplacePreservesIdAndOtherDocuments() {
        val store = CustomCurveStore(Disk()); store.save(doc())
        val sibling = doc().copy(id = CustomCurveDocument.newId()); store.save(sibling)
        val c = AiCurveWriteCoordinator(store); val p = c.propose(args(doc().copy(name = "New"), doc().id), epoch)
        assertEquals(doc(), p.before); assertEquals(doc(), store.find(doc().id))
        assertEquals(doc().id, c.confirm(p.token, epoch).id); assertEquals(sibling, store.find(sibling.id))
    }
    @Test fun schemaFactoryRawSpoofAndInvalidParametersAreRejected() {
        val store = CustomCurveStore(Disk()); val c = AiCurveWriteCoordinator(store)
        rejects { c.propose(args(target = "factory-001"), epoch) }
        rejects { c.propose(JSONObject(args()).put("approved", true).toString(), epoch) }
        rejects { c.propose(JSONObject(args()).also { it.getJSONObject("curve").put("id", doc().id) }.toString(), epoch) }
        rejects { c.propose(args(doc().copy(controlMode = CustomCurveDocument.ControlMode.FLOW_RAW)), epoch) }
        rejects { c.propose(JSONObject(args()).also { it.getJSONObject("curve").put("temperatureC", 999) }.toString(), epoch) }
        rejects { c.propose(args(doc().copy(name = "spoof\u202E")), epoch) }
        rejects { c.propose(args(doc().copy(name = "spoof\u2028field")), epoch) }
        rejects { c.propose(" ".repeat(8193), epoch) }
        assertTrue(store.list().isEmpty())
    }
    @Test fun failedDiskWriteConsumesApprovalWithoutChangingStore() {
        val disk = Disk(); val store = CustomCurveStore(disk); val c = AiCurveWriteCoordinator(store)
        val p = c.propose(args(), epoch); disk.fail = true
        rejects { c.confirm(p.token, epoch) }; disk.fail = false
        rejects { c.confirm(p.token, epoch) }; assertTrue(store.list().isEmpty()); assertNull(disk.value)
    }
    @Test fun inUseReplacementAndPrecisionAreRejected() {
        val store = CustomCurveStore(Disk()); store.save(doc())
        val c = AiCurveWriteCoordinator(store) { false }
        val p = c.propose(args(doc().copy(name = "New"), doc().id), epoch)
        rejects { c.confirm(p.token, epoch) }; assertEquals(doc(), store.find(doc().id))
        rejects { c.propose(args(doc().copy(targetHundredthsGram = 3601)), epoch) }
        rejects { c.propose(args(doc().copy(stages = listOf(CustomCurveDocument.Stage(90, 401)))), epoch) }
    }
    @Test fun directConfirmedInsertCannotOverwriteCollision() {
        val store = CustomCurveStore(Disk()); store.save(doc())
        rejects { store.saveAiConfirmed(doc().copy(name = "Spoof"), null, store.revision) }
        assertEquals(doc(), store.find(doc().id))
    }
    @Test fun legacySingleStageZeroPressureAndLargeWaterRemainValid() {
        val c = AiCurveWriteCoordinator(CustomCurveStore(Disk()))
        val legacy = doc().copy(stages = listOf(CustomCurveDocument.Stage(0, 12000)), totalWaterLimitMl = 1200)
        val p = c.propose(args(legacy), epoch)
        assertEquals(legacy.stages, p.after.stages)
        assertEquals(1200, c.confirm(p.token, epoch).totalWaterLimitMl)
    }
    @Test fun mixedPresentationPreservesUnitsInactiveAndNegativeCompensation() {
        val mixed = doc().copy(controlMode = CustomCurveDocument.ControlMode.FLOW,
            stages = listOf(CustomCurveDocument.Stage(90, 100), CustomCurveDocument.Stage(25, 200)),
            configuration = CustomCurveDocument.Configuration(variableFlowLogic = true,
                compensationHundredthsGram = -50, inactiveStages = listOf(CustomCurveDocument.Stage(30, 70), CustomCurveDocument.Stage(40, 80))))
        val text = AiCurveWritePresentation.recipe(mixed, true)
        assertTrue(text.contains("9 bar; 10 ml")); assertTrue(text.contains("2.5 ml/s; 20 ml"))
        assertTrue(text.contains("Stage 3 (inactive): 3 ml/s; 7 ml")); assertTrue(text.contains("-0.5 g"))
        assertTrue(AiCurveWritePresentation.recipe(doc().copy(name = "old\u2028field"), true).contains("\\u2028"))
    }
    @Test fun presentationShowsAllFieldsAndUnitsIncludingInactiveStages() {
        val text = AiCurveWritePresentation.recipe(doc(), true)
        for (field in listOf("ID", "93 °C", "36 g", "9 bar", "40 ml", "Pressure logic", "Preinfusion", "Weight compensation", "Stage 4 (inactive)")) assertTrue(field, text.contains(field))
    }
}
