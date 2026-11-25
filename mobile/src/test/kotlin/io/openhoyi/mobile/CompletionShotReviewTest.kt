package io.openhoyi.mobile

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CompletionShotReviewTest {
    private fun feedback(id: String) = BrewFeedbackResult(
        id, 22, 2, 20, 500, 1.6, BrewFeedbackClips.Level.BRAVO,
    )

    @Test fun exactCompletedCupTicketSurvivesNextCupAndNeverReplays() {
        val route = CompletionShotReview { "ticket" }
        val first = feedback("manual-provisional")
        val capture = requireNotNull(route.begin(first, "manual-persisted"))
        assertNull(route.issue(first, 3))
        route.persisted(capture, "only first cup")
        assertEquals("ticket", route.issue(first, 3))
        assertNull(route.issue(first, 3))
        val second = feedback("second")
        val next = requireNotNull(route.begin(second, "second-persisted"))
        route.persisted(capture, "stale overwrite")
        assertFalse(route.ready(second))
        route.persisted(next, "only second cup")
        assertTrue(route.ready(second))
        assertNull(route.issue(second, 3)) // Do not overwrite the first pending navigation.
        assertNull(route.consume("ticket", 3, false))
        val request = requireNotNull(route.consume("ticket", 3, true))
        assertEquals("manual-persisted", request.historyId)
        assertEquals("only first cup", request.body)
        assertNull(route.consume("ticket", 3, true))
    }

    @Test fun noKeyAndChangedKeyOrCancelledDisclosureCannotDispatch() {
        val route = CompletionShotReview { "ticket" }
        val cup = feedback("manual-provisional")
        val capture = requireNotNull(route.begin(cup, "persisted"))
        route.persisted(capture, "body")
        assertNull(route.issue(cup, null))
        assertEquals("ticket", route.issue(cup, 1))
        assertNull(route.consume("ticket", 2, true))
        assertNull(route.consume("ticket", 1, true))
        val next = requireNotNull(route.begin(cup, "other"))
        route.persisted(next, "other body")
        assertEquals("ticket", route.issue(cup, 2))
        route.abandon("ticket")
        assertNull(route.consume("ticket", 2, true))
    }

    @Test fun completionSummaryUsesBrewingPressureAndScaleFlowAndFrozenEndpoint() {
        val cup = feedback("live-id")
        val points = listOf(
            ShotPoint(100, 40, 100, 3, 9100, 7711, scaleFlowHundredths = 100, brewing = true),
            ShotPoint(1000, 80, 200, 600, 9200, 9999, scaleFlowHundredths = 200, brewing = true,
                beverageHundredthsGram = 3500, beverageSource = BeverageWeightSource.CONFIRMED, beverageSegmentId = 0),
            ShotPoint(21_000, 150, 300, 600, 9200, 9999, scaleFlowHundredths = 900, brewing = false),
        )
        val summary = BrewCompletionSummary.create(cup, points, terminalBeverageHundredthsGram = 3620, endCupCount = 128)
        assertEquals(6.0, summary.averagePressureBar!!, 0.0)
        assertEquals(1.5, summary.averageScaleFlowGramsPerSecond!!, 0.0)
        assertEquals(36.2, summary.outputGrams!!, 0.0)
        assertEquals(128, summary.endCupCount)

        val noScaleFlow = BrewCompletionSummary.create(cup, points.map { it.copy(scaleFlowHundredths = null) })
        assertNull(noScaleFlow.averageScaleFlowGramsPerSecond)
        assertNull(noScaleFlow.outputGrams) // Do not substitute the last plotted net sample.
        assertNull(noScaleFlow.endCupCount)
        assertNull(BrewCompletionSummary.create(cup, emptyList()).averagePressureBar)
    }

    @Test fun fakeProviderReceivesOnlyExplicitEvidenceOnce() = runBlocking {
        val request = CompletionShotReview.Request("record", "one-cup evidence", 3)
        var calls = 0
        val send: suspend (CompletionShotReview.Request) -> String = {
            calls++
            assertEquals("one-cup evidence", it.body)
            """{"choices":[{"finish_reason":"stop","message":{"content":"{\"good\":\"stable\",\"deviation\":\"none\",\"nextCup\":\"measure dose\"}"}}]}"""
        }
        assertEquals(ShotReviewStatus.FAILED, runCompletionShotReview(request, { false }, send).status)
        assertEquals(ShotReviewResult(ShotReviewStatus.READY, "stable\nnone\nmeasure dose"),
            runCompletionShotReview(request, { true }, send))
        assertEquals(ShotReviewStatus.FAILED, runCompletionShotReview(request, { true }, send).status)
        assertEquals(1, calls)
    }
}
