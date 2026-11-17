package io.openhoyi.mobile

import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException

internal open class FakeAiConnection(val code: Int = 200, val answer: String = aiAnswer("ok"), url: URL = URL(AiSafeTransport.ENDPOINT)) : HttpsURLConnection(url) {
    val sent = ByteArrayOutputStream()
    val disconnected = AtomicBoolean()
    var errorRead = false
    var responseStream: InputStream? = null
    val requestHeaders = mutableMapOf<String, String>()
    override fun setRequestProperty(key: String, value: String) { requestHeaders[key] = value }
    override fun getOutputStream() = sent
    override fun getInputStream(): InputStream = responseStream ?: ByteArrayInputStream(
        (if (requestHeaders["Accept"] == "text/event-stream") aiSseFixture(answer) else answer).toByteArray())
    override fun getResponseCode() = code
    override fun getErrorStream(): InputStream = object : ByteArrayInputStream(answer.toByteArray()) {
        override fun read(): Int { errorRead = true; return super.read() }
        override fun read(b: ByteArray, off: Int, len: Int): Int { errorRead = true; return super.read(b, off, len) }
    }
    override fun connect() = Unit
    override fun disconnect() { disconnected.set(true); responseStream?.close() }
    override fun usingProxy() = false
    override fun getCipherSuite() = "fake"
    override fun getLocalCertificates(): Array<Certificate>? = null
    override fun getServerCertificates(): Array<Certificate> = throw SSLPeerUnverifiedException("fake")
}

internal fun aiAnswer(text: String): String = """{"id":"fake","system_fingerprint":"fake","object":"chat.completion","created":1,"model":"deepseek-flash","choices":[{"index":0,"message":{"role":"assistant","content":${kotlinx.serialization.json.JsonPrimitive(text)}},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}"""

/** Existing JSON policy fixtures travel over the actual streaming wire contract. */
internal fun aiSseFixture(answer: String): String = try {
    val root = kotlinx.serialization.json.Json.parseToJsonElement(answer).jsonObject
    val choices = root["choices"]!!.jsonArray.map { value ->
        val choice = value.jsonObject
        val message = choice.getValue("message").jsonObject
        val tools = (message["tool_calls"] as? JsonArray)?.mapIndexed { index, tool ->
            JsonObject(tool.jsonObject + ("index" to JsonPrimitive(index)))
        }
        val delta = if (tools == null) message else JsonObject(message + ("tool_calls" to JsonArray(tools)))
        kotlinx.serialization.json.JsonObject(choice - "message" + ("delta" to delta))
    }
    "data: " + kotlinx.serialization.json.JsonObject(root + ("choices" to kotlinx.serialization.json.JsonArray(choices))) + "\n\ndata: [DONE]\n\n"
} catch (_: Exception) { answer }

