package io.openhoyi.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AiHttpErrorPolicyTest {
    private val privateText = "PRIVATE_KEY_USER_PROVIDER_SENTINEL"
    private fun read(body: String) = AiHttpErrorPolicy.read(ByteArrayInputStream(body.toByteArray())) {}
    @Test fun onlyExactOfficialTypesAndParametersBecomeEnums() {
        val cases = listOf(
            """{"error":{"message":"Content Exists Risk","type":"invalid_request_error"}}""" to AiProviderReason.CONTENT_RISK,
            """{"error":{"type":"invalid_request_error","message":"$privateText","param":"tools"}}""" to AiProviderReason.INVALID_REQUEST_ERROR,
            """{"error":{"code":"invalid_request_error","param":"stream_options"}}""" to AiProviderReason.INVALID_REQUEST_ERROR,
            """{"error":{"message":"Content Exists Risk $privateText","type":"$privateText","param":"$privateText"}}""" to AiProviderReason.UNKNOWN,
            """{"error":{"message":"$privateText","request_id":"$privateText"}}""" to AiProviderReason.UNKNOWN,
        )
        for ((body, reason) in cases) {
            val result = read(body)
            assertEquals(reason, result.reason)
            assertFalse(result.toString().contains(privateText)); assertFalse(result.label().contains(privateText))
        }
        assertEquals(AiProviderParameter.TOOLS, read(cases[1].first).parameter)
        assertEquals(AiProviderParameter.STREAM_OPTIONS, read(cases[2].first).parameter)
        assertNull(read(cases[3].first).parameter)
    }
    @Test fun completeOfficialMessagesOnlyMapToClosedReasons() {
        val examples = listOf(
            "tools: other tools cannot be used together with `deepseek_web_search`, `deepseek_read_url`, `safety_check` or `title` yet" to AiProviderReason.TOOLS_FEATURE_CONFLICT,
        )
        fun envelope(message: String) = buildJsonObject { put("error", buildJsonObject {
            put("type", "invalid_request_error"); put("code", "invalid_request_error")
            put("message", message); put("param", privateText); put("request_id", privateText)
        }) }.toString()
        for ((message, expected) in examples) {
            val actual = read(envelope(message))
            assertEquals(expected, actual.reason); assertNull(actual.parameter)
            assertFalse(actual.label().contains(privateText)); assertFalse(actual.toString().contains(message))
            // Never classify an echoed quote, truncated message or appended private text as this template.
            for (changed in listOf(privateText + " " + message, message + " " + privateText, message.dropLast(1)))
                assertEquals(AiProviderReason.INVALID_REQUEST_ERROR, read(envelope(changed)).reason)
        }
        assertEquals(AiProviderReason.INVALID_REQUEST_ERROR, read(envelope("model tools safety_check max_tokens " + privateText)).reason)
    }
    @Test fun malformedInvalidUtf8OversizedAndDeepBodiesStayBounded() {
        assertEquals(AiProviderReason.MALFORMED, read(privateText).reason)
        assertEquals(AiProviderReason.UNKNOWN, read("{}").reason)
        assertEquals(AiProviderReason.MALFORMED, read("[]").reason)
        assertEquals(AiProviderReason.MALFORMED, read("[".repeat(17) + "]".repeat(17)).reason)
        assertEquals(AiProviderReason.MALFORMED, AiHttpErrorPolicy.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))) {}.reason)
        var consumed = 0
        val infinite = object : InputStream() {
            override fun read(): Int { consumed++; return 120 }
            override fun read(bytes: ByteArray, off: Int, len: Int): Int { consumed += len; bytes.fill(120, off, off + len); return len }
        }
        assertEquals(AiProviderReason.TOO_LARGE, AiHttpErrorPolicy.read(infinite) {}.reason)
        assertEquals(4097, consumed)
        assertFalse(read("""{"error":{"message":"$privateText { [ \\\""}}""").label().contains(privateText))
    }
    @Test fun transportPreserves400WhenClassifierTimesOutAndDoesNotExposeCauses() = runBlocking {
        val c = object : FakeAiConnection(400) {
            override fun getErrorStream() = object : InputStream() { override fun read(): Int = throw SocketTimeoutException(privateText) }
        }
        val transport = AiSafeTransport({ "fake-only-key" }, { true }, AiRequestBudget(), { c })
        val error = runCatching { transport.post("chat/completions", "{}", String::class, String::class) }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.NETWORK, error.problem)
        assertEquals(400, error.diagnostic?.httpStatus)
        assertEquals(AiProviderReason.UNAVAILABLE, error.diagnostic?.provider?.reason)
        assertNull(error.cause); assertFalse(error.toString().contains(privateText)); assertTrue(c.disconnected.get())
    }
    @Test fun watchdogDisconnectsBlockedErrorReadAndIsCancelledAfterFastRead() = runBlocking {
        val released = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int { released.await(5, TimeUnit.SECONDS); return -1 }
            override fun close() { released.countDown() }
        }
        val c = object : FakeAiConnection(400) {
            override fun getErrorStream() = stream
            override fun disconnect() { super.disconnect(); stream.close() }
        }
        val transport = AiSafeTransport({ "fake-only-key" }, { true }, AiRequestBudget(), { c }, errorReadTimeoutMillis = 50)
        val failure = withTimeout(1000) { runCatching { transport.post("chat/completions", "{}", String::class, String::class) }.exceptionOrNull() } as AiFailure
        assertEquals(400, failure.diagnostic?.httpStatus)
        assertEquals(AiProviderReason.UNAVAILABLE, failure.diagnostic?.provider?.reason)
        assertEquals(0, released.count)
        var disconnects = 0
        val fast = object : FakeAiConnection(400, "{}") {
            override fun disconnect() { disconnects++; super.disconnect() }
        }
        val immediate = AiSafeTransport({ "fake-only-key" }, { true }, AiRequestBudget(), { fast }, errorReadTimeoutMillis = 50)
        runCatching { immediate.post("chat/completions", "{}", String::class, String::class) }
        assertEquals(1, disconnects)
        delay(100)
        assertEquals(1, disconnects) // No late watchdog touches a completed request.
    }
    @Test fun cancellationDuring400ReadStillDisconnects() = runBlocking {
        val began = CountDownLatch(1); val released = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int { began.countDown(); released.await(2, TimeUnit.SECONDS); return -1 }
            override fun close() { released.countDown() }
        }
        val c = object : FakeAiConnection(400) {
            override fun getErrorStream() = stream
            override fun disconnect() { super.disconnect(); stream.close() }
        }
        val transport = AiSafeTransport({ "fake-only-key" }, { true }, AiRequestBudget(), { c })
        val job = async(Dispatchers.Default) { transport.post("chat/completions", "{}", String::class, String::class) }
        withTimeout(1000) { while (began.count > 0) delay(1) }
        withTimeout(1000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled); assertTrue(c.disconnected.get())
    }
}
