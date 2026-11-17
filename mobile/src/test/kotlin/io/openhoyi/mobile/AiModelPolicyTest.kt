package io.openhoyi.mobile

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AiModelPolicyTest {
    private val epoch = "00000000-0000-0000-0000-000000000001"
    private fun state(model: String, draft: String): AiSession {
        val lines = listOf(AiChatLine(true, "$model question"), AiChatLine(false, "$model answer"))
        return AiSession(epoch, lines, AiProtocolHistory.checked(AiContextPolicy.lines(lines), modelId = model), draft, AiUsageSnapshot())
    }
    @Test fun trustedCatalogCapabilitiesAndUnknownIdsAreClosed() {
        assertEquals(listOf("deepseek-flash", "deepseek-v4-pro"), AiModelPolicy.models.map { it.id })
        assertEquals(1048576L, AiModelPolicy.known(AiModelPolicy.PRO).contextTokens)
        assertTrue(runCatching { AiModelPolicy.known("unknown-model") }.isFailure)
        val a = AiAttachment(UUID.randomUUID().toString(), "image.jpg", "image/jpeg", 3, AiAttachmentKind.IMAGE)
        assertTrue(AiModelPolicy.accepts(AiModelPolicy.FLASH, listOf(a)))
        assertFalse(AiModelPolicy.accepts(AiModelPolicy.PRO, listOf(a)))
        assertTrue(runCatching { AiProtocolHistory.checked(listOf(AiAttachmentConversation.message("q", listOf(a))), modelId = AiModelPolicy.PRO) }.isFailure)
    }
    @Test fun modelSwitchRestoresFullIndependentProtocolDraftAndUsageAfterRestart() {
        val flash = state(AiModelPolicy.FLASH, "Flash unsent")
        val pro = AiModelSessions.switch(flash, AiModelPolicy.PRO)
        assertTrue(pro.lines.isEmpty()); assertTrue(pro.protocol.messages.isEmpty()); assertEquals("", pro.draft)
        assertEquals(flash.protocol.messages, pro.inactive.single().protocol.messages)
        val proWithDraft = AiSession(epoch, pro.lines, pro.protocol, "Pro unsent", pro.usage, inactive = pro.inactive)
        val recovered = AiSessionCodec.decode(AiSessionCodec.encode(proWithDraft))
        val back = AiModelSessions.switch(recovered, AiModelPolicy.FLASH)
        assertEquals("Flash unsent", back.draft); assertEquals(flash.lines, back.lines); assertEquals(flash.protocol.messages, back.protocol.messages)
        assertEquals("Pro unsent", back.inactive.single().draft)
        assertEquals(AiModelPolicy.PRO, back.inactive.single().modelId)
    }
    @Test fun codecRejectsRecursiveDuplicateCrossEpochUnknownModelsAndTotalOversize() {
        val flash = state(AiModelPolicy.FLASH, "draft"); val pro = state(AiModelPolicy.PRO, "other")
        assertTrue(runCatching { AiSessionCodec.encode(AiSession(epoch, flash.lines, flash.protocol, "", flash.usage, inactive = listOf(flash))) }.isFailure)
        assertTrue(runCatching { AiSessionCodec.encode(AiSession(epoch, flash.lines, flash.protocol, "", flash.usage, inactive = listOf(AiSession(epoch, pro.lines, pro.protocol, "", pro.usage, inactive = listOf(flash))))) }.isFailure)
        val raw = Json.parseToJsonElement(AiSessionCodec.encode(flash).toString(Charsets.UTF_8)).jsonObject
        assertTrue(runCatching { AiSessionCodec.decode(JsonObject(raw + ("model" to JsonPrimitive("arbitrary"))).toString().toByteArray()) }.isFailure)
        assertTrue(runCatching { AiSessionCodec.decode(ByteArray(AiSessionCodec.MAX_BYTES + 1)) }.isFailure)
        val alien = AiSession("00000000-0000-0000-0000-000000000002", pro.lines, pro.protocol, "", pro.usage)
        assertTrue(runCatching { AiSessionCodec.encode(AiSession(epoch, flash.lines, flash.protocol, "", flash.usage, inactive = listOf(alien))) }.isFailure)
    }
    @Test fun protocolRejectsCrossModelBeforeNetworkAndFailedRequestsStayInRequestedUsageBucket() = runBlocking {
        var calls = 0
        val ledger = AiUsageLedger()
        val safe = AiSafeTransport({ "fake-model-key" }, { true }, AiRequestBudget(), { calls++; FakeAiConnection(401, "private") }, usage = AiUsageRecorder(listOf(ledger)))
        val turn = AiProtocolTurn(AiProtocolHistory.empty(AiModelPolicy.PRO), emptyList(), "q", safe)
        fun body(model: String) = buildJsonObject { put("model", model); put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", AiChatPolicy.SYSTEM) }); add(buildJsonObject { put("role", "user"); put("content", "q") })
        }) }.toString()
        val mismatch = runCatching { turn.post("chat/completions", body(AiModelPolicy.FLASH), String::class, String::class) }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.MODEL, mismatch.problem); assertEquals(0, calls)
        assertTrue(runCatching { turn.post("chat/completions", body(AiModelPolicy.PRO), String::class, String::class) }.isFailure)
        assertEquals(1, calls); assertEquals(AiModelPolicy.PRO, ledger.snapshot().models.single().model)
        assertEquals(1L, ledger.snapshot().unknownChat)
    }
    @Test fun proCompactionUsesSameModelAndNoImplicitFlashFallback() = runBlocking {
        val lines = listOf(AiChatLine(true, "old"), AiChatLine(false, "answer"), AiChatLine(true, "recent"), AiChatLine(false, "answer"))
        val history = AiProtocolHistory.checked(AiContextPolicy.lines(lines), reportedInput = 200000, modelId = AiModelPolicy.PRO)
        val calls = mutableListOf<FakeAiConnection>()
        val safe = AiSafeTransport({ "fake-model-key" }, { true }, AiRequestBudget(), { FakeAiConnection(answer = aiAnswer("old facts")).also(calls::add) })
        val compacted = AiContextCompactor.compact(history, lines, "next", safe)
        assertEquals(AiModelPolicy.PRO, compacted.modelId)
        assertEquals(AiModelPolicy.PRO, Json.parseToJsonElement(calls.single().sent.toString("UTF-8")).jsonObject["model"]!!.jsonPrimitive.content)
    }
    @Test fun successfulCheckpointFencesLateUsageBeforeReleasingTheSlotLock() {
        val lock = Any(); var generation = 1L; var base = state(AiModelPolicy.FLASH,"old")
        val candidate = AiModelSessions.switch(base,AiModelPolicy.PRO)
        val oldToken = generation
        val late = java.util.concurrent.CountDownLatch(1)
        var worker: Thread? = null
        assertTrue(AiSessionTransactions.commit(lock,candidate,save={
            worker=Thread { synchronized(lock) { if(oldToken==generation) base=state(AiModelPolicy.FLASH,"stale overwrite") }; late.countDown() }.also { it.start() }
            true
        },committed={ generation++;base=it }))
        assertTrue(late.await(2,java.util.concurrent.TimeUnit.SECONDS));worker!!.join()
        assertEquals(AiModelPolicy.PRO,base.modelId);assertEquals("old",base.inactive.single().draft)
    }
    @Test fun failedCheckpointPreservesOriginalSlotAndDoesNotFenceItsUsage() {
        val current=state(AiModelPolicy.FLASH,"unsent");var base=current;var generation=1
        assertFalse(AiSessionTransactions.commit(Any(),AiModelSessions.switch(current,AiModelPolicy.PRO),save={false},committed={base=it;generation++}))
        assertSame(current,base);assertEquals(1,generation)
    }
    @Test fun cancelNewConversationOrNewOperationRejectsLateOcrResultAndFinally() {
        val fence=AiOperationFence();val old=fence.begin();assertTrue(fence.current(old))
        fence.invalidate();assertFalse(fence.current(old))
        val next=fence.begin();assertTrue(fence.current(next));assertFalse(fence.current(old))
        var activeJob="new"
        if(fence.current(old)) activeJob="cleared by old finally"
        assertEquals("new",activeJob)
    }

    @Test fun imageHistoryAndUnsentImageDraftStayInFlashSlotAcrossProSwitchAndRestore() = runBlocking {
        fun image(name: String) = AiAttachment(UUID.randomUUID().toString(), name, "image/jpeg", 3, AiAttachmentKind.IMAGE)
        val historical = image("historical.jpg"); val unsent = image("unsent.jpg")
        val lines = listOf(AiChatLine(true, "picture question", listOf(historical)), AiChatLine(false, "picture answer"))
        val flash = AiSession(epoch, lines, AiProtocolHistory.checked(AiContextPolicy.lines(lines)), "unsent image question",
            AiUsageSnapshot(), listOf(unsent))
        val pro = AiModelSessions.switch(flash, AiModelPolicy.PRO)
        assertTrue(pro.lines.isEmpty()); assertTrue(pro.protocol.messages.isEmpty()); assertTrue(pro.draftAttachments.isEmpty())
        assertEquals(historical.id, pro.inactive.single().lines.first().attachments.single().id)
        assertEquals(unsent.id, pro.inactive.single().draftAttachments.single().id)
        val restoredPro = AiSessionCodec.decode(AiSessionCodec.encode(pro))
        val back = AiModelSessions.switch(restoredPro, AiModelPolicy.FLASH)
        assertEquals(flash.protocol.messages, back.protocol.messages)
        assertEquals(flash.lines.map { it.user to it.text }, back.lines.map { it.user to it.text })
        assertEquals(historical.id, back.lines.first().attachments.single().id)
        assertEquals(historical.name, back.lines.first().attachments.single().name)
        assertEquals(historical.mimeType, back.lines.first().attachments.single().mimeType)
        assertEquals(historical.size, back.lines.first().attachments.single().size)
        assertEquals(flash.draft, back.draft); assertEquals(unsent.id, back.draftAttachments.single().id)
        assertTrue(AiContextPolicy.matches(back.protocol, back.lines))
        var calls = 0; var loads = 0
        val safe = AiSafeTransport({ "fake-model-image-key" }, { true }, AiRequestBudget(), { calls++; FakeAiConnection() })
        val turn = AiProtocolTurn(restoredPro.protocol, restoredPro.lines, "q", safe, attachments = listOf(unsent),
            loadAttachment = { loads++; error("Unsupported Pro image must not load") })
        val request = buildJsonObject { put("model", AiModelPolicy.PRO); put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", AiChatPolicy.SYSTEM) })
            add(buildJsonObject { put("role", "user"); put("content", "q") })
        }) }.toString()
        val failure = runCatching { turn.post("chat/completions", request, String::class, String::class) }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.MODEL, failure.problem); assertEquals(0, calls); assertEquals(0, loads)
        assertEquals(flash.protocol.messages, back.protocol.messages)
    }

}
