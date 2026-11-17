package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiCurveCardSelectionTest {
    private fun document(index: Int) = CustomCurveDocument("draft-00000000-0000-0000-0000-${index.toString().padStart(12, '0')}", "Synthetic $index", 93, 3600,
        CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(90, 400), CustomCurveDocument.Stage(60, 100)))
    private fun item(d: CustomCurveDocument) = CurveLibraryItem(d.id, d.name, "", "", null, customDocument = d)
    private fun result(documents: List<CustomCurveDocument>) = JSONObject().put("status", "available").put("dataOnly", true)
        .put("items", JSONArray(documents.map { JSONObject().put("id", it.id).put("origin", "local") })).toString()
    private fun ids(vararg docs: CustomCurveDocument) = JSONArray(docs.map { it.id }).toString()
    private fun status(result: String) = JSONObject(result).getString("status")
    @Test fun queryingTenDisplaysNoneAndActualTwoRecommendationsPreserveOrderAndReplaceIdempotently() {
        val documents = (1..10).map(::document); val library = documents.associateBy { it.id }
        val collector = AiCurveCardCollector { library[it]?.let(::item) }
        collector.captureToolResult(result(documents)); assertTrue(collector.snapshot().isEmpty())
        val selected = ids(documents[6], documents[1])
        assertEquals("selected", status(collector.select(selected)))
        assertEquals(listOf(documents[6].id, documents[1].id), collector.snapshot().map { it.id })
        val first = collector.snapshot(); collector.select(selected); assertEquals(first, collector.snapshot())
        collector.select(ids(documents[9])); assertEquals(listOf(documents[9].id), collector.snapshot().map { it.id })
    }
    @Test fun laterQueryPagesAreSelectableAndExplicitFiveOrTenAreNotHardCappedAtThree() {
        val documents = (1..20).map(::document); val library = documents.associateBy { it.id }
        val collector = AiCurveCardCollector { library[it]?.let(::item) }
        collector.captureToolResult(result(documents.take(10))); collector.captureToolResult(result(documents.drop(10)))
        val five = documents.drop(12).take(5)
        assertEquals("selected", status(collector.select(ids(*five.toTypedArray())))); assertEquals(5, collector.snapshot().size)
        assertEquals(five.map { it.id }, collector.snapshot().map { it.id })
        assertEquals("selected", status(collector.select(ids(*documents.take(10).toTypedArray())))); assertEquals(10, collector.snapshot().size)
        val previous = collector.snapshot()
        assertEquals("card_limit", status(collector.select(ids(*documents.take(11).toTypedArray())))); assertEquals(previous, collector.snapshot())
    }
    @Test fun unknownDuplicateChangedAndTamperedCandidatesRejectTheWholeSelection() {
        val documents = (1..3).map(::document); val library = documents.associateBy { it.id }.toMutableMap()
        val collector = AiCurveCardCollector { library[it]?.let(::item) }
        collector.captureToolResult(result(documents.take(2))); collector.select(ids(documents[0])); val previous = collector.snapshot()
        assertEquals("unknown_curve", status(collector.select(ids(documents[0], documents[2])))); assertEquals(previous, collector.snapshot())
        assertEquals("invalid_arguments", status(collector.select(ids(documents[0], documents[0])))); assertEquals(previous, collector.snapshot())
        library[documents[1].id] = documents[1].copy(temperatureC = 94)
        assertEquals("curve_changed", status(collector.select(ids(documents[0], documents[1])))); assertEquals(previous, collector.snapshot())
        val tampered = JSONObject(result(listOf(documents[2])))
        tampered.getJSONArray("items").getJSONObject(0).put("origin", "factory")
        collector.captureToolResult(tampered.toString())
        assertEquals("unknown_curve", status(collector.select(ids(documents[2])))); assertEquals(previous, collector.snapshot())
    }
    @Test fun sourceChangesAfterSelectionKeepTheOldSnapshotAndMakeItUnusableInsteadOfReplacingParameters() {
        val original = document(1); var current = original
        val collector = AiCurveCardCollector { item(current).takeIf { card -> card.id == it } }
        collector.captureToolResult(result(listOf(original))); collector.select(ids(original))
        val selected = collector.snapshot().single(); val fingerprint = selected.fingerprint
        current = original.copy(name = "Changed after selection")
        assertEquals(original, collector.snapshot().single().document)
        assertEquals(fingerprint, collector.snapshot().single().fingerprint)
        assertFalse(collector.snapshot().single().matches(item(current)))
        val nextTurn = AiCurveCardCollector { item(current) }
        assertEquals("unknown_curve", status(nextTurn.select(ids(current))))
    }
    @Test fun candidateCapIsExplicitAndDoesNotEvictEarlierOrAdmitAnUnregisteredRow() {
        val documents = (1..101).map(::document); val library = documents.associateBy { it.id }
        val collector = AiCurveCardCollector { library[it]?.let(::item) }
        documents.take(100).chunked(10).forEach { assertEquals("available", status(collector.captureToolResult(result(it)))) }
        assertEquals("candidate_limit", status(collector.captureToolResult(result(documents.takeLast(1)))))
        assertEquals("selected", status(collector.select(ids(documents.first(), documents[99]))))
        assertEquals("unknown_curve", status(collector.select(ids(documents.last()))))
        assertEquals(listOf(documents.first().id, documents[99].id), collector.snapshot().map { it.id })
    }
    @Test fun strictIdsOnlyAndExplicitEmptyClearNeverParseMarkdownOrRestoreOldTurnAuthority() {
        val original = document(1); val collector = AiCurveCardCollector { item(original) }
        collector.captureToolResult(result(listOf(original))); collector.select(ids(original)); val previous = collector.snapshot()
        for (bad in listOf("{}", "[{}]", "[null]", "[1]", "[[\"id\"]]", "[\"id\",]", "[Open](curve://${original.id})", " ".repeat(13000))) {
            assertEquals("invalid_arguments", status(collector.select(bad))); assertEquals(previous, collector.snapshot())
        }
        assertEquals("unknown_curve", status(collector.select("[\"curve://forged\"]")))
        assertEquals("selected", status(collector.select("[]"))); assertTrue(collector.snapshot().isEmpty())
    }
}
