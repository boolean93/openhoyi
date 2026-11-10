package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class PortraitTypographyTest {
    @Test fun landscapeAndReadingTextKeepTheirScale() {
        listOf(24 to 36, 40 to 60, 48 to 72, 64 to 96).forEach { (size, line) ->
            assertEquals(size to line, PortraitTypography.resolve(size, line, 700, false, true))
        }
        listOf(16 to 24, 14 to 22).forEach { (size, line) ->
            assertEquals(size to line, PortraitTypography.resolve(size, line, 400, true, true))
        }
    }
    @Test fun phoneAndTabletPreserveDistinctHierarchy() {
        assertEquals(20 to 28, PortraitTypography.resolve(24, 36, 500, true, true))
        assertEquals(22 to 30, PortraitTypography.resolve(24, 36, 500, true, false))
        assertEquals(30 to 38, PortraitTypography.resolve(40, 60, 700, true, true))
        assertEquals(32 to 42, PortraitTypography.resolve(40, 60, 700, true, false))
        assertEquals(32 to 40, PortraitTypography.resolve(48, 72, 700, true, true))
        assertEquals(16 to 24, PortraitTypography.resolve(22, 34, 400, true, true))
    }
}