class AiSafeTransportTest {
    private val key = "fake-sentinel-key-never-real"
    private suspend fun send(t: AiSafeTransport, body: String = "{}") = t.post("chat/completions", body, String::class, String::class)
    @Test fun streamingRequestPreservesPostAndProgressCallbackCannotBreakIt() = runBlocking {
        val c = FakeAiConnection()
        var progress = 0
        val transport = AiSafeTransport({ key }, { true }, AiRequestBudget(), { c },
            onProgress = { progress++; throw IllegalStateException("not logged") })
        assertEquals(Json.parseToJsonElement(aiAnswer("ok")), Json.parseToJsonElement(send(transport)))
        assertEquals(1, progress)
        assertEquals("text/event-stream", c.requestHeaders["Accept"])
        assertEquals(JsonPrimitive(true), Json.parseToJsonElement(c.sent.toString("UTF-8")).jsonObject["stream"])
        assertTrue(c.disconnected.get())
    }
    @Test fun onlyFixedHttpsPostAndNoRedirects() = runBlocking {
        val c = FakeAiConnection(302, key)
        var opens = 0
        val transport = AiSafeTransport({ key }, { true }, AiRequestBudget(), { u -> assertEquals(AiSafeTransport.ENDPOINT, u.toString()); opens++; c })
        val failure = runCatching { send(transport) }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.NETWORK, failure.problem); assertNull(failure.cause)
        assertEquals(1, opens); assertFalse(c.instanceFollowRedirects); assertFalse(c.errorRead)
        assertEquals("Bearer $key", c.requestHeaders["Authorization"])
        assertTrue(c.disconnected.get())
        assertTrue(runCatching { transport.post("https://other.invalid", "{}", String::class, String::class) }.isFailure)
        assertEquals(1, opens)
    }
    @Test fun authenticationFailureNeverReadsOrSurfacesErrorBody() = runBlocking {
        val c = FakeAiConnection(401, key + " PRIVATE_USER_SENTINEL")
        val transport = AiSafeTransport({ key }, { true }, AiRequestBudget(), { c })
        val failure = runCatching { send(transport) }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.AUTH, failure.problem); assertEquals("AUTH", failure.message); assertNull(failure.cause)
        assertFalse(c.errorRead); assertFalse(failure.toString().contains(key))
    }
    @Test fun limitsAndKeyMutationStopBeforeAnotherRequest() = runBlocking {
        var keyReads = 0; var current = true; var opened = 0
        val t = AiSafeTransport({ keyReads++; key }, { current }, AiRequestBudget(), { opened++; FakeAiConnection(answer = aiAnswer("ok")) })
        assertEquals(Json.parseToJsonElement(aiAnswer("ok")), Json.parseToJsonElement(send(t)))
        current = false
        assertEquals(AiProblem.KEY, (runCatching { send(t) }.exceptionOrNull() as AiFailure).problem)
        assertEquals(1, keyReads); assertEquals(1, opened)
        val large = AiSafeTransport({ keyReads++; key }, { true }, AiRequestBudget(), { FakeAiConnection(answer = "x".repeat(100)) }, maxResponseBytes = 10)
        assertEquals(AiProblem.RESPONSE_LIMIT, (runCatching { send(large) }.exceptionOrNull() as AiFailure).problem)
        val before = keyReads
        assertEquals(AiProblem.REQUEST_LIMIT, (runCatching { send(t, "x".repeat(AiContextPolicy.REQUEST_BYTES + 1)) }.exceptionOrNull() as AiFailure).problem)
        assertEquals(before, keyReads)
    }
    @Test fun replaceOrDeleteDuringRequestDisconnectsAndInvalidatesCachedRevision() = runBlocking {
        for (replace in listOf(true, false)) {
            var sealed: String? = null
            val storage = object : AiCiphertextStorage {
                override fun contains() = sealed != null
                override fun read() = sealed
                override fun write(ciphertext: String?): Boolean { sealed = ciphertext; return true }
            }
            var material: javax.crypto.SecretKey? = null
            val source = object : AiKeySource {
                override fun existing() = material
                override fun create() = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { material = it }
            }
            val store = AiKeyStore(storage, source)
            assertTrue(store.save(key)); val revision = store.revision()
            val began = CountDownLatch(1); val released = CountDownLatch(1)
            val c = FakeAiConnection().apply { responseStream = object : InputStream() {
                override fun read(): Int { began.countDown(); released.await(2, TimeUnit.SECONDS); return -1 }
                override fun close() { released.countDown() }
            } }
            var reads = 0
            val t = AiSafeTransport({ reads++; store.readForRequest(revision) }, { store.revision() == revision }, AiRequestBudget(), { c })
            val stopObserving = store.observeMutation(t::close)
            try {
                val request = async(Dispatchers.Default) { runCatching { send(t) }.exceptionOrNull() }
                withTimeout(1000) { while (began.count > 0) delay(1) }
                if (replace) assertTrue(store.save("fake-new-key-never-real")) else assertTrue(store.delete())
                assertEquals(AiProblem.KEY, (withTimeout(1000) { request.await() } as AiFailure).problem)
                assertTrue(c.disconnected.get())
                assertEquals(AiProblem.KEY, (runCatching { send(t) }.exceptionOrNull() as AiFailure).problem)
                assertEquals(1, reads)
            } finally { stopObserving(); t.close(); store.delete() }
        }
    }
    @Test fun cancellationDisconnectsBlockedRead() = runBlocking {
        val began = CountDownLatch(1); val released = CountDownLatch(1)
        val c = FakeAiConnection().apply { responseStream = object : InputStream() {
            override fun read(): Int { began.countDown(); released.await(2, TimeUnit.SECONDS); return -1 }
            override fun close() { released.countDown() }
        } }
        val t = AiSafeTransport({ key }, { true }, AiRequestBudget(), { c })
        val request = async(Dispatchers.Default) { send(t) }
        withTimeout(1000) { while (began.count > 0) delay(1) }
        withTimeout(1000) { request.cancelAndJoin() }
        assertTrue(c.disconnected.get())
    }
}
