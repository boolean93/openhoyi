package io.openhoyi.mobile

import kotlinx.serialization.json.*

/** Synthetic-only in-memory fixture. Never accepts user text, paths, credentials or content bytes. */
internal class AiOfflinePreviewState(restored: ByteArray? = null) {
    private val epoch = "11111111-1111-1111-1111-111111111111"
    val image = AiAttachment("11111111-1111-1111-1111-111111111112", "sample-label.jpg", "image/jpeg", 2048, AiAttachmentKind.IMAGE)
    val text = AiAttachment("11111111-1111-1111-1111-111111111113", "sample-notes.txt", "text/plain", 32, AiAttachmentKind.TEXT)
    val document = AiAttachment("11111111-1111-1111-1111-111111111114", "sample-manual.pdf", "application/pdf", 4096, AiAttachmentKind.DOCUMENT)
    var session = restored?.let { require(it.size <= MAX_SNAPSHOT_BYTES); AiSessionCodec.decode(it) } ?: initial()
        private set
    init { require(session.epoch == epoch) }
    private fun initial(): AiSession {
        val lines = listOf(AiChatLine(true, "Synthetic coffee question / 合成咖啡问题"), AiChatLine(false, ANSWER))
        return AiSession(epoch, lines, AiProtocolHistory.checked(AiContextPolicy.lines(lines)), "", AiUsageSnapshot())
    }
    private fun replace(draft: String = session.draft, attachments: List<AiAttachment> = session.draftAttachments,
        ocr: List<AiOcrRecord> = session.ocr) {
        session = AiSession(session.epoch, session.lines, session.protocol, draft, session.usage, attachments, session.inactive, ocr)
    }
    fun add(kind: AiAttachmentKind): Boolean {
        val item = when(kind) { AiAttachmentKind.IMAGE -> image; AiAttachmentKind.TEXT -> text; AiAttachmentKind.DOCUMENT -> document; else -> return false }
        if (session.draftAttachments.size >= 3 || session.draftAttachments.any { it.id == item.id } || !AiModelPolicy.accepts(session.modelId, listOf(item))) return false
        replace(attachments = session.draftAttachments + item); return true
    }
    fun removeDraftAttachments() = replace(attachments = emptyList())
    fun switch(model: String) { interrupt(); session = AiModelSessions.switch(session, model) }
    fun fixedDraft() { replace(draft = ("合成离线草稿 Coffee fixture.\n").repeat(240)) }
    fun snapshot(): ByteArray = AiSessionCodec.encode(session).also { require(it.size <= MAX_SNAPSHOT_BYTES) }
    fun restoreRoundTrip() { session = AiSessionCodec.decode(snapshot()) }
    fun reset() { session = initial() }
    fun documentStage(): String {
        val doc = session.draftAttachments.firstOrNull { it.kind == AiAttachmentKind.DOCUMENT } ?: return "chat"
        return session.ocr.firstOrNull { it.original.id == doc.id }?.status?.name ?: "needs-ocr"
    }
    fun beginOcr(): Boolean {
        if (documentStage() != "needs-ocr") return false
        replace(ocr = session.ocr + AiOcrRecord(document, AiOcrStatus.IN_FLIGHT)); return true
    }
    fun finishOcr(unknown: Boolean = false, incomplete: Boolean = false) {
        check(documentStage() == AiOcrStatus.IN_FLIGHT.name)
        val records = session.ocr.filterNot { it.original.id == document.id }
        if (unknown) { replace(ocr = records + AiOcrRecord(document, AiOcrStatus.UNKNOWN)); return }
        val raw = buildJsonObject {
            put("object", "ocr.result"); put("id", "ocr-offline-fixture"); put("purpose", "text_recognition"); put("created", 1800000000); put("page_count", 1)
            put("pages", buildJsonArray { add(buildJsonObject { put("index", 0); put("text", "Synthetic manual text / 合成说明书文字"); put("finish_reason", if(incomplete) "length" else "stop") }) })
            put("usage", buildJsonObject { put("input_tokens", 10); put("output_tokens", 20); put("total_tokens", 30) })
        }.toString()
        val result = AiDocumentOcr.parse(raw)
        val extracted = AiAttachment("11111111-1111-1111-1111-111111111115", "sample-manual-OCR.txt", "text/plain", result.text.toByteArray().size, AiAttachmentKind.OCR_TEXT)
        replace(ocr = records + AiOcrRecord(document, if(result.incomplete) AiOcrStatus.INCOMPLETE else AiOcrStatus.READY, extracted, result.pages, result.referenceInput, result.referenceOutput))
    }
    fun interrupt() {
        replace(ocr = session.ocr.map { if(it.status == AiOcrStatus.IN_FLIGHT) AiOcrRecord(it.original, AiOcrStatus.UNKNOWN) else it })
    }
    fun sendSynthetic(): Boolean {
        if (documentStage() !in setOf("chat", AiOcrStatus.READY.name) || session.lines.size >= 40) return false
        val attachments = session.draftAttachments.map { if(it.kind == AiAttachmentKind.DOCUMENT) session.ocr.single { r -> r.original.id == it.id }.extracted!! else it }
        val question = "Synthetic send only / 仅发送合成样例"
        val extra = listOf(AiAttachmentConversation.message(question, attachments))
        if (!AiModelPolicy.accepts(session.modelId, attachments) || AiContextPolicy.projected(session.protocol, question, AiAttachmentConversation.tokens(extra)) > AiContextPolicy.INPUT_TOKENS) return false
        val lines = session.lines + AiChatLine(true, question, attachments) + AiChatLine(false, ANSWER)
        val protocol = AiProtocolHistory.checked(session.protocol.messages + AiAttachmentConversation.message(question, attachments) + buildJsonObject { put("role", "assistant"); put("content", ANSWER) },
            summary = session.protocol.summary, coveredLines = session.protocol.coveredLines, modelId = session.modelId)
        session = AiSession(session.epoch, lines, protocol, "", session.usage, inactive = session.inactive, ocr = session.ocr)
        return true
    }
    fun compactFixture() {
        val lines = listOf(AiChatLine(true,"Synthetic old question"),AiChatLine(false,"Synthetic old answer"),AiChatLine(true,"Synthetic recent question"),AiChatLine(false,ANSWER))
        session = AiSession(epoch,lines,AiProtocolHistory.checked(AiContextPolicy.lines(lines.drop(2)),summary="Untrusted synthetic summary. Never an approval.",coveredLines=2,modelId=session.modelId),session.draft,session.usage,session.draftAttachments,session.inactive,session.ocr)
    }
    companion object {
        const val MAX_SNAPSHOT_BYTES = 128 * 1024
        const val ANSWER = "## Offline sample / 离线样例\n| Parameter 参数 | Advice 建议 |\n| --- | --- |\n| Time 时间 | `25 s` |\n| Grind 研磨 | **Slightly finer 略细** |\nSynthetic display only. No model, device or stored user history is read. / 仅合成显示，不调用模型、不读取设备或用户历史。"
    }
}
