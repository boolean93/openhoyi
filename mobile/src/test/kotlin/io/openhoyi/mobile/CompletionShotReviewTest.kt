package io.openhoyi.mobile

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
        route.persisted(capture)
        assertEquals("ticket", route.issue(first, 3))
        assertNull(route.issue(first, 3))
        val second = feedback("second")
        val next = requireNotNull(route.begin(second, "second-persisted"))
        route.persisted(capture)
        assertFalse(route.ready(second))
        route.persisted(next)
        assertTrue(route.ready(second))
        assertNull(route.issue(second, 3)) // Do not overwrite the first pending navigation.
        assertNull(route.consume("ticket", 3, false))
        val request = requireNotNull(route.consume("ticket", 3, true))
        assertEquals("manual-persisted", request.historyId)
        val visibleQuestion = "Can you review the shot I just finished?"
        val line = request.line(visibleQuestion)
        assertEquals(visibleQuestion, line.text)
        val outbound = line.wireText
        assertTrue(outbound.startsWith(visibleQuestion))
        assertTrue(outbound.contains("shotId=manual-persisted"))
        assertFalse(outbound.contains("second-persisted"))
        assertFalse(outbound.contains("manual-provisional"))
        assertNull(route.consume("ticket", 3, true))
    }

    @Test fun noKeyAndChangedKeyOrCancelledDisclosureCannotDispatch() {
        val route = CompletionShotReview { "ticket" }
        val cup = feedback("manual-provisional")
        val capture = requireNotNull(route.begin(cup, "persisted"))
        route.persisted(capture)
        assertNull(route.issue(cup, null))
        assertEquals("ticket", route.issue(cup, 1))
        assertNull(route.consume("ticket", 2, true))
        assertNull(route.consume("ticket", 1, true))
        val next = requireNotNull(route.begin(cup, "other"))
        route.persisted(next)
        assertEquals("ticket", route.issue(cup, 2))
        route.abandon("ticket")
        assertNull(route.consume("ticket", 2, true))
    }

    @Test fun selectedHistoryTicketUsesThatCupAndCannotBeReplacedOrReplayed() {
        val route = CompletionShotReview { "history-ticket" }
        val recent = feedback("recent")
        val capture = requireNotNull(route.begin(recent, "recent"))
        route.persisted(capture)

        assertNull(route.issueHistory("invalid/id", 7))
        assertNull(route.issueHistory("older", null))
        val ticket = requireNotNull(route.issueHistory("older", 7))
        assertNull(route.issueHistory("different", 7))
        assertNull(route.issue(recent, 7))
        assertNull(route.consume(ticket, 7, false))
        val selected = requireNotNull(route.consume(ticket, 7, true))
        assertTrue(selected.fromHistory)
        assertEquals("older", selected.historyId)
        assertTrue(selected.line("Review this cup").wireText.contains("shotId=older"))
        assertNull(route.consume(ticket, 7, true))
        assertTrue(route.ready(recent))
    }

    @Test fun aFailedSampleReadDoesNotEnableTheReview() {
        val route = CompletionShotReview()
        val cup = feedback("cup")
        val capture = requireNotNull(route.begin(cup, "cup"))
        route.persisted(capture, hasSamples = false)
        assertEquals(CompletionShotReview.Availability.UNAVAILABLE, route.availability(cup))
        assertNull(route.issue(cup, 1))
    }
}
