package io.openhoyi.mobile

import kotlinx.coroutines.*
import ai.koog.http.client.KoogHttpClient
import kotlin.reflect.KClass
import org.junit.Assert.*
import org.junit.Test

class AiReplyScopeTest {
    @Test fun preparationAndReplyShareOneDeadlineAndAlwaysCleanUp() = runBlocking {
        val scope = AiReplyScope(timeoutMillis = 40)
        var cleaned = false
        var replied = false
        val failure = runCatching { scope.run {
            try {
                delay(25) // preparation / optional summary
                scope.budget.request()
                delay(100) // answer uses only remaining time, not a new 40 ms
                replied = true
            } finally { cleaned = true }
        } }.exceptionOrNull() as AiFailure
        assertEquals(AiFailureKind.TOTAL_TIMEOUT, failure.diagnostic?.kind)
        assertNull(failure.diagnostic?.httpStatus)
        assertTrue(cleaned); assertFalse(replied)
    }
    @Test fun summaryAndReplyDoNotGetSeparateModelQuotas() = runBlocking {
        val scope = AiReplyScope()
        scope.run {
            scope.budget.request() // summary
            repeat(49) { scope.budget.request() } // answer
            val failure = runCatching { scope.budget.request() }.exceptionOrNull() as AiFailure
            assertEquals(AiFailureKind.MODEL_REQUEST_BUDGET, failure.diagnostic?.kind)
        }
    }
    @Test fun realSessionAndCompactorDoNotMaskOuterReplyTimeout() = runBlocking {
        for (compact in listOf(false, true)) {
            val scope = AiReplyScope(timeoutMillis = 300)
            scope.diagnostics.stage = AiStage.WRITE // Distinguish the outer deadline from the inner Session.
            var entered = false
            var cleaned = false
            val base = AiSafeTransport({ "fake-test-only-key" }, { true }, scope.budget, { error("No connection may open") })
            val waiting = object : KoogHttpClient by base {
                override suspend fun <T : Any, R : Any> post(path: String, requestBody: T, requestBodyType: KClass<T>,
                    responseType: KClass<R>, parameters: Map<String, String>, headers: Map<String, String>): R {
                    entered = true
                    try { awaitCancellation() } finally { cleaned = true }
                }
            }
            val error = runCatching { scope.run {
                if (compact) {
                    val lines = listOf(AiChatLine(true, "old"), AiChatLine(false, "old reply"),
                        AiChatLine(true, "recent"), AiChatLine(false, "recent reply"))
                    val history = AiProtocolHistory.checked(AiContextPolicy.lines(lines), reportedInput = 200000)
                    AiContextCompactor.compact(history, lines, "next", waiting)
                } else {
                    val data = object : AiReadOnlyData {
                        override fun currentStatus() = "{}"
                        override fun curves(query: String, offset: Int, limit: Int) = "{}"
                        override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
                    }
                    AiChatSession(data, waiting, scope.budget, Dispatchers.Unconfined, timeoutMillis = 1000)
                        .reply(emptyList(), "next")
                }
            } }.exceptionOrNull() as AiFailure
            assertTrue(entered); assertTrue(cleaned)
            assertEquals(AiFailureKind.TOTAL_TIMEOUT, error.diagnostic?.kind)
            assertEquals(AiStage.WRITE, error.diagnostic?.stage)
            base.close()
        }
    }
    @Test fun enclosingDeadlineRemainsCancellationInsteadOfReplyError() = runBlocking {
        val failure = runCatching { withTimeout(10) {
            AiReplyScope(timeoutMillis = 1000).run { awaitCancellation() }
        } }.exceptionOrNull()
        assertTrue(failure is TimeoutCancellationException)
        assertFalse(failure is AiFailure)
    }
}
