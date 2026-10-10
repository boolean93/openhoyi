package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class BeanQuantityTest {
    @Test fun parsesExactMgWithoutFloatingPoint() {
        assertEquals(18_125L, BeanQuantity.parseGrams("18.125"))
        assertEquals(500L, BeanQuantity.parseGrams(".5"))
        assertEquals(0L, BeanQuantity.parseGrams("0"))
        assertEquals(Long.MAX_VALUE, BeanQuantity.parseGrams("9223372036854775.807"))
    }
    @Test fun rejectsPrecisionOverflowAndAmbiguousInput() {
        listOf("18.0001", "9223372036854775.808", "-1", "1e3", "NaN", "", "1,5").forEach {
            assertTrue(it, runCatching { BeanQuantity.parseGrams(it) }.isFailure)
        }
    }
    @Test fun formatsEveryBoundaryExactly() {
        assertEquals("18.125", BeanQuantity.formatGrams(18_125))
        assertEquals("0", BeanQuantity.formatGrams(0))
        assertEquals("-0.001", BeanQuantity.formatGrams(-1))
        assertEquals("9223372036854775.807", BeanQuantity.formatGrams(Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, BeanQuantity.parseGrams(BeanQuantity.formatGrams(Long.MAX_VALUE)))
    }
}
