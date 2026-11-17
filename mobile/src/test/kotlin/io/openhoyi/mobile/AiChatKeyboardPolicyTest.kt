package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class AiChatKeyboardPolicyTest {
    private val p = AiChatKeyboardPolicy.Decision.PASS
    private val c = AiChatKeyboardPolicy.Decision.CONSUME
    private val s = AiChatKeyboardPolicy.Decision.SEND

    @Test fun imeOnlySendsNonblankIdleAction() {
        assertEquals(s, AiChatKeyboardPolicy.ime(true, true, false, false))
        assertEquals(c, AiChatKeyboardPolicy.ime(true, false, false, false))
        assertEquals(c, AiChatKeyboardPolicy.ime(true, true, true, false))
        assertEquals(p, AiChatKeyboardPolicy.ime(false, true, false, false))
        assertEquals(p, AiChatKeyboardPolicy.ime(true, true, false, true))
    }

    @Test fun hardwareDownSendsOnceAndShiftKeepsNewline() {
        fun key(down: Boolean = false, up: Boolean = false, shift: Boolean = false,
            repeated: Boolean = false, hasText: Boolean = true, busy: Boolean = false, composing: Boolean = false) =
            AiChatKeyboardPolicy.hardware(true, down, up, shift, repeated, hasText, busy, composing)
        assertEquals(s, key(down = true))
        assertEquals(c, key(up = true))
        assertEquals(c, key(down = true, repeated = true))
        assertEquals(c, key(down = true, hasText = false))
        assertEquals(c, key(down = true, busy = true))
        assertEquals(p, key(down = true, shift = true))
        assertEquals(p, key(down = true, composing = true))
        assertEquals(p, AiChatKeyboardPolicy.hardware(false, true, false, false, false, true, false, false))
    }
}
