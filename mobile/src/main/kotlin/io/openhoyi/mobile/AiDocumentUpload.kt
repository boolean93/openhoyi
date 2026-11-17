package io.openhoyi.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class AiDocumentUploadProblem { KEY, AUTH, RATE_LIMIT, LIMIT, INVALID, REJECTED, UNKNOWN, EXPIRED }
/** No provider body, filename, key, URL or source exception is ever exposed. */
internal class AiDocumentUploadFailure(val problem: AiDocumentUploadProblem, val remoteMayExist: Boolean = false) : Exception(problem.name)

internal class AiUploadedDocument(val localAttachmentId: String, val fileId: String, val expiresAt: Long) {
    init {
        require(runCatching { UUID.fromString(localAttachmentId).toString() == localAttachmentId }.getOrDefault(false))
        require(fileId.matches(Regex("file-api-[A-Za-z0-9_-]{1,160}")) && expiresAt > 0)
    }
    override fun toString() = "AiUploadedDocument"
}

/**
 * Call ONLY after explicit upload/cost confirmation, never from a picker callback.
 * PDF/Office rich-layout parsing is billed at upload; figures may be missed and chat costs extra.
 * Disconnect/cancel does NOT undo parsing, storage or billing. No retries or remote DELETE here.
 * The caller owns/wipes bytes (normally vault.load(...).use) and persists successful id mappings.
 * Scope one instance to one confirmed batch/credential revision; observe key mutation with close().
 */
