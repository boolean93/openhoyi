package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiErrorPresentationTest {
    private fun d(kind: AiFailureKind, http: Int? = null) = AiDiagnostic(AiStage.SDK_RESPONSE, kind, 32, http)
    @Test fun protocolAndLocalSdkAreNeverPresentedAsNetworkFailure() {
        assertEquals(AiErrorPresentation.Semantic.PROTOCOL, AiErrorPresentation.semantic(AiProblem.PROTOCOL, null))
        for (kind in listOf(AiFailureKind.PROTOCOL_FINAL_MISSING, AiFailureKind.PROTOCOL_HISTORY, AiFailureKind.PARSE))
            assertEquals(AiErrorPresentation.Semantic.PROTOCOL, AiErrorPresentation.semantic(AiProblem.NETWORK, d(kind, 200)))
        assertEquals(AiErrorPresentation.Semantic.LOCAL_PROCESSING, AiErrorPresentation.semantic(AiProblem.NETWORK, d(AiFailureKind.SDK)))
        assertEquals(AiErrorPresentation.Semantic.UNKNOWN, AiErrorPresentation.semantic(AiProblem.NETWORK, null))
        assertEquals(AiErrorPresentation.Semantic.LOCAL_PROCESSING, AiErrorPresentation.semantic(AiProblem.NETWORK, d(AiFailureKind.TRANSPORT_CONTRACT)))
        assertEquals(AiErrorPresentation.Semantic.SERVICE, AiErrorPresentation.semantic(AiProblem.NETWORK, d(AiFailureKind.RESPONSE_POLICY, 200)))
    }
    @Test fun httpAndTimeoutsHaveDistinctNextStepCategories() {
        for (http in listOf(401, 403)) assertEquals(AiErrorPresentation.Semantic.ACCESS, AiErrorPresentation.semantic(AiProblem.NETWORK, d(AiFailureKind.HTTP, http)))
        for (http in listOf(402, 429)) assertEquals(AiErrorPresentation.Semantic.RATE, AiErrorPresentation.semantic(AiProblem.NETWORK, d(AiFailureKind.HTTP, http)))
        assertEquals(AiErrorPresentation.Semantic.SERVICE, AiErrorPresentation.semantic(AiProblem.NETWORK, d(AiFailureKind.HTTP, 503)))
        assertEquals(AiErrorPresentation.Semantic.CONNECT_TIMEOUT, AiErrorPresentation.semantic(AiProblem.NETWORK, d(AiFailureKind.CONNECT_TIMEOUT)))
        assertEquals(AiErrorPresentation.Semantic.READ_TIMEOUT, AiErrorPresentation.semantic(AiProblem.NETWORK, d(AiFailureKind.READ_TIMEOUT, 200)))
        assertEquals(AiErrorPresentation.Semantic.REPLY_TIMEOUT, AiErrorPresentation.semantic(AiProblem.NETWORK, d(AiFailureKind.TOTAL_TIMEOUT)))
        assertEquals(AiErrorPresentation.Semantic.REFUSED, AiErrorPresentation.semantic(AiProblem.REFUSED, d(AiFailureKind.RESPONSE_POLICY, 200)))
    }
    @Test fun totalReplyTimeoutOverridesStaleProtocolAndHttpDiagnostics() {
        for (stage in AiStage.entries) for (status in listOf(null, 200, 429, 503)) {
            assertEquals(AiErrorPresentation.Semantic.REPLY_TIMEOUT,
                AiErrorPresentation.semantic(AiProblem.NETWORK, AiDiagnostic(stage, AiFailureKind.TOTAL_TIMEOUT, 120000, status)))
        }
    }
    @Test fun budgetLimitsHaveSpecificErrorCategories() {
        for ((kind, category) in listOf(
            AiFailureKind.MODEL_REQUEST_BUDGET to AiErrorPresentation.Semantic.MODEL_BUDGET,
            AiFailureKind.SEARCH_BUDGET to AiErrorPresentation.Semantic.SEARCH_BUDGET,
            AiFailureKind.TOOL_CALL_BUDGET to AiErrorPresentation.Semantic.TOOL_BUDGET,
            AiFailureKind.TOOL_RESULT_BUDGET to AiErrorPresentation.Semantic.TOOL_RESULT_BUDGET,
            AiFailureKind.AGENT_ITERATION_BUDGET to AiErrorPresentation.Semantic.AGENT_BUDGET)) {
            assertEquals(category, AiErrorPresentation.semantic(AiProblem.TOOL_LIMIT, d(kind)))
        }
        assertEquals(AiErrorPresentation.Semantic.TOOL, AiErrorPresentation.semantic(AiProblem.TOOL_LIMIT, null))
    }
    @Test fun diagnosticIsBoundedClosedAndNeverCopiesArbitraryStrings() {
        val text = AiErrorPresentation.mockDetails(-1, "PRIVATE_KEY_URL_BODY".repeat(1000),
            AiDiagnostic(AiStage.VALIDATE, AiFailureKind.PARSE, Long.MAX_VALUE, 999), "PRIVATE_FILENAME", "PRIVATE_BODY")
        assertFalse(text.contains("PRIVATE")); assertFalse(text.contains("999"))
        assertTrue(text.contains("Elapsed ms: 86400000")); assertTrue(text.contains("Model: unknown"))
        assertTrue(text.length < 500)
        val noAttempt = AiErrorPresentation.mockDetails(140, AiModelPolicy.FLASH, null)
        assertFalse(noAttempt.contains("Attempt:")); assertTrue(noAttempt.contains("HTTP: unknown"))
        assertFalse(noAttempt.contains("HTTP: 0"))
    }
    @Test fun validAttemptIdentityAndLocalSemanticMeaningArePreserved() {
        val id = "12345678-1234-1234-1234-123456789abc"
        assertTrue(AiErrorPresentation.mockDetails(140, AiModelPolicy.PRO, d(AiFailureKind.HTTP, 401), id).contains("Attempt: $id"))
        assertEquals(AiErrorPresentation.Semantic.COMPACT, AiErrorPresentation.semantic(AiProblem.COMPACT_FAILED, d(AiFailureKind.READ_TIMEOUT)))
        assertEquals(AiErrorPresentation.Semantic.RESPONSE, AiErrorPresentation.semantic(AiProblem.RESPONSE_LIMIT, d(AiFailureKind.RESPONSE_POLICY, 200)))
    }
    @Test fun providerDiagnosticRetainsOnlyClosedReasonAndParameter() {
        val text = AiErrorPresentation.mockDetails(140, "PRIVATE_SENTINEL",
            AiDiagnostic(AiStage.RESPONSE_HEADERS, AiFailureKind.HTTP, 1, 400,
                AiProviderDiagnostic(AiProviderReason.TOOLS_FEATURE_CONFLICT, AiProviderParameter.TOOLS)))
        assertTrue(text.contains("Provider: TOOLS_FEATURE_CONFLICT / TOOLS"))
        assertFalse(text.contains("PRIVATE_SENTINEL"))
        val noParameter = AiErrorPresentation.mockDetails(140, AiModelPolicy.FLASH,
            AiDiagnostic(AiStage.RESPONSE_HEADERS, AiFailureKind.HTTP, 1, 400,
                AiProviderDiagnostic(AiProviderReason.CONTENT_RISK)))
        assertTrue(noParameter.contains("Provider: CONTENT_RISK"))
        assertFalse(noParameter.contains(" / null"))
    }

}
