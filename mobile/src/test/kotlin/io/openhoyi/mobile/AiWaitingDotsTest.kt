package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class AiWaitingDotsTest {
    @Test fun cyclesWithoutInventingProgress() {
        assertEquals("正在回复.", AiWaitingDots.frame("正在回复…", 0))
        assertEquals("正在回复..", AiWaitingDots.frame("正在回复…", 1))
        assertEquals("正在回复...", AiWaitingDots.frame("正在回复…", 2))
        assertEquals("正在回复.", AiWaitingDots.frame("正在回复…", 3))
    }
    @Test fun preservesOtherStatusLinesAndLanguage() {
        assertEquals("Replying..\nContext summary", AiWaitingDots.frame("Replying…\nContext summary", 1))
    }
}
