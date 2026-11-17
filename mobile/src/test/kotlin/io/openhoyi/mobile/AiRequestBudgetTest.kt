package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiRequestBudgetTest {
    @Test fun fiftyModelRequestsAreAllowedAndFiftyFirstStopsWithClosedDiagnostic() {
        val budget = AiRequestBudget()
        repeat(50) { budget.request() }
        val error = runCatching { budget.request() }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.TOOL_LIMIT, error.problem)
        assertEquals(AiFailureKind.MODEL_REQUEST_BUDGET, error.diagnostic?.kind)
        assertEquals(AiStage.BUDGET, error.diagnostic?.stage)
        assertNull(error.diagnostic?.httpStatus)
        assertNull(error.cause)
        // A new send has its own budget; no quota leaks from the previous attempt.
        val next = AiRequestBudget(); repeat(50) { next.request() }
    }
    @Test fun hardToolExhaustionPreventsFurtherModelSearchAndTools() {
        val budget = AiRequestBudget(); repeat(12) { budget.tool() }
        val original = runCatching { budget.tool() }.exceptionOrNull() as AiFailure
        assertEquals(AiFailureKind.TOOL_CALL_BUDGET, original.diagnostic?.kind)
        assertSame(original, runCatching { budget.request() }.exceptionOrNull())
        assertSame(original, runCatching { budget.search() }.exceptionOrNull())
        assertSame(original, runCatching { budget.tool() }.exceptionOrNull())
        assertSame(original, runCatching { budget.toolResult("bounded") }.exceptionOrNull())
        val results = AiRequestBudget()
        val resultFailure = runCatching { results.toolResult("x".repeat(AiContextPolicy.REQUEST_BYTES + 1)) }.exceptionOrNull()
        assertSame(resultFailure, runCatching { results.request() }.exceptionOrNull())
    }
    @Test fun searchExhaustionStillAllowsAnswerWithoutAnotherSearch() {
        val budget = AiRequestBudget(); repeat(4) { budget.search() }
        assertEquals(AiFailureKind.SEARCH_BUDGET, (runCatching { budget.search() }.exceptionOrNull() as AiFailure).diagnostic?.kind)
        budget.request(); budget.tool(); assertEquals("existing facts", budget.toolResult("existing facts"))
    }
    @Test fun configuredLimitsMatchUserChoiceAndGraphHasRoomForTools() {
        assertEquals(50, AiChatPolicy.MODEL_REQUESTS)
        assertEquals(12, AiChatPolicy.TOOL_CALLS)
        assertEquals(4, AiChatPolicy.SEARCH_CALLS)
        assertEquals(120_000L, AiChatPolicy.REPLY_TIMEOUT_MS)
        assertEquals(104, AiChatPolicy.AGENT_ITERATIONS)
    }
    @Test fun ordinaryLargeToolResultsArePreservedWithoutTinyCharacterQuotas() {
        val budget = AiRequestBudget()
        val result = "咖啡".repeat(4000)
        repeat(3) { assertSame(result, budget.toolResult(result)) }
        budget.request() // > 12k cumulative and > 6k per-result chars no longer end the reply.
    }
    @Test fun extremeResultsStopAtActualRequestByteBoundaryAndLatch() {
        val budget = AiRequestBudget()
        val megabyte = "x".repeat(1024 * 1024)
        repeat(16) { budget.toolResult(megabyte) }
        val failure = runCatching { budget.toolResult("x") }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.REQUEST_LIMIT, failure.problem)
        assertEquals(AiFailureKind.REQUEST_SIZE_LIMIT, failure.diagnostic?.kind)
        assertSame(failure, runCatching { budget.request() }.exceptionOrNull())
        // Byte safety is UTF-8, not characters: this string is < 16 Mi chars but > 16 MiB.
        val unicode = AiRequestBudget()
        val other = runCatching { unicode.toolResult("中".repeat(AiContextPolicy.REQUEST_BYTES / 3 + 1)) }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.REQUEST_LIMIT, other.problem)
        assertEquals(AiFailureKind.REQUEST_SIZE_LIMIT, other.diagnostic?.kind)
    }
    @Test fun searchAndToolCapsRemainIndependent() {
        val search = AiRequestBudget(); repeat(4) { search.search() }
        assertEquals(AiFailureKind.SEARCH_BUDGET, (runCatching { search.search() }.exceptionOrNull() as AiFailure).diagnostic?.kind)
        val tools = AiRequestBudget(); repeat(12) { tools.tool() }
        assertEquals(AiFailureKind.TOOL_CALL_BUDGET, (runCatching { tools.tool() }.exceptionOrNull() as AiFailure).diagnostic?.kind)

    }
}
