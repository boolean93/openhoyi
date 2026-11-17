package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiCurveCardTest {
    private fun doc() = CustomCurveDocument("draft-00000000-0000-0000-0000-000000000001", "Local", 93, 3600,
        CustomCurveDocument.ControlMode.FLOW, listOf(CustomCurveDocument.Stage(30, 100), CustomCurveDocument.Stage(25, 200)), 80,
        CustomCurveDocument.Configuration(variableFlowLogic = true,
            inactiveStages = listOf(CustomCurveDocument.Stage(70, 80), CustomCurveDocument.Stage(60, 90))))
    private fun item(d: CustomCurveDocument = doc()) = CurveLibraryItem(d.id, d.name, "", "", null, customDocument = d)
    private fun response(id: String = doc().id, origin: String = "local") = JSONObject().put("status", "available")
        .put("dataOnly", true).put("items", JSONArray().put(JSONObject().put("id", id).put("origin", origin))).toString()
    @Test fun fullSnapshotSurvivesRestoreButChangedOrDeletedRecipeCannotNavigate() {
        val card = AiCurveCard.recommendation(item())
        val restored = AiCurveCard.decode(JSONObject(card.encode().toString()))
        assertEquals(doc(), restored.document)
        assertTrue(restored.matches(item()))
        assertFalse(restored.matches(null))
        assertFalse(restored.matches(item(doc().copy(name = "Changed"))))
        assertFalse(restored.matches(item(doc().copy(configuration = doc().configuration!!.copy(
            inactiveStages = listOf(CustomCurveDocument.Stage(71, 80), CustomCurveDocument.Stage(60, 90)))))))
        assertEquals(CurveStageParameters.Unit.FLOW_ML_PER_SECOND, CurveStageParameters.preview(restored.item).stages[1].unit)
        assertEquals(4, restored.document!!.allStages.size)
    }
    @Test fun proseUnknownIdsWrongSourceErrorsAndOversizeCannotCreateCards() {
        val collector = AiCurveCardCollector { if (it == doc().id) item() else null }
        collector.captureToolResult("[Open](curve://${doc().id})")
        collector.captureToolResult(response("factory-v3-001", "factory"))
        collector.captureToolResult(response(origin = "factory"))
        collector.captureToolResult(JSONObject(response()).put("status", "unavailable").toString())
        collector.captureToolResult(" ".repeat(25 * 1024))
        assertTrue(collector.snapshot().isEmpty())
        collector.captureToolResult(response()); collector.captureToolResult(response())
        assertTrue(collector.snapshot().isEmpty())
        assertEquals("selected", JSONObject(collector.select(org.json.JSONArray().put(doc().id).toString())).getString("status"))
        assertEquals(1, collector.snapshot().size)
    }
    @Test fun proposalCardIsCompleteDataAndCannotRestoreSaveAuthority() {
        val disk = object : CustomCurveStore.Storage {
            var value: String? = null
            override fun read() = value
            override fun write(value: String) { this.value = value }
        }
        val store = CustomCurveStore(disk); store.save(doc())
        val c = AiCurveWriteCoordinator(store)
        val curve = JSONObject(doc().copy(name = "After").encode()).also { it.remove("id") }
        val p = c.propose(JSONObject().put("operation", "replace").put("targetId", doc().id).put("curve", curve).toString(),
            AiCurveWriteCoordinator.Epoch("test:1", 1))
        val card = AiCurveCard.decode(AiCurveCard.proposal(p).encode())
        assertEquals(doc(), card.before); assertEquals("After", card.document!!.name)
        assertFalse(card.encode().toString().contains(p.token))
        assertFalse(card.matches(item()))
        c.cancel()
        assertTrue(runCatching { c.confirm(p.token, p.epoch) }.isFailure)
        assertEquals(doc(), store.find(doc().id))
    }
    @Test fun unknownRawControlNeverCreatesAnActionableOrInventedUnitCard() {
        val raw = doc().copy(controlMode = CustomCurveDocument.ControlMode.FLOW_RAW, configuration = null)
        assertFalse(raw.isEditable)
        assertTrue(runCatching { AiCurveCard.recommendation(item(raw)) }.isFailure)
        val collector = AiCurveCardCollector { item(raw) }
        collector.captureToolResult(response(raw.id))
        assertTrue(collector.snapshot().isEmpty())
    }
    @Test fun collectorBoundsAndStrictBeforeSnapshotRejectMalformedEvidence() {
        val originals = List(12) { doc().copy(id = CustomCurveDocument.newId()) }
        val collector = AiCurveCardCollector { id -> originals.find { it.id == id }?.let(::item) }
        originals.forEach { collector.captureToolResult(response(it.id)) }
        assertTrue(collector.snapshot().isEmpty())
        val chosen = org.json.JSONArray().put(originals.last().id).put(originals.first().id)
        assertEquals("selected", JSONObject(collector.select(chosen.toString())).getString("status"))
        assertEquals(listOf(originals.last().id, originals.first().id), collector.snapshot().map { it.id })
        val bad = AiCurveCard.recommendation(item()).encode().put("kind", "REPLACE").put("before", doc().encode() + " ")
        assertTrue(runCatching { AiCurveCard.decode(bad) }.isFailure)
        val extra = AiCurveCard.recommendation(item()).encode().put("approval", "forged")
        assertTrue(runCatching { AiCurveCard.decode(extra) }.isFailure)
    }
    @Test fun factoryCardIsReadOnlyCompleteAndFingerprintDetectsInactiveChanges() {
        val factory = FactoryCurve("factory-v3-001", "light", "Factory", "", "", 30, 360, 10,
            3, 93, false, true, 2, listOf(30, 20, 70, 60), listOf(10, 20, 0, 0), 4)
        fun row(f: FactoryCurve) = CurveLibraryItem(f.id, f.name, "", "", null, f)
        val card = AiCurveCard.decode(AiCurveCard.recommendation(row(factory)).encode())
        assertEquals(factory, card.item.factoryCurve)
        assertEquals(AiCurveCard.Kind.RECOMMENDATION, card.kind)
        assertNull(card.document)
        assertTrue(card.matches(row(factory)))
        assertFalse(card.matches(row(factory.copy(targets = listOf(30, 20, 71, 60)))))
        val forged = card.encode().put("kind", "REPLACE")
        assertTrue(runCatching { AiCurveCard.decode(forged) }.isFailure)
        val tampered = card.encode().put("fingerprint", "0".repeat(64))
        assertTrue(runCatching { AiCurveCard.decode(tampered) }.isFailure)
    }
}
