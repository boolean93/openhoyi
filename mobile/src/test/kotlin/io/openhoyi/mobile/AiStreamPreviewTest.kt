package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiStreamPreviewTest {
    @Test fun realThinkingOnlyAndEphemeralCompletion() {
        val state = AiStreamPreview()
        assertEquals("", state.snapshot().reasoning)
        state.toolStarted()
        assertTrue(state.snapshot().toolStarted)
        state.append(AiStreamDelta.Reasoning("received thought"))
        state.append(AiStreamDelta.Content("partial answer"))
        assertFalse(state.snapshot().toolStarted)
        state.finish()
        state.append(AiStreamDelta.Reasoning(" late"))
        state.toolStarted()
        assertEquals("received thought", state.snapshot().reasoning)
        assertEquals("", state.snapshot().content)
        state.clear()
        state.append(AiStreamDelta.Content("late secret"))
        state.toolStarted()
        assertEquals(AiStreamPreview.Snapshot(), state.snapshot())
        assertFalse(state.snapshot().toString().contains("secret"))
    }
    @Test fun boundedAndCoalesced() {
        val state = AiStreamPreview()
        repeat(3) { state.append(AiStreamDelta.Reasoning("x".repeat(10_000))) }
        assertEquals(AiStreamPreview.MAX_CHARS, state.snapshot().reasoning.length)
        assertTrue(state.scheduleUpdate())
        assertFalse(state.scheduleUpdate())
        state.updateDelivered()
        assertTrue(state.scheduleUpdate())
    }

    @Test fun interruptionRetainsBodyButNotReasoningOrLateUpdates() {
        val preview = AiStreamPreview()
        preview.append(AiStreamDelta.Reasoning("private"))
        preview.append(AiStreamDelta.Content("answer"))
        preview.toolStarted()
        preview.interrupt()
        preview.append(AiStreamDelta.Content("late")); preview.toolStarted()
        val snapshot = preview.snapshot()
        assertEquals("answer", snapshot.content)
        assertEquals("", snapshot.reasoning)
        assertFalse(snapshot.toolStarted)
        assertEquals(AiStreamPreview.Phase.INTERRUPTED, snapshot.phase)
        preview.clear()
        assertEquals(AiStreamPreview.Snapshot(), preview.snapshot())
        val next = AiStreamPreview()
        assertEquals("", next.snapshot().content)
    }
    @Test fun bodyPrefixIsBoundedAndTruncationExplicitInsteadOfLosingBeginning() {
        val preview = AiStreamPreview()
        preview.append(AiStreamDelta.Content("prefix" + "x".repeat(AiStreamPreview.CONTENT_CHARS)))
        preview.append(AiStreamDelta.Content("tail"))
        preview.interrupt()
        assertEquals(AiStreamPreview.CONTENT_CHARS, preview.snapshot().content.length)
        assertTrue(preview.snapshot().content.startsWith("prefix"))
        assertFalse(preview.snapshot().content.endsWith("tail"))
        assertTrue(preview.snapshot().contentTruncated)
    }
    @Test fun interruptedBodyNeverEndsWithHalfAnEmoji() {
        val preview = AiStreamPreview()
        preview.append(AiStreamDelta.Content("x".repeat(AiStreamPreview.CONTENT_CHARS - 1) + "😀"))
        assertFalse(preview.snapshot().content.last().isHighSurrogate())
        preview.interrupt()
        assertFalse(preview.snapshot().content.last().isHighSurrogate())
        assertTrue(preview.snapshot().contentTruncated)
    }
    @Test fun emojiSplitAcrossDeltasIsPreservedInDisplay() {
        val preview = AiStreamPreview()
        preview.append(AiStreamDelta.Content("\uD83D"))
        assertEquals("", preview.snapshot().content)
        preview.append(AiStreamDelta.Content("\uDE00"))
        assertEquals("😀", preview.snapshot().content)
    }
}
