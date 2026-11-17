package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekApiCompatFixtureTest {
    @Test fun actualClientMergesReasoningAndToolCallWithoutPlatform35Api() {
        assertEquals("SDK_API_OK", DeepSeekApiCompatFixture.run())
    }
}
