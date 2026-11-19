package io.openhoyi.mobile

import ai.koog.http.client.KoogHttpClient
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.ConnectException
import javax.net.ssl.SSLException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KClass

internal class AiRequestBudget {
    private val requests = AtomicInteger()
    private val searches = AtomicInteger()
    private val tools = AtomicInteger()
    private val toolBytes = AtomicInteger()
    // Koog may turn a thrown tool exception into a tool-result message. Latch hard
    // exhaustion so the subsequent model request cannot spend again after that error.
    private val terminal = AtomicReference<AiFailure?>()
    private fun checkTerminal() { terminal.get()?.let { throw it } }
    private val diagnostics = AiDiagnostics()
    // Per visible reply, not a global quota. Search and local-tool caps remain independent.
    fun request() { checkTerminal(); if (requests.incrementAndGet() > MAX_MODEL_REQUESTS) exhausted(AiFailureKind.MODEL_REQUEST_BUDGET) }
    fun search() { checkTerminal(); if (searches.incrementAndGet() > AiChatPolicy.SEARCH_CALLS) exhausted(AiFailureKind.SEARCH_BUDGET) }
    fun tool() { checkTerminal(); if (tools.incrementAndGet() > AiChatPolicy.TOOL_CALLS) exhausted(AiFailureKind.TOOL_CALL_BUDGET) }
    fun toolResult(value: String): String {
        checkTerminal()
        // Tools already paginate/validate structured data. Do not impose a second tiny
        // character quota that aborts normal multi-page research. Bound retained tool bytes
        // using the transport ceiling; this is not the full wire size. The complete
        // protocol/transport still checks actual context and serialized request bytes.
        val max = AiContextPolicy.REQUEST_BYTES
        if (value.length > max) resultTooLarge()
        val bytes = value.toByteArray(Charsets.UTF_8).size
        if (bytes > max || toolBytes.addAndGet(bytes) > max) resultTooLarge()
        return value // Never truncate JSON, recipe fields or source evidence.
    }
    private fun resultTooLarge(): Nothing {
        diagnostics.stage = AiStage.BUDGET
        val failure = AiFailure(AiProblem.REQUEST_LIMIT, diagnostics.failure(AiFailureKind.REQUEST_SIZE_LIMIT, status = null))
        terminal.compareAndSet(null, failure)
        throw requireNotNull(terminal.get())
    }
    private fun exhausted(kind: AiFailureKind): Nothing {
        diagnostics.stage = AiStage.BUDGET
        val failure = AiFailure(AiProblem.TOOL_LIMIT, diagnostics.failure(kind, status = null))
        // Search exhaustion is deliberately recoverable; it returns a bounded tool
        // status and allows the model to answer using already gathered evidence.
        if (kind != AiFailureKind.SEARCH_BUDGET) {
            terminal.compareAndSet(null, failure)
            throw requireNotNull(terminal.get())
        }
        throw failure
    }
    companion object { const val MAX_MODEL_REQUESTS = AiChatPolicy.MODEL_REQUESTS }
}

