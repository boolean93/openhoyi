package io.openhoyi.lab

import org.junit.Assert.*
import org.junit.Test

class DisplayStateTest {
    @Test fun freshnessNeverInventsLiveData() {
        assertEquals("尚无数据", freshness(null, 5000, true))
        assertEquals("已断开 · 历史数据", freshness(4999, 5000, false))
        assertEquals("实时", freshness(4999, 5000, true))
        assertEquals("数据已过期", freshness(3000, 5000, true))
        assertEquals("数据已过期", freshness(6000, 5000, true))
    }
    @Test fun signedFixedUnitsRemainExact() {
        assertEquals("-0.01", hundredths(-1))
        assertEquals("92.00", hundredths(9200))
        assertEquals("0.00", hundredths(0))
    }
}
