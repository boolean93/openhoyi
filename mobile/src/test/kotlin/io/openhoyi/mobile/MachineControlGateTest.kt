package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class MachineControlGateTest {
    @Test fun unresolvedShotBlocksEveryNewMachineWriteEvenWhenSessionLooksIdle() {
        val warning = "上一杯未确认结束"
        assertEquals(warning, MachineControlGate.block(true, null, warning))
        assertEquals(warning, MachineControlGate.block(true, "预热结果未知", warning))
        assertEquals("预热结果未知", MachineControlGate.block(false, "预热结果未知", warning))
        assertNull(MachineControlGate.block(false, null, warning))
    }
}
