package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackgroundTest {
    @Test fun defaultsAndControlledPhotoIds() {
        assertEquals(AppBackgroundConfig(), AppBackgroundConfig.restore(null))
        for (bad in listOf("photo:../user.jpg", "photo:file://external", "not-a-choice", "photo:\\outside")) {
            assertEquals(AppBackground.Solid, AppBackground.parse(bad))
        }
        val value = AppBackgroundConfig(AppBackground.Photo("12345678-1234-1234-1234-123456789abc"), 24)
        assertEquals(value, AppBackgroundConfig.restore(value.key))
        assertEquals(AppBackgroundConfig.MAX_STRENGTH, AppBackgroundConfig.restore("mist|100").strength)
        assertEquals(AppBackgroundConfig.MIN_STRENGTH, AppBackgroundConfig.restore("slate|-20").strength)
    }

    @Test fun decodeBoundsAreIndependentAndNeverRoundAboveBudget() {
        assertEquals(1, BackgroundImageLimits.sample(1600, 2000))
        assertEquals(2, BackgroundImageLimits.sample(2048, 2048))
        assertEquals(8, BackgroundImageLimits.sample(9000, 6000))
        assertEquals(4, BackgroundImageLimits.sample(4097, 2101))
        assertFalse(BackgroundImageLimits.fits(2048, 2048))
        assertTrue(BackgroundImageLimits.fits(2048, 1953))
        assertFalse(BackgroundImageLimits.fits(2049, 2))
        for ((w, h) in listOf(0 to 200, 32769 to 20, 20000 to 20000)) {
            assertTrue(runCatching { BackgroundImageLimits.sample(w, h) }.isFailure)
        }
    }

    @Test fun onlyLatestRequestCanPublish() {
        val fence = BackgroundRequestFence()
        val first = fence.next()
        val second = fence.next()
        assertFalse(fence.accepts(first))
        assertTrue(fence.accepts(second))
        fence.next() // cancel or Apply
        assertFalse(fence.accepts(second))
    }
}