/** Dedicated POST-only adapter. The SDK never receives the key, an arbitrary URL or a raw HTTP error. */
internal class AiSafeTransport(
    private val key: () -> String,
    private val keyStillCurrent: () -> Boolean,
    private val budget: AiRequestBudget,
    private val connection: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
    private val maxResponseBytes: Int = AiContextPolicy.RESPONSE_BYTES,
    private val diagnostics: AiDiagnostics = AiDiagnostics(),
    private val errorReadTimeoutMillis: Long = 2000,
    private val usage: AiUsageRecorder = AiUsageRecorder(emptyList()),
    private val retryState: AiRetryState? = null,
    private val onRetry: (AiRetryProgress?) -> Unit = {},
    private val onProgress: (AiStreamDelta) -> Unit = {},
    private val sendPermit: () -> Boolean = { true },
) : KoogHttpClient {
    init { require(errorReadTimeoutMillis in 1..2000) }
    override val clientName = "OpenHOYI-DeepSeek"
    private val closed = AtomicBoolean()
    private val closeSignal = CompletableDeferred<Unit>()
    private val active = AtomicReference<HttpsURLConnection?>()

    override suspend fun <T : Any, R : Any> post(path: String, requestBody: T, requestBodyType: KClass<T>,
        responseType: KClass<R>, parameters: Map<String, String>, headers: Map<String, String>): R {
        diagnostics.httpStatus = null
        diagnostics.stage = AiStage.TRANSPORT
        if (path != "chat/completions" || requestBody !is String || requestBodyType != String::class ||
            responseType != String::class || parameters.isNotEmpty() || headers.isNotEmpty()) throw AiFailure(AiProblem.NETWORK, diagnostics.failure(AiFailureKind.TRANSPORT_CONTRACT))
        return send(requestBody, false) as R
    }

    suspend fun search(query: String): String {
        val body = AiWebSearch.request(query) ?: throw AiFailure(AiProblem.TOOL_LIMIT)
        return send(body, true)
    }

    private fun requestedModel(raw: String): String = try {
        val model = Json.parseToJsonElement(raw).jsonObject["model"]?.jsonPrimitive?.content
        AiUsagePolicy.model(model) ?: "unknown"
    } catch (_: Exception) { "unknown" }
    private suspend fun send(requestBody: String, search: Boolean): String {
        diagnostics.httpStatus = null
        diagnostics.stage = AiStage.TRANSPORT
        val endpoint = if (search) SEARCH_ENDPOINT else ENDPOINT
        var bytes = requestBody.toByteArray(Charsets.UTF_8)
        if (bytes.size > AiContextPolicy.REQUEST_BYTES) { bytes.fill(0); throw AiFailure(AiProblem.REQUEST_LIMIT) }
        val corrected = if (search) requestBody else AiSseCompletion.request(AiRequestContract.forDeepSeek(requestBody))
        if (corrected != requestBody) { bytes.fill(0); bytes = corrected.toByteArray(Charsets.UTF_8) }
        if (bytes.size > AiContextPolicy.REQUEST_BYTES) { bytes.fill(0); throw AiFailure(AiProblem.REQUEST_LIMIT) }
        try { if (search) budget.search() else budget.request() } catch (failure: AiFailure) { bytes.fill(0); throw failure }
        var usageId: String? = null
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                checkAvailable { true }
                diagnostics.httpStatus = null
                diagnostics.stage = AiStage.TRANSPORT
                try {
                    return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<String> { pending ->
                var owner: HttpsURLConnection? = null
                val cancelled = AtomicBoolean()
                val writeBoundary = AtomicBoolean()
                var localStage = AiStage.TRANSPORT
                fun stage(next: AiStage) { localStage = next; diagnostics.stage = next }
                val answer: Result<String> = try {
                    checkAvailable { pending.isActive && !cancelled.get() }
                    val socket = connection(URL(endpoint)).also { owner = it }
                    if (socket.url.toString() != endpoint || !active.compareAndSet(null, socket)) throw AiFailure(AiProblem.NETWORK)
                    pending.invokeOnCancellation { cancelled.set(true); close() }
                    checkAvailable { pending.isActive && !cancelled.get() }
                    socket.instanceFollowRedirects = false
                    socket.useCaches = false
                    socket.requestMethod = "POST"
                    socket.connectTimeout = 15000
                    socket.readTimeout = 30000
                    socket.doOutput = true
                    socket.setFixedLengthStreamingMode(bytes.size)
                    socket.setRequestProperty("Content-Type", "application/json")
                    socket.setRequestProperty("Accept", if (search) "application/json" else "text/event-stream")
                    socket.setRequestProperty("Connection", "close")
                    // A fresh short-lived value is obtained only at the final send boundary.
                    val credential = key()
                    if (!AiKeyCodec.valid(credential)) throw AiFailure(AiProblem.KEY)
                    checkAvailable { pending.isActive && !cancelled.get() }
                    // Linearize optional background consent before adding credentials or
                    // contacting the provider. Do not hold any caller lock across socket I/O.
                    if (!sendPermit()) throw AiFailure(AiProblem.KEY)
                    checkAvailable { pending.isActive && !cancelled.get() }
                    socket.setRequestProperty("Authorization", "Bearer $credential")
                    stage(AiStage.CONNECT)
                    if (usageId == null) usageId = usage.begin(if (search) AiUsageKind.SEARCH else AiUsageKind.CHAT, if (search) "unknown" else requestedModel(corrected))
                    checkAvailable { pending.isActive && !cancelled.get() }
                    socket.connect()
                    checkAvailable { pending.isActive && !cancelled.get() }
                    stage(AiStage.WRITE)
                    writeBoundary.set(true) // Before the getter: obtaining an output stream can already send headers.
                    notifyRetry(null)
                    socket.outputStream.use { output ->
                        checkAvailable { pending.isActive && !cancelled.get() }
                        output.write(bytes)
                    }
                    checkAvailable { pending.isActive && !cancelled.get() }
                    stage(AiStage.RESPONSE_HEADERS)
                    val status = socket.responseCode
                    diagnostics.httpStatus = status.takeIf { it in 100..599 }
                    if (status !in 200..299) {
                        usageId?.let { usage.finish(it, search = AiSearchOutcome.FAILED) }
                        // Only HTTP400 receives bounded, closed-vocabulary classification.
                        // Nothing from the body, responseMessage or Location reaches SDK/UI/logs.
                        val provider = if (status == 400) {
                            val expired = AtomicBoolean()
                            val watchdog = CoroutineScope(pending.context).launch(Dispatchers.IO) {
                                delay(errorReadTimeoutMillis)
                                expired.set(true)
                                runCatching { socket.disconnect() }
                            }
                            try {
                                val classified = readBadRequest(socket) { checkAvailable { pending.isActive && !cancelled.get() } }
                                if (expired.get()) AiProviderDiagnostic(AiProviderReason.UNAVAILABLE) else classified
                            } finally { watchdog.cancel() }
                        } else { runCatching { socket.errorStream?.close() }; null }
                        checkAvailable { pending.isActive && !cancelled.get() }
                        throw AiFailure(when (status) { 401, 403 -> AiProblem.AUTH; 402, 429 -> AiProblem.RATE_LIMIT; else -> AiProblem.NETWORK }, diagnostics.failure(AiFailureKind.HTTP, status, provider))
                    }
                    stage(AiStage.RESPONSE_BODY)
                    val rawResponse = socket.inputStream.use { input ->
                        if (!search) AiSseCompletion.read(input.buffered(4096), maxResponseBytes,
                            check = { checkAvailable { pending.isActive && !cancelled.get() } },
                            emit = { delta ->
                                checkAvailable { pending.isActive && !cancelled.get() }
                                try { onProgress(delta) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { }
                            }, diagnostics = diagnostics, onUsage = { reported -> usageId?.let { usage.finish(it, reported = reported) } })
                        else {
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            while (true) {
                                checkAvailable { pending.isActive && !cancelled.get() }
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > maxResponseBytes) throw AiFailure(AiProblem.RESPONSE_LIMIT)
                                output.write(buffer, 0, count)
                            }
                            output.toString(Charsets.UTF_8.name())
                        }
                    }
                    usageId?.let { usage.finish(it, reported = if (search) null else AiUsagePolicy.reported(rawResponse),
                        search = if (search) AiUsagePolicy.searchOutcome(rawResponse) else AiSearchOutcome.UNKNOWN) }
                    checkAvailable { pending.isActive && !cancelled.get() }
                    if (rawResponse.isEmpty()) throw AiFailure(AiProblem.EMPTY)
                    stage(AiStage.VALIDATE)
                    val response = rawResponse.also { if (!search) AiResponsePolicy.validate(it, diagnostics) }
                    stage(AiStage.SDK_RESPONSE)
                    Result.success(response)
                } catch (failure: AiFailure) { Result.failure(if (failure.diagnostic == null && diagnostics.stage == AiStage.VALIDATE)
                    AiFailure(failure.problem, diagnostics.failure(AiFailureKind.RESPONSE_POLICY)) else failure) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    val kind = when (failure) {
                        is UnknownHostException -> AiFailureKind.DNS
                        is SSLException -> AiFailureKind.TLS
                        is SocketTimeoutException -> when (localStage) {
                            AiStage.CONNECT -> AiFailureKind.CONNECT_TIMEOUT
                            AiStage.RESPONSE_HEADERS, AiStage.RESPONSE_BODY -> AiFailureKind.READ_TIMEOUT
                            else -> AiFailureKind.IO_TIMEOUT
                        }
                        is ConnectException -> AiFailureKind.CONNECT
                        is java.io.IOException -> AiFailureKind.IO
                        else -> AiFailureKind.SDK
                    }
                    val retryKind = AiRetryPolicy.connectionFailure(failure, localStage, writeBoundary.get())
                    Result.failure(if (!keyStillCurrent()) AiFailure(AiProblem.KEY, diagnostics.failure(kind))
                        else if (retryKind != null) UnsentConnectionFailure(retryKind, diagnostics.failure(kind))
                        else AiFailure(AiProblem.NETWORK, diagnostics.failure(kind)))
                }
                finally {
                    owner?.let { socket -> active.compareAndSet(socket, null); runCatching { socket.disconnect() } }
                }
                if (pending.isActive) answer.fold(pending::resume, pending::resumeWithException)
            }
                    }
                } catch (failure: UnsentConnectionFailure) {
                    currentCoroutineContext().ensureActive()
                    checkAvailable { true }
                    val retries = retryState ?: throw failure
                    val progress = retries.reserve(failure.retryKind) ?: throw failure
                    notifyRetry(progress)
                    awaitRetry(retries, progress)
                    currentCoroutineContext().ensureActive()
                    checkAvailable { true }
                    notifyRetry(progress.copy(waitMillis = 0))
                }
            }
        } finally {
            try { usageId?.let { usage.finish(it) } }
            finally { bytes.fill(0); notifyRetry(null) }
        }
    }
    private suspend fun awaitRetry(retries: AiRetryState, progress: AiRetryProgress) = coroutineScope {
        val timer = async { retries.await(progress) }
        try {
            select<Unit> {
                timer.onAwait { }
                closeSignal.onAwait { checkAvailable { false } }
            }
        } finally { timer.cancel() }
    }
    private class UnsentConnectionFailure(val retryKind: AiFailureKind, diagnostic: AiDiagnostic) : AiFailure(AiProblem.NETWORK, diagnostic)
    private fun notifyRetry(progress: AiRetryProgress?) { try { onRetry(progress) } catch (_: Exception) { } }

    private fun readBadRequest(socket: HttpsURLConnection, check: () -> Unit): AiProviderDiagnostic {
        val deadline = System.nanoTime() + errorReadTimeoutMillis * 1_000_000L
        return try {
            check()
            socket.readTimeout = errorReadTimeoutMillis.toInt()
            socket.errorStream?.use { input ->
                AiHttpErrorPolicy.read(input) {
                    check()
                    val remaining = deadline - System.nanoTime()
                    if (remaining <= 0) throw java.net.SocketTimeoutException()
                    socket.readTimeout = (remaining / 1_000_000).coerceIn(1, errorReadTimeoutMillis).toInt()
                }
            } ?: AiProviderDiagnostic(AiProviderReason.UNAVAILABLE)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: AiFailure) { throw failure }
        catch (_: Exception) { AiProviderDiagnostic(AiProviderReason.UNAVAILABLE) }
    }

    private fun checkAvailable(pending: () -> Boolean) {
        if (!keyStillCurrent()) throw AiFailure(AiProblem.KEY)
        if (!pending() || closed.get()) throw CancellationException()
    }
    override fun close() { closed.set(true); closeSignal.complete(Unit); active.getAndSet(null)?.let { runCatching { it.disconnect() } } }
    override suspend fun <R : Any> get(path: String, responseType: KClass<R>, parameters: Map<String, String>, headers: Map<String, String>): R =
        throw AiFailure(AiProblem.NETWORK)
    override fun <T : Any, R : Any, O : Any> sse(path: String, requestBody: T, requestBodyType: KClass<T>, dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R, processStreamingChunk: (R) -> O?, parameters: Map<String, String>, headers: Map<String, String>): Flow<O> =
        flow { throw AiFailure(AiProblem.NETWORK) }
    override fun <T : Any> lines(path: String, requestBody: T, requestBodyType: KClass<T>, parameters: Map<String, String>, headers: Map<String, String>): Flow<String> =
        flow { throw AiFailure(AiProblem.NETWORK) }
    internal companion object {
        const val SEARCH_ENDPOINT = "https://api.deepseek.com/v1/tools/search"
        const val ENDPOINT = "https://api.deepseek.com/chat/completions" }
}