internal class AiDocumentUpload(
    private val key: () -> String,
    private val keyStillCurrent: () -> Boolean,
    private val connection: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : AutoCloseable {
    private class Attempt(val size: Int, val fingerprint: ByteArray, var result: AiUploadedDocument? = null)
    private val attempts = mutableMapOf<String, Attempt>()
    private val closed = AtomicBoolean()
    private val active = AtomicReference<HttpsURLConnection?>()

    suspend fun upload(attachment: AiAttachment, bytes: ByteArray): AiUploadedDocument {
        checkAvailable { true }
        if (attachment.kind != AiAttachmentKind.DOCUMENT || attachment.mimeType !in AiAttachmentPolicy.DOCUMENT_MIMES ||
            bytes.size != attachment.size || bytes.size !in 1..AiAttachmentPolicy.MAX_DOCUMENT_BYTES)
            throw AiDocumentUploadFailure(AiDocumentUploadProblem.INVALID)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes)
        val prior = synchronized(attempts) {
            attempts[attachment.id]?.also {
                if (!MessageDigest.isEqual(it.fingerprint, fingerprint)) throw AiDocumentUploadFailure(AiDocumentUploadProblem.INVALID)
            }.also {
                if (it == null && (attempts.size >= AiAttachmentPolicy.MAX_COUNT ||
                    attempts.values.sumOf { attempt -> attempt.size.toLong() } + bytes.size > AiAttachmentPolicy.MAX_TOTAL_BYTES))
                    throw AiDocumentUploadFailure(AiDocumentUploadProblem.LIMIT)
            }
        }
        if (prior != null) {
            fingerprint.fill(0)
            val result = prior.result ?: throw AiDocumentUploadFailure(AiDocumentUploadProblem.UNKNOWN, true)
            if (result.expiresAt <= nowSeconds()) throw AiDocumentUploadFailure(AiDocumentUploadProblem.EXPIRED, true)
            return result
        }
        val boundary = "OpenHOYI" + UUID.randomUUID().toString().replace("-", "")
        // Generic ASCII filenames avoid exposing the user's local filename or header injection.
        val extension = when (attachment.mimeType) {
            "application/pdf" -> "pdf"
            "application/msword" -> "doc"
            "application/vnd.ms-powerpoint" -> "ppt"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            else -> "pptx"
        }
        val prefix = (field(boundary, "purpose", PURPOSE) +
            field(boundary, "expires_after[anchor]", "created_at") +
            field(boundary, "expires_after[seconds]", EXPIRY_SECONDS.toString()) +
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.$extension\"\r\n" +
            "Content-Type: ${attachment.mimeType}\r\n\r\n").toByteArray(Charsets.US_ASCII)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.US_ASCII)
        try {
            return withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { pending ->
                    var owner: HttpsURLConnection? = null
                    var started = false
                    val cancelled = AtomicBoolean()
                    val outcome: Result<AiUploadedDocument> = try {
                        checkAvailable { pending.isActive && !cancelled.get() }
                        val socket = connection(URL(ENDPOINT)).also { owner = it }
                        if (socket.url.toString() != ENDPOINT || !active.compareAndSet(null, socket))
                            throw AiDocumentUploadFailure(AiDocumentUploadProblem.INVALID)
                        pending.invokeOnCancellation { cancelled.set(true); close() }
                        socket.instanceFollowRedirects = false
                        socket.useCaches = false
                        socket.requestMethod = "POST"
                        socket.connectTimeout = 15000
                        // Parsing can take 120 seconds after the final upload byte, without a response.
                        socket.readTimeout = 150000
                        socket.doOutput = true
                        socket.setFixedLengthStreamingMode(prefix.size + bytes.size + suffix.size)
                        socket.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                        socket.setRequestProperty("Accept", "application/json")
                        socket.setRequestProperty("Connection", "close")
                        val credential = try { key() }
                            catch (cancelledFailure: CancellationException) { throw cancelledFailure }
                            catch (_: Exception) { throw AiDocumentUploadFailure(AiDocumentUploadProblem.KEY) }
                        if (!AiKeyCodec.valid(credential)) throw AiDocumentUploadFailure(AiDocumentUploadProblem.KEY)
                        checkAvailable { pending.isActive && !cancelled.get() }
                        socket.setRequestProperty("Authorization", "Bearer $credential")
                        synchronized(attempts) {
                            // Fail closed for repeat/concurrent calls, even when a previous outcome is unknown.
                            if (attempts.containsKey(attachment.id)) throw AiDocumentUploadFailure(AiDocumentUploadProblem.UNKNOWN, true)
                            if (attempts.size >= AiAttachmentPolicy.MAX_COUNT ||
                                attempts.values.sumOf { it.size.toLong() } + bytes.size > AiAttachmentPolicy.MAX_TOTAL_BYTES)
                                throw AiDocumentUploadFailure(AiDocumentUploadProblem.LIMIT)
                            attempts[attachment.id] = Attempt(bytes.size, fingerprint.copyOf())
                        }
                        val watchdog = CoroutineScope(pending.context).launch(Dispatchers.IO) {
                            delay(255000)
                            runCatching { socket.disconnect() }
                        }
                        try {
                            checkAvailable { pending.isActive && !cancelled.get() }
                            started = true // Even a connection/write failure may have reached the service.
                            socket.connect()
                            socket.outputStream.use { output ->
                                checkAvailable { pending.isActive && !cancelled.get() }
                                output.write(prefix)
                                var offset = 0
                                while (offset < bytes.size) {
                                    checkAvailable { pending.isActive && !cancelled.get() }
                                    val count = minOf(8192, bytes.size - offset)
                                    output.write(bytes, offset, count); offset += count
                                }
                                checkAvailable { pending.isActive && !cancelled.get() }
                                output.write(suffix)
                            }
                            checkAvailable { pending.isActive && !cancelled.get() }
                            val status = socket.responseCode
                            if (status !in 200..299) {
                                // Reading zero error bytes is deliberate: error text may echo keys or document content.
                                runCatching { socket.errorStream?.close() }
                                throw AiDocumentUploadFailure(when (status) {
                                    401, 403 -> AiDocumentUploadProblem.AUTH
                                    402, 429 -> AiDocumentUploadProblem.RATE_LIMIT
                                    400, 408, 413, 415, 422 -> AiDocumentUploadProblem.REJECTED
                                    else -> AiDocumentUploadProblem.UNKNOWN
                                }, status !in setOf(400, 401, 402, 403, 408, 413, 415, 422, 429))
                            }
                            val response = UploadBuffer()
                            val buffer = ByteArray(4096)
                            try {
                                socket.inputStream.use { input ->
                                    while (true) {
                                        checkAvailable { pending.isActive && !cancelled.get() }
                                        val count = input.read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_BYTES - response.size() + 1))
                                        if (count < 0) break
                                        if (count == 0 || response.size() + count > MAX_RESPONSE_BYTES)
                                            throw AiDocumentUploadFailure(AiDocumentUploadProblem.UNKNOWN, true)
                                        response.write(buffer, 0, count)
                                    }
                                }
                                checkAvailable { pending.isActive && !cancelled.get() }
                                val result = parse(response.toString(Charsets.UTF_8.name()), attachment)
                                checkAvailable { pending.isActive && !cancelled.get() }
                                synchronized(attempts) { attempts[attachment.id]?.result = result }
                                Result.success(result)
                            } finally { buffer.fill(0); response.wipe() }
                        } finally { watchdog.cancel() }
                    } catch (failure: AiDocumentUploadFailure) {
                        Result.failure(if (started && failure.problem == AiDocumentUploadProblem.KEY)
                            AiDocumentUploadFailure(failure.problem, true) else failure)
                    }
                    catch (cancelledFailure: CancellationException) { throw cancelledFailure }
                    catch (_: Exception) {
                        Result.failure(AiDocumentUploadFailure(if (!keyStillCurrent()) AiDocumentUploadProblem.KEY else AiDocumentUploadProblem.UNKNOWN, started))
                    } finally {
                        owner?.let { socket -> active.compareAndSet(socket, null); runCatching { socket.disconnect() } }
                    }
                    if (pending.isActive) outcome.fold(pending::resume, pending::resumeWithException)
                }
            }
        } finally { prefix.fill(0); suffix.fill(0); fingerprint.fill(0) }
    }

    private fun parse(raw: String, attachment: AiAttachment): AiUploadedDocument {
        try {
            val root = Json.parseToJsonElement(raw) as? JsonObject ?: error("Invalid")
            fun text(name: String) = (root[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            fun number(name: String) = (root[name] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            val created = number("created_at") ?: error("Invalid")
            val expiry = number("expires_at") ?: error("Invalid")
            check(text("object") == "file" && text("purpose") == PURPOSE && number("bytes") == attachment.size.toLong())
            check(created > 0 && created <= nowSeconds() + 300 && expiry > nowSeconds() && expiry - created == EXPIRY_SECONDS)
            return AiUploadedDocument(attachment.id, text("id") ?: error("Invalid"), expiry)
        } catch (_: Exception) { throw AiDocumentUploadFailure(AiDocumentUploadProblem.UNKNOWN, true) }
    }
    private fun checkAvailable(pending: () -> Boolean) {
        if (!keyStillCurrent()) throw AiDocumentUploadFailure(AiDocumentUploadProblem.KEY)
        if (closed.get() || !pending()) throw CancellationException()
    }
    override fun close() {
        closed.set(true)
        active.getAndSet(null)?.let { runCatching { it.disconnect() } }
    }
    private class UploadBuffer : ByteArrayOutputStream() { fun wipe() { buf.fill(0); reset() } }
    companion object {
        const val ENDPOINT = "https://api.deepseek.com/files"
        const val PURPOSE = "rich_layout_recognition"
        const val EXPIRY_SECONDS = 86400L
        const val MAX_RESPONSE_BYTES = 16384
        private fun field(boundary: String, name: String, value: String) =
            "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n"
    }
}
