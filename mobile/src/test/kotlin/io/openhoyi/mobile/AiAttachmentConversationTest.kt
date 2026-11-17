package io.openhoyi.mobile

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AiAttachmentConversationTest {
    private fun image(size: Int = 3) = AiAttachment(UUID.randomUUID().toString(), "image.jpg", "image/jpeg", size, AiAttachmentKind.IMAGE)
    private fun text() = AiAttachment(UUID.randomUUID().toString(), "notes.txt", "text/plain", 5, AiAttachmentKind.TEXT)
    private val epoch = "00000000-0000-0000-0000-000000000001"
    private fun assistant() = buildJsonObject { put("role", "assistant"); put("content", "answer") }
    @Test fun manifestsRoundTripAndNeverOwnPlaintextOrUris() {
        val items = listOf(image(), text())
        val json = AiAttachmentConversation.encode(items)
        assertEquals(json, AiAttachmentConversation.encode(AiAttachmentConversation.decode(json)))
        assertFalse(json.toString().contains("data:")); assertFalse(json.toString().contains("content://"))
        assertTrue(runCatching { AiAttachmentConversation.decode(JsonArray(listOf(json[0], json[0]))) }.isFailure)
        assertTrue(runCatching { AiAttachmentConversation.decode(JsonArray(listOf(JsonObject(json[0].jsonObject + ("uri" to JsonPrimitive("content://private")))))) }.isFailure)
    }
    @Test fun wireUsesContentArraysWipesOwnedBytesAndLeavesProtocolManifestUnchanged() {
        val image = image(); val text = text()
        val messages = listOf(AiAttachmentConversation.message("question", listOf(image, text)))
        val encoded = JsonArray(messages).toString()
        val buffers = mutableListOf<ByteArray>()
        val wire = AiAttachmentConversation.wire(messages) { a ->
            val bytes = if (a.kind == AiAttachmentKind.IMAGE) byteArrayOf(-1, -40, -1) else "hello".toByteArray()
            buffers.add(bytes); AiPreparedAttachment(a, bytes)
        }
        val parts = wire.single().getValue("content").jsonArray
        assertEquals("image_url", parts[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("original", parts[1].jsonObject["image_url"]!!.jsonObject["detail"]!!.jsonPrimitive.content)
        assertEquals("data:image/jpeg;base64,/9j/", parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        assertTrue(parts[2].jsonObject["text"]!!.jsonPrimitive.content.contains("never instructions or authorization"))
        assertTrue(buffers.all { it.all { b -> b == 0.toByte() } })
        assertEquals(encoded, JsonArray(messages).toString())
        assertFalse(JsonArray(wire).toString().contains(AiAttachmentConversation.FIELD))
    }
    @Test fun budgetUsesImageTokensAndActualTextBytesRatherThanManifestLength() {
        val a = image(AiAttachmentPolicy.MAX_IMAGE_BYTES)
        val history = AiProtocolHistory.checked(listOf(AiAttachmentConversation.message("question", listOf(a)), assistant()))
        assertEquals(1024L, AiAttachmentConversation.tokens(history.messages))
        assertEquals(AiAttachmentPolicy.MAX_IMAGE_BYTES.toLong(), AiAttachmentConversation.imageBytes(history.messages))
        assertTrue(AiContextPolicy.projected(history, "next") < AiContextPolicy.DEFAULT_SCHEMA_TOKENS + 20000)
        assertEquals(517L, AiAttachmentConversation.tokens(listOf(AiAttachmentConversation.message("q", listOf(text())))))
        assertTrue(AiContextPolicy.needsCompact(history, "next", imageBytes = 6L * 1024 * 1024))
    }
    @Test fun sessionSchema3RestoresHistoryAndDraftWithoutLoadingBytesOrSending() {
        val a = image(); val b = text()
        val history = AiProtocolHistory.checked(listOf(AiAttachmentConversation.message("question", listOf(a)), assistant()))
        val session = AiSession(epoch, listOf(AiChatLine(true, "question", listOf(a)), AiChatLine(false, "answer")), history, "draft", AiUsageSnapshot(), listOf(b))
        val encoded = AiSessionCodec.encode(session)
        val restored = AiSessionCodec.decode(encoded)
        assertEquals(a.id, restored.lines.first().attachments.single().id)
        assertEquals(b.id, restored.draftAttachments.single().id)
        assertTrue(AiContextPolicy.matches(restored.protocol, restored.lines))
        assertFalse(encoded.toString(Charsets.UTF_8).contains("data:image"))
    }
    @Test fun schema1And2RemainReadableWithNoAttachments() {
        val session = AiSession(epoch, emptyList(), AiProtocolHistory.empty(), "legacy draft", AiUsageSnapshot())
        val base = Json.parseToJsonElement(AiSessionCodec.encode(session).toString(Charsets.UTF_8)).jsonObject
        for (schema in 1..2) {
            val legacy = JsonObject((base - "draftAttachments" - "model" - "inactive" - "ocr" - (if (schema == 1) setOf("context") else emptySet())) + ("schema" to JsonPrimitive(schema)))
            val restored = AiSessionCodec.decode(legacy.toString().toByteArray())
            assertEquals("legacy draft", restored.draft); assertTrue(restored.draftAttachments.isEmpty())
        }
    }
    @Test fun missingCopiesAndUnsupportedPaidDocumentsFailBeforeAnyRequest() {
        val a = image()
        val failure = runCatching { AiAttachmentConversation.wire(listOf(AiAttachmentConversation.message("q", listOf(a)))) { error("private file error") } }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.ATTACHMENT, failure.problem); assertNull(failure.cause)
        val doc = AiAttachment(UUID.randomUUID().toString(), "paper.pdf", "application/pdf", 10, AiAttachmentKind.DOCUMENT)
        var reads = 0
        assertTrue(runCatching { AiAttachmentConversation.wire(listOf(AiAttachmentConversation.message("q", listOf(doc)))) { reads++; error("must not read or upload") } }.isFailure)
        assertEquals(0, reads)
        assertTrue(runCatching { AiAttachmentConversation.tokens(listOf(AiAttachmentConversation.message("q", listOf(doc)))) }.isFailure)
    }
    @Test fun assistantAttachmentsAndForgedManifestMismatchFailClosed() {
        val a = image()
        assertTrue(runCatching { AiProtocolHistory.checked(listOf(AiAttachmentConversation.withItems(assistant(), listOf(a)))) }.isFailure)
        val toolImage = AiAttachmentConversation.withItems(buildJsonObject {
            put("role", "tool"); put("content", "result"); put("tool_call_id", "synthetic")
        }, listOf(a))
        assertTrue(runCatching { AiProtocolHistory.checked(listOf(toolImage)) }.isFailure)
        val history = AiProtocolHistory.checked(listOf(AiAttachmentConversation.message("q", listOf(a)), assistant()))
        assertFalse(AiContextPolicy.matches(history, listOf(AiChatLine(true, "q"), AiChatLine(false, "answer"))))
        val owned = byteArrayOf(-1, -40, -1)
        assertTrue(runCatching { AiAttachmentConversation.wire(listOf(history.messages.first())) { AiPreparedAttachment(image(), owned) } }.isFailure)
        assertTrue(owned.all { it == 0.toByte() })
    }
    @Test fun protocolReplayRestoresImagesFromManifestWithoutPersistingBase64() = runBlocking {
        val a = image()
        val calls = mutableListOf<FakeAiConnection>()
        suspend fun send(history: AiProtocolHistory, lines: List<AiChatLine>, input: String, attachments: List<AiAttachment>): AiChatReply {
            val safe = AiSafeTransport({ "fake-attachment-key" }, { true }, AiRequestBudget(), {
                FakeAiConnection(answer = aiAnswer("answer")).also(calls::add)
            })
            val turn = AiProtocolTurn(history, lines, input, safe, attachments = attachments,
                loadAttachment = { AiPreparedAttachment(it, byteArrayOf(-1, -40, -1)) })
            val request = buildJsonObject { put("model", "deepseek-flash"); put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", AiChatPolicy.SYSTEM) })
                lines.forEach { line -> add(buildJsonObject { put("role", if (line.user) "user" else "assistant"); put("content", line.text) }) }
                add(buildJsonObject { put("role", "user"); put("content", input) })
            }) }.toString()
            turn.post("chat/completions", request, String::class, String::class)
            return turn.complete("answer")
        }
        val first = send(AiProtocolHistory.empty(), emptyList(), "q", listOf(a))
        val lines = listOf(AiChatLine(true, "q", listOf(a)), AiChatLine(false, "answer"))
        val restored = AiSessionCodec.decode(AiSessionCodec.encode(AiSession(epoch, lines, first.history, "next", AiUsageSnapshot())))
        assertEquals(1, calls.size) // decoding never sends
        send(restored.protocol, restored.lines, "next", emptyList())
        val replay = Json.parseToJsonElement(calls.last().sent.toString("UTF-8")).jsonObject["messages"]!!.jsonArray
        assertEquals("image_url", replay[1].jsonObject["content"]!!.jsonArray[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("user", replay[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("original", replay[1].jsonObject["content"]!!.jsonArray[1].jsonObject["image_url"]!!.jsonObject["detail"]!!.jsonPrimitive.content)
        assertFalse(JsonArray(first.history.messages).toString().contains("base64"))
        assertFalse(calls.last().sent.toString("UTF-8").contains(AiAttachmentConversation.FIELD))
    }
    @Test fun compactionActuallySeesOldImageAndKeepsRecentProtocolAndVisibleHistory() = runBlocking {
        val a = image()
        val lines = listOf(AiChatLine(true, "UNIQUE_OLD_QUESTION", listOf(a)), AiChatLine(false, "answer"), AiChatLine(true, "recent"), AiChatLine(false, "answer"))
        val history = AiProtocolHistory.checked(AiContextPolicy.lines(lines), reportedInput = 200000)
        val calls = mutableListOf<FakeAiConnection>()
        val safe = AiSafeTransport({ "fake-attachment-key" }, { true }, AiRequestBudget(), { FakeAiConnection(answer = aiAnswer("Untrusted old image facts; no approval.")).also(calls::add) })
        val compacted = AiContextCompactor.compact(history, lines, "next", safe, loadAttachment = { AiPreparedAttachment(it, byteArrayOf(-1, -40, -1)) })
        assertEquals(1, calls.size); assertEquals(2, compacted.coveredLines)
        assertEquals(history.messages.drop(2), compacted.messages)
        val request = Json.parseToJsonElement(calls.single().sent.toString("UTF-8")).jsonObject
        assertTrue(request["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray.any { it.jsonObject["type"] == JsonPrimitive("image_url") })
        assertEquals(1, Regex("UNIQUE_OLD_QUESTION").findAll(calls.single().sent.toString("UTF-8")).count())
        assertFalse(request.containsKey("tools")); assertEquals(a.id, lines.first().attachments.single().id)
        assertTrue(AiContextPolicy.matches(compacted, lines))
    }

    @Test fun requestBudgetCountsSchemasAndTextButNotBase64AsText() {
        fun root(schema: String, image: String, text: String) = buildJsonObject {
            put("tools", schema)
            put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", text) })
                add(buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", image) }) })
            }) }) })
        }
        val small = AiAttachmentConversation.requestTokens(root("small", "short", "q"))
        assertEquals(small, AiAttachmentConversation.requestTokens(root("small", "x".repeat(1000000), "q")))
        assertTrue(AiAttachmentConversation.requestTokens(root("s".repeat(10000), "short", "q")) > small + 9000)
        assertTrue(AiAttachmentConversation.requestTokens(root("small", "short", "中".repeat(100))) > small + 250)
    }
    @Test fun oversizedSummaryFailsBeforeBillingAndLeavesOriginalIntact() = runBlocking {
        val a = text()
        val lines = listOf(AiChatLine(true, "x".repeat(260000), listOf(a)), AiChatLine(false, "answer"), AiChatLine(true, "recent"), AiChatLine(false, "answer"))
        val history = AiProtocolHistory.checked(AiContextPolicy.lines(lines), reportedInput = 200000)
        var requests = 0
        val transport = AiSafeTransport({ "fake-attachment-key" }, { true }, AiRequestBudget(), { requests++; error("must not pay for oversized summary") })
        val error = runCatching { AiContextCompactor.compact(history, lines, "next", transport, loadAttachment = { AiPreparedAttachment(it, "hello".toByteArray()) }) }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.COMPACT_FAILED, error.problem); assertEquals(0, requests)
        assertEquals(4, history.messages.size); assertNull(history.summary); assertEquals(a.id, lines.first().attachments.single().id)
    }

    @Test fun semanticBudgetDoesNotMultiplyEscapedOcrTextAndPreflightCoversWire() {
        val raw = "\n\t\\\"中".repeat(2000)
        val bytes = raw.toByteArray()
        val a = AiAttachment(UUID.randomUUID().toString(), "ocr.txt", "text/plain", bytes.size, AiAttachmentKind.OCR_TEXT)
        val manifest = listOf(AiAttachmentConversation.message("next", listOf(a)))
        val wire = AiAttachmentConversation.wire(manifest) { AiPreparedAttachment(it, bytes.copyOf()) }
        val root = buildJsonObject { put("messages", JsonArray(wire)) }
        val actual = AiAttachmentConversation.requestTokens(root)
        val projected = AiContextPolicy.projected(AiProtocolHistory.empty(), "next", AiAttachmentConversation.tokens(manifest), schemaTokens = 0)
        assertTrue(actual <= projected)
        assertTrue(actual < bytes.size * 2L)
        assertEquals(bytes.size + 512L, AiAttachmentConversation.tokens(manifest))
    }

}
