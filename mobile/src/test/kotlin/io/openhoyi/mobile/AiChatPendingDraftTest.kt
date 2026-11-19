package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiChatPendingDraftTest {
    @Test fun sendClearsComposerAndShowsPendingUntilAtomicCommit() {
        val state = AiChatPendingDraft().apply { draft = "First question" }
        assertTrue(state.begin(state.draft))
        assertEquals("", state.draft)
        assertEquals("First question", state.pending)
        assertFalse(state.begin("duplicate"))
        state.committed()
        assertNull(state.pending)
        assertEquals("", state.draft)
    }

    @Test fun failureAndExplicitCancellationRestoreExactlyTheQuestionForRetry() {
        val state = AiChatPendingDraft()
        assertTrue(state.begin("retry me"))
        assertEquals("retry me", state.restore())
        assertEquals("retry me", state.draft)
        assertNull(state.pending)
        assertTrue(state.begin(state.draft))
        assertEquals("retry me", state.restore())
    }

    @Test fun failedCompactionRestoresQuestionWithoutDiscardingNewComposerText() {
        val state = AiChatPendingDraft()
        assertTrue(state.begin("first question"))
        assertEquals("first question\n\nnext draft", state.restore("next draft"))
        assertNull(state.pending)
    }

    @Test fun conversationSwitchDiscardsPendingAndBlankOrRepeatedSendCannotStart() {
        val state = AiChatPendingDraft()
        assertFalse(state.begin(" \n "))
        assertTrue(state.begin("old conversation"))
        assertFalse(state.begin("second while busy"))
        state.clear()
        assertNull(state.pending)
        assertEquals("", state.draft)
        assertTrue(state.begin("new conversation"))
        assertEquals("new conversation", state.pending)
    }
}
