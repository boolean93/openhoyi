package io.openhoyi.mobile

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class AiOcrProblem { KEY, AUTH, RATE_LIMIT, LIMIT, INVALID, REJECTED, UNKNOWN }
internal class AiOcrFailure(val problem: AiOcrProblem, val mayHaveCharged: Boolean = false) : Exception(problem.name)
internal class AiOcrResult(val text: String, val pages: Int, val incomplete: Boolean,
    val referenceInput: Long, val referenceOutput: Long) {
    override fun toString() = "AiOcrResult"
}
/** No Files API storage, URLs, retries, logs, model calls or chat usage accounting. */
internal class AiDocumentOcr(private val key: () -> String, private val keyStillCurrent: () -> Boolean,
    private val connection: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) : AutoCloseable {
    private val closed = AtomicBoolean()
    private val attempted = AtomicBoolean()
    private val active = AtomicReference<HttpsURLConnection?>()
    /** Caller must durably checkpoint the original attachment attempt before calling this method. */
    suspend fun recognize(a: AiAttachment, bytes: ByteArray): AiOcrResult {
        if (a.kind != AiAttachmentKind.DOCUMENT || a.size != bytes.size || a.mimeType !in AiAttachmentPolicy.DOCUMENT_MIMES)
            throw AiOcrFailure(AiOcrProblem.INVALID)
        AiAttachmentDocuments.validate(bytes, a.mimeType)
        if (!attempted.compareAndSet(false, true)) throw AiOcrFailure(AiOcrProblem.UNKNOWN, true)
        val boundary = "OpenHOYI" + UUID.randomUUID().toString().replace("-", "")
        val ext = when (a.mimeType) { "application/pdf" -> "pdf"; "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"; else -> "pptx" }
        val prefix = ("--$boundary\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\ntext_recognition\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.$ext\"\r\nContent-Type: ${a.mimeType}\r\n\r\n").toByteArray(Charsets.US_ASCII)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.US_ASCII)
        try {
            return withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { pending ->
                    var owner: HttpsURLConnection? = null
                    var started = false
                    val result: Result<AiOcrResult> = try {
                        checkAvailable { pending.isActive }
                        val socket = connection(URL(ENDPOINT)).also { owner = it }
                        if (socket.url.toString() != ENDPOINT || !active.compareAndSet(null, socket)) throw AiOcrFailure(AiOcrProblem.INVALID)
                        pending.invokeOnCancellation { close() }
                        socket.instanceFollowRedirects = false; socket.useCaches = false; socket.requestMethod = "POST"
                        socket.connectTimeout = 15000; socket.readTimeout = 185000; socket.doOutput = true
                        socket.setFixedLengthStreamingMode(prefix.size + bytes.size + suffix.size)
                        socket.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                        socket.setRequestProperty("Accept", "application/json"); socket.setRequestProperty("Connection", "close")
                        val credential = try { key() } catch (_: Exception) { throw AiOcrFailure(AiOcrProblem.KEY) }
                        if (!AiKeyCodec.valid(credential)) throw AiOcrFailure(AiOcrProblem.KEY)
                        checkAvailable { pending.isActive }
                        socket.setRequestProperty("Authorization", "Bearer $credential")
                        val watchdog = CoroutineScope(pending.context).launch(Dispatchers.IO) { delay(195000); runCatching { socket.disconnect() } }
                        try {
                            checkAvailable { pending.isActive }; started = true
                            socket.connect()
                            socket.outputStream.use { output ->
                                output.write(prefix)
                                var offset = 0
                                while (offset < bytes.size) {
                                    checkAvailable { pending.isActive }
                                    val count = minOf(8192, bytes.size - offset); output.write(bytes, offset, count); offset += count
                                }
                                checkAvailable { pending.isActive }; output.write(suffix)
                            }
                            checkAvailable { pending.isActive }
                            val status = socket.responseCode
                            if (status !in 200..299) {
                                runCatching { socket.errorStream?.close() }
                                throw AiOcrFailure(when(status) { 401,403 -> AiOcrProblem.AUTH; 402,429 -> AiOcrProblem.RATE_LIMIT
                                    400,408,413,415,422 -> AiOcrProblem.REJECTED; else -> AiOcrProblem.UNKNOWN }, true)
                            }
                            val response = WipeBuffer(); val buffer = ByteArray(4096)
                            try {
                                socket.inputStream.use { input ->
                                    while (true) {
                                        checkAvailable { pending.isActive }
                                        val count = input.read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_BYTES - response.size() + 1))
                                        if (count < 0) break
                                        if (count == 0 || response.size() + count > MAX_RESPONSE_BYTES) throw AiOcrFailure(AiOcrProblem.LIMIT, true)
                                        response.write(buffer, 0, count)
                                    }
                                }
                                checkAvailable { pending.isActive }
                                val received = response.toByteArray()
                                val raw = try { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(received)).toString() } finally { received.fill(0) }
                                Result.success(parse(raw))
                            } finally { buffer.fill(0); response.wipe() }
                        } finally { watchdog.cancel() }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: AiOcrFailure) { Result.failure(if (started && !failure.mayHaveCharged) AiOcrFailure(failure.problem, true) else failure) }
                    catch (_: Exception) { Result.failure(AiOcrFailure(if (!keyStillCurrent()) AiOcrProblem.KEY else AiOcrProblem.UNKNOWN, started)) }
                    finally { owner?.let { active.compareAndSet(it, null); runCatching { it.disconnect() } } }
                    if (pending.isActive) result.fold(pending::resume, pending::resumeWithException)
                }
            }
        } finally { prefix.fill(0); suffix.fill(0) }
    }
    private fun checkAvailable(alive: () -> Boolean) {
        if (!keyStillCurrent()) throw AiOcrFailure(AiOcrProblem.KEY)
        if (closed.get() || !alive()) throw CancellationException()
    }
    override fun close() { closed.set(true); active.getAndSet(null)?.let { runCatching { it.disconnect() } } }
    private class WipeBuffer : ByteArrayOutputStream() { fun wipe() { buf.fill(0); reset() } }
    companion object {
        const val ENDPOINT = "https://api.deepseek.com/v1/tools/ocr"
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        fun parse(raw: String): AiOcrResult = try {
            val o = Json.parseToJsonElement(raw).jsonObject
            fun string(value: JsonElement?) = (value as? JsonPrimitive)?.also { require(it.isString) }?.content ?: error("Invalid OCR")
            fun number(value: JsonElement?) = (value as? JsonPrimitive)?.also { require(!it.isString) }?.long?.also { require(it in 0..1_000_000_000_000L) } ?: error("Invalid OCR")
            require("error" !in o && string(o["object"]) == "ocr.result" && string(o["purpose"]) == "text_recognition")
            require(string(o["id"]).matches(Regex("ocr-[A-Za-z0-9_-]{1,160}")) && number(o["created"]) > 0)
            val pages = o.getValue("pages").jsonArray
            val count = number(o["page_count"]); require(count in 1..1000 && count == pages.size.toLong())
            var incomplete = false
            val text = buildString {
                pages.forEachIndexed { index, element ->
                    val page = element.jsonObject; require(number(page["index"]) == index.toLong())
                    val reason = string(page["finish_reason"]); require(reason in setOf("stop", "length"))
                    if (reason == "length") incomplete = true
                    append("[OCR page "); append(index + 1); if (reason == "length") append(" INCOMPLETE"); append("]\n")
                    append(string(page["text"])); append("\n\n")
                }
            }
            val bytes = text.toByteArray(Charsets.UTF_8)
            try { require(bytes.toString(Charsets.UTF_8) == text); AiAttachmentPolicy.validateText(bytes, AiAttachmentPolicy.MAX_EXTRACTED_BYTES) }
            finally { bytes.fill(0) }
            val usage = o.getValue("usage").jsonObject
            val input = number(usage["input_tokens"]); val output = number(usage["output_tokens"])
            require(input <= 1_000_000_000L && output <= 1_000_000_000L && number(usage["total_tokens"]) == input + output)
            AiOcrResult(text, pages.size, incomplete, input, output)
        } catch (f: AiAttachmentFailure) { throw AiOcrFailure(if (f.problem == AiAttachmentProblem.SIZE) AiOcrProblem.LIMIT else AiOcrProblem.INVALID, true) }
        catch (_: Exception) { throw AiOcrFailure(AiOcrProblem.UNKNOWN, true) }
    }
}

