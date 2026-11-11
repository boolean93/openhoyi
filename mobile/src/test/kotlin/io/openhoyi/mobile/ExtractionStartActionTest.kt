package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ExtractionStartActionTest {
    @Test fun protectedTapExplainsWithoutStarting() {
        for (reason in listOf("Scale not ready. Reconnect scale", "Waiting for scale zero", "Target weight unsupported. Use another scale")) {
            var starts = 0
            val messages = mutableListOf<String>()
            if (ExtractionStartAction.permit(reason, messages::add)) starts++
            assertEquals(listOf(reason), messages)
            assertEquals(0, starts)
        }
    }
    @Test fun permittedTapCanContinueWithoutToast() {
        val messages = mutableListOf<String>()
        assertTrue(ExtractionStartAction.permit(null, messages::add))
        assertTrue(messages.isEmpty())
    }
}
