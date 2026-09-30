package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class CoffeeAlarmPolicyTest {
    @Test fun everyWireCombinationAllowsOnlyNoAlarmOrTailWaterAlone() {
        for (bits in 0..0xFFFF) {
            assertEquals("alarm 0x${bits.toString(16)}", bits == 0 || bits == 0x4000,
                CoffeeAlarmPolicy.permitsNewControl(bits))
        }
    }

    @Test fun firstFaultFollowsLegacyPriorityAndUnknownHighBitBlocks() {
        assertNull(CoffeeAlarmPolicy.firstBlockingBit(0))
        assertNull(CoffeeAlarmPolicy.firstBlockingBit(0x4000))
        for (bit in 0..15) {
            if (bit == 14) continue
            assertEquals(bit, CoffeeAlarmPolicy.firstBlockingBit((1 shl bit) or 0x4000))
        }
        assertEquals(15, CoffeeAlarmPolicy.firstBlockingBit(0xC000))
        assertEquals(0, CoffeeAlarmPolicy.firstBlockingBit(0xFFFF))
        assertEquals(9, CoffeeAlarmPolicy.firstBlockingBit(0xC200))
    }
}
