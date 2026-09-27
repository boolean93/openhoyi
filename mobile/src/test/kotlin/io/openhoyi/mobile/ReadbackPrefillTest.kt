package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadbackPrefillTest {
    @Test fun prefillTracksMachineReadbackUntilUserEdits() {
        assertEquals("92", ReadbackPrefill.next("", null, 92, focused = false))
        assertEquals("93", ReadbackPrefill.next("92", "92", 93, focused = false))
        assertNull(ReadbackPrefill.next("95", "92", 93, focused = false))
        assertNull(ReadbackPrefill.next("", "92", 93, focused = false))
        assertNull(ReadbackPrefill.next("92", "92", 93, focused = true))
        assertNull(ReadbackPrefill.next("93", "92", 93, focused = false))
        assertNull(ReadbackPrefill.next("95", null, 93, focused = false))
        assertNull(ReadbackPrefill.next("93", null, 93, focused = false))
    }
}
