package io.openhoyi.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiCurveDetailTest {
    private class Disk : CustomCurveStore.Storage {
        var value: String? = null
        override fun read() = value
        override fun write(value: String) { this.value = value }
    }
    private val original = CustomCurveDocument(CustomCurveDocument.newId(), "Complete", 93, 3600,
        CustomCurveDocument.ControlMode.FLOW,
        listOf(CustomCurveDocument.Stage(30, 100), CustomCurveDocument.Stage(25, 200)), 80,
        CustomCurveDocument.Configuration(pressureLogic = true, variableFlowLogic = true,
            preinfusionSeconds = 3, firstDurationSeconds = 4, compensationHundredthsGram = -50,
            inactiveStages = listOf(CustomCurveDocument.Stage(70, 83), CustomCurveDocument.Stage(60, 97))))
    @Test fun fullDetailRetainsHiddenSlotsAndNameOnlyReplacementDoesNotResetThem() {
        val store = CustomCurveStore(Disk()); store.save(original)
        val coordinator = AiCurveWriteCoordinator(store)
        val detail = JSONObject(AiCurveDetail.result(original.id, coordinator.readDocument(original.id)))
        assertEquals("available", detail.getString("status"))
        val curve = detail.getJSONObject("document"); assertEquals(3, curve.getInt("schema"))
        assertEquals(83, curve.getJSONObject("configuration").getJSONArray("inactiveStages").getJSONObject(0).getInt("waterTenthsMl"))
        curve.remove("id"); curve.put("name", "Renamed")
        val proposal = JSONObject().put("operation", "replace").put("targetId", original.id).put("curve", curve)
        val epoch = AiCurveWriteCoordinator.Epoch("session", 1)
        val p = coordinator.propose(proposal.toString(), epoch)
        assertEquals(original.copy(name = "Renamed"), coordinator.confirm(p.token, epoch))
    }
    @Test fun factoryReadOnlyTemplatePreservesAllStagesAndNeverUsesFactoryIdAsSaveTarget() {
        val factory = FactoryCurve("factory-v3-001", "light", "Factory", "", "", 30, 360, 10,
            3, 93, false, true, 2, listOf(30, 25, 70, 60), listOf(10, 20, 0, 0), 4)
        val item = CurveLibraryItem(factory.id, factory.name, "", "", null, factory)
        val result = JSONObject(AiCurveDetail.factoryResult(factory.id, item))
        assertEquals("available", result.getString("status")); assertTrue(result.getBoolean("readOnly"))
        val template = result.getJSONObject("copyTemplate")
        assertFalse(template.has("id")); assertEquals(3, template.getInt("schema"))
        val inactive = template.getJSONObject("configuration").getJSONArray("inactiveStages")
        assertEquals(2, inactive.length()); assertEquals(70, inactive.getJSONObject(0).getInt("target"))
        assertTrue(AiCurveDetail.validFactoryId("factory-v3-100")); assertFalse(AiCurveDetail.validFactoryId("factory-v3-101"))
        assertEquals("unavailable", JSONObject(AiCurveDetail.factoryResult("factory-v3-002", item)).getString("status"))
    }
    @Test fun detailRejectsFactoryRawMissingAndMismatchedTargetsAndUsesLiveSingleton() {
        val store = CustomCurveStore(Disk()); store.save(original); val c = AiCurveWriteCoordinator(store)
        assertEquals("invalid_arguments", JSONObject(AiCurveDetail.result("factory-001", original)).getString("status"))
        assertEquals("unavailable", JSONObject(AiCurveDetail.result(CustomCurveDocument.newId(), original)).getString("status"))
        store.save(original.copy(name = "Latest")); assertEquals("Latest", c.readDocument(original.id)?.name)
        store.delete(original.id, emptyMap()); assertNull(c.readDocument(original.id))
        val raw = original.copy(id = CustomCurveDocument.newId(), controlMode = CustomCurveDocument.ControlMode.FLOW_RAW, configuration = null)
        store.save(raw); assertNull(c.readDocument(raw.id))
    }
}