internal enum class AiOcrStatus { IN_FLIGHT, UNKNOWN, READY, INCOMPLETE, LIMIT, FAILED }
/** Metadata only; original and extracted text live in the encrypted attachment vault. */
internal class AiOcrRecord(val original: AiAttachment, val status: AiOcrStatus, val extracted: AiAttachment? = null,
    val pages: Int? = null, val referenceInput: Long? = null, val referenceOutput: Long? = null) {
    init {
        require(original.kind == AiAttachmentKind.DOCUMENT)
        require(extracted == null || extracted.kind == AiAttachmentKind.OCR_TEXT && extracted.id != original.id)
        require((referenceInput == null) == (referenceOutput == null))
        require(pages == null || pages in 1..1000)
        require(referenceInput == null || referenceInput in 0..1_000_000_000L && referenceOutput!! in 0..1_000_000_000L)
        require(status !in setOf(AiOcrStatus.READY, AiOcrStatus.INCOMPLETE) || extracted != null && pages != null)
    }
    override fun toString() = "AiOcrRecord"
}
internal object AiOcrRecords {
    fun encode(records: List<AiOcrRecord>) = buildJsonArray {
        require(records.size <= 128 && records.map { it.original.id }.distinct().size == records.size)
        records.forEach { r -> add(buildJsonObject {
            put("original", AiAttachmentConversation.encode(listOf(r.original)).single())
            put("status", r.status.name); put("extracted", r.extracted?.let { AiAttachmentConversation.encode(listOf(it)).single() } ?: JsonNull)
            put("pages", r.pages?.let(::JsonPrimitive) ?: JsonNull)
            put("referenceInput", r.referenceInput?.let(::JsonPrimitive) ?: JsonNull); put("referenceOutput", r.referenceOutput?.let(::JsonPrimitive) ?: JsonNull)
        }) }
    }
    fun decode(value: JsonElement?): List<AiOcrRecord> {
        if (value == null) return emptyList()
        val records = value.jsonArray.map { e ->
            val o = e.jsonObject
            require(o.keys == setOf("original", "status", "extracted", "pages", "referenceInput", "referenceOutput"))
            fun number(k: String) = o[k]?.takeUnless { it == JsonNull }?.jsonPrimitive?.also { require(!it.isString) }?.long
            val status = o.getValue("status").jsonPrimitive.also { require(it.isString) }.content
            AiOcrRecord(AiAttachmentConversation.decode(JsonArray(listOf(o.getValue("original")))).single(),
                AiOcrStatus.valueOf(status).let { if (it == AiOcrStatus.IN_FLIGHT) AiOcrStatus.UNKNOWN else it },
                o["extracted"]?.takeUnless { it == JsonNull }?.let { AiAttachmentConversation.decode(JsonArray(listOf(it))).single() },
                number("pages")?.also { require(it in 1..1000) }?.toInt(), number("referenceInput"), number("referenceOutput"))
        }
        encode(records); return records
    }
}
