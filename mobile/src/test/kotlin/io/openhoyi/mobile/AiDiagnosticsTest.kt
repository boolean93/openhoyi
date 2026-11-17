package io.openhoyi.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class AiDiagnosticsTest {
    private val sentinel = "fake-key-PRIVATE_SENTINEL"
    private suspend fun failure(connection: FakeAiConnection? = null, open: (() -> FakeAiConnection)? = null): AiFailure {
        val transport = AiSafeTransport({ sentinel }, { true }, AiRequestBudget(), { open?.invoke() ?: connection!! })
        return runCatching { transport.post("chat/completions", """{"model":"deepseek-flash","messages":[{"role":"user","content":"PRIVATE_BODY"}]}""", String::class, String::class) }.exceptionOrNull() as AiFailure
    }
    private fun verify(f: AiFailure, kind: AiFailureKind, stage: AiStage, status: Int? = null) {
        val d = requireNotNull(f.diagnostic)
        assertEquals(kind, d.kind); assertEquals(stage, d.stage); assertEquals(status, d.httpStatus)
        assertTrue(d.elapsedMillis >= 0); assertNull(f.cause)
        assertFalse(d.label().contains(sentinel)); assertFalse(f.toString().contains("PRIVATE"))
    }
    @Test fun httpFailuresAreNotTimeoutsAndNeverReadErrorBodies() = runBlocking {
        for (code in listOf(302, 400, 401, 402, 403, 429, 500, 503)) {
            val c = FakeAiConnection(code, sentinel)
            verify(failure(c), AiFailureKind.HTTP, AiStage.RESPONSE_HEADERS, code)
            assertEquals(code == 400, c.errorRead); assertTrue(c.disconnected.get())
        }
    }
    @Test fun dnsTlsAndConnectAreTypeMappedWithoutExceptionText() = runBlocking {
        for ((error, kind) in listOf(UnknownHostException(sentinel) to AiFailureKind.DNS,
            SSLException(sentinel) to AiFailureKind.TLS, ConnectException(sentinel) to AiFailureKind.CONNECT)) {
            verify(failure(open = { throw error }), kind, AiStage.TRANSPORT)
        }
    }
    @Test fun connectionAndReadTimeoutsHaveDistinctStages() = runBlocking {
        val connecting = object : FakeAiConnection() { override fun connect(): Unit = throw SocketTimeoutException(sentinel) }
        verify(failure(connecting), AiFailureKind.CONNECT_TIMEOUT, AiStage.CONNECT)
        val reading = FakeAiConnection().apply { responseStream = object : InputStream() {
            override fun read(): Int = throw SocketTimeoutException(sentinel)
        } }
        verify(failure(reading), AiFailureKind.READ_TIMEOUT, AiStage.RESPONSE_BODY, 200)
        assertTrue(reading.disconnected.get())
    }
    @Test fun malformedSseEnvelopeHasProtocolDiagnosticWithoutPayload() = runBlocking {
        verify(failure(FakeAiConnection(answer = sentinel)), AiFailureKind.PROTOCOL_FORMAT, AiStage.PROTOCOL, 200)
    }
    @Test fun boundedMonotonicDiagnosticContainsOnlyClosedFields() {
        var clock = 5_000_000L
        val diagnostics = AiDiagnostics { clock }
        clock += 7_000_000L
        assertEquals("SDK/SDK_REQUEST/7ms", diagnostics.failure(AiFailureKind.SDK, 999).label())
    }
    @Test fun realSdkDecodeFailureAndTotalTimeoutStayDistinct() = runBlocking {
        val data = object : AiReadOnlyData {
            override fun currentStatus() = "{}"
            override fun curves(query: String, offset: Int, limit: Int) = "{}"
            override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
        }
        val diagnostic = AiDiagnostics(); val budget = AiRequestBudget()
        // Valid boundary envelope, invalid SDK schema (created must be numeric).
        val badSchema = aiAnswer("ok").replace("\"created\":1", "\"created\":\"PRIVATE_SENTINEL\"")
        val transport = AiSafeTransport({ sentinel }, { true }, budget, { FakeAiConnection(answer = badSchema) }, diagnostics = diagnostic)
        val parsed = runCatching { AiChatSession(data, transport, budget, Dispatchers.Unconfined, diagnostics = diagnostic).reply(emptyList(), "test") }.exceptionOrNull() as AiFailure
        verify(parsed, AiFailureKind.PARSE, AiStage.SDK_RESPONSE, 200)
        val timed = runCatching { AiChatSession(data, transport, AiRequestBudget(), Dispatchers.Unconfined, timeoutMillis = 0).reply(emptyList(), "test") }.exceptionOrNull() as AiFailure
        verify(timed, AiFailureKind.TOTAL_TIMEOUT, AiStage.SDK_REQUEST)
    }
}
