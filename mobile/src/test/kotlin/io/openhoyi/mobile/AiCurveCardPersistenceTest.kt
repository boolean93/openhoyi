package io.openhoyi.mobile

import kotlinx.serialization.json.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiCurveCardPersistenceTest {
    private val doc = CustomCurveDocument("draft-00000000-0000-0000-0000-000000000001", "Synthetic", 93, 3600,
        CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(90, 400), CustomCurveDocument.Stage(60, 100)))
    private val card get() = AiCurveCard.recommendation(CurveLibraryItem(doc.id, doc.name, "", "", null, customDocument = doc))
    private fun session(model: String = AiModelPolicy.FLASH): AiSession {
        val messages = listOf(buildJsonObject { put("role", "user"); put("content", "Q") },
            buildJsonObject { put("role", "assistant"); put("content", "A") })
        return AiSession("00000000-0000-0000-0000-000000000001", listOf(AiChatLine(true, "Q"), AiChatLine(false, "A", curveCards = listOf(card))),
            AiProtocolHistory.checked(messages, modelId = model), "draft", AiUsageSnapshot())
    }
    @Test fun sessionPayloadRoundtripRetainsCardsDraftAndInactiveModelWithoutAddingProtocolAuthority() {
        val active = session(); val inactive = session(AiModelPolicy.PRO)
        val full = AiSession(active.epoch, active.lines, active.protocol, active.draft, active.usage, inactive = listOf(inactive))
        val restored = AiSessionCodec.decode(AiSessionCodec.encode(full))
        assertEquals(active.lines, restored.lines); assertEquals("draft", restored.draft)
        assertEquals(inactive.lines, restored.inactive.single().lines)
        assertEquals(active.protocol.messages, restored.protocol.messages)
        assertFalse(restored.protocol.messages.toString().contains("fingerprint"))
    }
    @Test fun compactRetainsHistoricalCardsAndDoesNotAddThemToWorkingProtocol() {
        val initial = session()
        val lines = initial.lines + listOf(AiChatLine(true, "next"), AiChatLine(false, "answer"))
        val protocol = AiProtocolHistory.checked(listOf(
            buildJsonObject { put("role", "user"); put("content", "next") },
            buildJsonObject { put("role", "assistant"); put("content", "answer") }), summary = "Untrusted old context", coveredLines = 2)
        val state = AiSession(initial.epoch, lines, protocol, "", initial.usage)
        val restored = AiSessionCodec.decode(AiSessionCodec.encode(state))
        assertEquals(card, restored.lines[1].curveCards.single())
        assertEquals(2, restored.protocol.coveredLines); assertEquals(2, restored.protocol.messages.size)
    }
    @Test fun tenReferencesPlusOneInertProposalPersistTogether() {
        val disk = object : CustomCurveStore.Storage {
            override fun read(): String? = null
            override fun write(value: String) { error("No write expected") }
        }
        val coordinator = AiCurveWriteCoordinator(CustomCurveStore(disk))
        val body = JSONObject(doc.encode()).also { it.remove("id") }
        val proposal = coordinator.propose(JSONObject().put("operation", "create").put("curve", body).toString(),
            AiCurveWriteCoordinator.Epoch("synthetic", 1))
        val references = List(10) { index ->
            val d = doc.copy(id = "draft-00000000-0000-0000-0000-${(index + 1).toString().padStart(12, '0')}")
            AiCurveCard.recommendation(CurveLibraryItem(d.id, d.name, "", "", null, customDocument = d))
        }
        val base = session()
        val lines = listOf(base.lines[0], base.lines[1].copy(curveCards = references + AiCurveCard.proposal(proposal)))
        val full = AiSession(base.epoch, lines, base.protocol, "draft", base.usage)
        val restored = AiSessionCodec.decode(AiSessionCodec.encode(full))
        assertEquals(11, restored.lines[1].curveCards.size)
        assertEquals(1, restored.lines[1].curveCards.count { it.kind != AiCurveCard.Kind.RECOMMENDATION })
        coordinator.cancel()
        assertFalse(restored.lines[1].curveCards.last().matchesProposal(coordinator.pending(proposal.epoch)))
    }
    @Test fun responseAndCardsHaveBoundedStorageReservedBeforeRequest() {
        val edge = AiSessionCodec.MAX_BYTES - AiSessionCodec.TURN_RESERVE_BYTES
        assertTrue(AiSessionCodec.hasTurnHeadroomBytes(edge))
        assertFalse(AiSessionCodec.hasTurnHeadroomBytes(edge + 1))
        assertTrue(AiSessionCodec.hasTurnHeadroomBytes(edge + 1000, 1000))
        assertFalse(AiSessionCodec.hasTurnHeadroomBytes(edge + 1001, 1000))
        assertTrue(AiSessionCodec.hasTurnHeadroom(session()))
        assertTrue(AiSessionCodec.TURN_RESERVE_BYTES >= AiProtocolHistory.MAX_BYTES + 11 * AiCurveCard.MAX_ENCODED_BYTES)
    }
    @Test fun schema4RemainsReadableAndCardsCannotBeAttachedToUserOrOverflowed() {
        val old = JSONObject(AiSessionCodec.encode(session()).toString(Charsets.UTF_8)).put("schema", 4)
        old.remove("hoyiScreenshotSourceId")
        val rows = old.getJSONArray("lines")
        for (i in 0 until rows.length()) rows.getJSONObject(i).remove("curveCards")
        assertTrue(AiSessionCodec.decode(old.toString().toByteArray()).lines.all { it.curveCards.isEmpty() })
        val bad = JSONObject(AiSessionCodec.encode(session()).toString(Charsets.UTF_8))
        bad.getJSONArray("lines").getJSONObject(0).getJSONArray("curveCards").put(card.encode())
        assertTrue(runCatching { AiSessionCodec.decode(bad.toString().toByteArray()) }.isFailure)
        val tooMany = session().let { AiSession(it.epoch, listOf(it.lines[0], it.lines[1].copy(curveCards = List(11) { card })), it.protocol, "", it.usage) }
        assertTrue(runCatching { AiSessionCodec.encode(tooMany) }.isFailure)
    }
}
