package io.openhoyi.mobile

import kotlinx.serialization.json.Json

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class AiRetryTest {
    private val key = "synthetic-retry-key-never-real"
    private val body = "{\"model\":\"deepseek-flash\",\"messages\":[{\"role\":\"user\",\"content\":\"SYNTHETIC_BODY\"}]}"
    private suspend fun send(t: AiSafeTransport) = t.post("chat/completions", body, String::class, String::class)
    private fun noConnect() = object : FakeAiConnection() { override fun connect() { throw UnknownHostException("SYNTHETIC_FAILURE") } }
    @Test fun recoveryKeepsTheOriginalBodyAndDoesNotCloseTheTransportOrInflateUsage() = runBlocking {
        val waits = mutableListOf<Long>(); val progress = mutableListOf<AiRetryProgress?>(); val calls = mutableListOf<FakeAiConnection>()
        val ledger = AiUsageLedger(); val state = AiRetryState { waits += it }
        val transport = AiSafeTransport({ key }, { true }, AiRequestBudget(), {
            (if (calls.isEmpty()) noConnect() else FakeAiConnection()).also(calls::add)
        }, usage = AiUsageRecorder(listOf(ledger)), retryState = state, onRetry = { progress += it })
        assertEquals(Json.parseToJsonElement(aiAnswer("ok")), Json.parseToJsonElement(send(transport)))
        assertEquals(2, calls.size); assertEquals(0, calls[0].sent.size())
        assertEquals(AiSseCompletion.request(AiRequestContract.forDeepSeek(body)), calls[1].sent.toString("UTF-8"))
        assertEquals(listOf(1000L), waits); assertEquals(listOf(1), progress.filterNotNull().filter { it.waitMillis > 0 }.map { it.index })
        assertNull(progress.last()); assertEquals(1L, ledger.snapshot().requests); assertEquals(0L, ledger.snapshot().unknownChat)
        assertTrue(calls.all { it.disconnected.get() })
    }
    @Test fun fiveRetriesMeanAtMostSixAttemptsAndAreSharedAcrossLogicalHttpCalls() = runBlocking {
        val waits = mutableListOf<Long>(); var opened = 0; val ledger = AiUsageLedger()
        val state = AiRetryState { waits += it }
        val transport = AiSafeTransport({ key }, { true }, AiRequestBudget(), {
            val index = opened++
            if (index == 1 || index == 3) FakeAiConnection() else noConnect()
        }, usage = AiUsageRecorder(listOf(ledger)), retryState = state)
        send(transport); send(transport)
        val failure = runCatching { send(transport) }.exceptionOrNull() as AiFailure
        assertEquals(AiFailureKind.DNS, failure.diagnostic?.kind); assertEquals(8, opened)
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L), waits)
        assertEquals(3L, ledger.snapshot().requests); assertEquals(1L, ledger.snapshot().unknownChat)
        var onlyOpened = 0
        val onlyFailures = AiSafeTransport({ key }, { true }, AiRequestBudget(), { onlyOpened++; noConnect() }, retryState = AiRetryState { })
        assertTrue(runCatching { send(onlyFailures) }.isFailure); assertEquals(6, onlyOpened)
    }
    @Test fun writeReadHttpAuthRateTlsProtocolAndNonconnectFailuresNeverRetry() = runBlocking {
        val fixtures = listOf<() -> FakeAiConnection>(
            { object : FakeAiConnection() { override fun connect() { throw SSLHandshakeException("SYNTHETIC_CERT") } } },
            { object : FakeAiConnection() { override fun getOutputStream(): java.io.ByteArrayOutputStream { throw SocketTimeoutException() } } },
            { object : FakeAiConnection() { override fun getInputStream(): InputStream { throw SocketTimeoutException() } } },
            { FakeAiConnection(503) }, { FakeAiConnection(401) }, { FakeAiConnection(429) },
            { object : FakeAiConnection() { override fun connect() { throw AiFailure(AiProblem.PROTOCOL) } } })
        for (fixture in fixtures) {
            var opened = 0; val waits = mutableListOf<Long>()
            val transport = AiSafeTransport({ key }, { true }, AiRequestBudget(), { opened++; fixture() }, retryState = AiRetryState { waits += it })
            assertTrue(runCatching { send(transport) }.isFailure); assertEquals(1, opened); assertTrue(waits.isEmpty())
        }
        assertNull(AiRetryPolicy.connectionFailure(UnknownHostException(), AiStage.TRANSPORT, false))
        assertNull(AiRetryPolicy.connectionFailure(ConnectException(), AiStage.CONNECT, true))
        assertEquals(AiFailureKind.CONNECT, AiRetryPolicy.connectionFailure(ConnectException(), AiStage.CONNECT, false))
        assertEquals(AiFailureKind.CONNECT_TIMEOUT, AiRetryPolicy.connectionFailure(SocketTimeoutException(), AiStage.CONNECT, false))
    }
    @Test fun cancellationDeadlineAndKeyChangeDuringWaitingCreateNoNewConnection() = runBlocking {
        val waiting = CompletableDeferred<Unit>(); var opened = 0
        val transport = AiSafeTransport({ key }, { true }, AiRequestBudget(), { opened++; noConnect() },
            retryState = AiRetryState { waiting.complete(Unit); awaitCancellation() })
        val job = async(Dispatchers.Default) { send(transport) }
        waiting.await(); job.cancelAndJoin(); assertEquals(1, opened)
        val closeWaiting = CompletableDeferred<Unit>(); var closeOpened = 0; var epochCurrent = true
        val closed = AiSafeTransport({ key }, { epochCurrent }, AiRequestBudget(), { closeOpened++; noConnect() },
            retryState = AiRetryState { closeWaiting.complete(Unit); awaitCancellation() })
        val closedJob = async(Dispatchers.Default) { runCatching { send(closed) } }
        closeWaiting.await(); epochCurrent = false; closed.close()
        val closedFailure = withTimeout(1000) { closedJob.await() }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.KEY, closedFailure.problem); assertEquals(1, closeOpened)
        var current = true; var changedOpened = 0
        val changed = AiSafeTransport({ key }, { current }, AiRequestBudget(), { changedOpened++; noConnect() }, retryState = AiRetryState { current = false })
        assertEquals(AiProblem.KEY, (runCatching { send(changed) }.exceptionOrNull() as AiFailure).problem); assertEquals(1, changedOpened)
        var timedOpened = 0
        val timed = AiSafeTransport({ key }, { true }, AiRequestBudget(), { timedOpened++; noConnect() }, retryState = AiRetryState { awaitCancellation() })
        val failure = runCatching { withTimeout(100) { send(timed) } }.exceptionOrNull()
        assertTrue(failure is TimeoutCancellationException); assertEquals(1, timedOpened)
    }
    @Test fun retryingTheNextHttpCallDoesNotReplayAnAlreadyExecutedKoogTool() = runBlocking {
        val tool = """{"id":"fake","system_fingerprint":"fake","object":"chat.completion","created":1,"model":"deepseek-flash","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"read-once","type":"function","function":{"name":"current_status","arguments":"{}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}"""
        var opened = 0; var reads = 0
        val data = object : AiReadOnlyData {
            override fun currentStatus(): String { reads++; return "{\"status\":\"unknown\"}" }
            override fun curves(query: String, offset: Int, limit: Int) = "{}"
            override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
        }
        val ledger = AiUsageLedger(); val budget = AiRequestBudget()
        val transport = AiSafeTransport({ key }, { true }, budget, {
            when (opened++) { 0 -> FakeAiConnection(answer = tool); 1 -> noConnect(); else -> FakeAiConnection(answer = aiAnswer("done")) }
        }, usage = AiUsageRecorder(listOf(ledger)), retryState = AiRetryState { })
        val turn = AiProtocolTurn(AiProtocolHistory.empty(), emptyList(), "synthetic", transport)
        val result = AiChatSession(data, turn, budget, Dispatchers.Unconfined).reply(emptyList(), "synthetic")
        assertEquals("done", turn.complete(result).text); assertEquals(1, reads); assertEquals(3, opened)
        assertEquals(2L, ledger.snapshot().requests); assertEquals(0L, ledger.snapshot().unknownChat)
    }
    @Test fun retryTraceContainsOnlyClosedProgressAndStopsAfterTheAttemptFinishes() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val recorder = AiDiagnosticRecorder({ kind, fields -> events += kind to fields })
        val attempt = recorder.begin(AiModelPolicy.FLASH)
        recorder.retry(attempt, AiRetryProgress(1, 1000, AiFailureKind.DNS))
        recorder.retry(attempt, AiRetryProgress(1, 0, AiFailureKind.DNS))
        recorder.retry(attempt, AiRetryProgress(6, 32000, AiFailureKind.DNS))
        recorder.succeeded(attempt); recorder.retry(attempt, AiRetryProgress(2, 2000, AiFailureKind.CONNECT))
        val retries = events.filter { it.first == "ai.reply.retry" }
        assertEquals(1, retries.size)
        assertEquals(setOf("scope", "attemptId", "model", "retryIndex", "retryLimit", "waitMs", "stage", "failureKind"), retries.single().second.keys)
        assertEquals("5", retries.single().second["retryLimit"])
        assertEquals("DNS", retries.single().second["failureKind"])
    }
}
