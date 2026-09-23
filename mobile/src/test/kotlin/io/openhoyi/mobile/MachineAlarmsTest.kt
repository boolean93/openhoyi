package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class MachineAlarmsTest {
    @Test fun legacyBitOrderMapsC15SlotToC16AndPreservesUnknownHighBit() {
        val active = MachineAlarms.active(0xC201)
        assertEquals(listOf("C1", "C10", "C16", "bit15"), active.map { it.code })
        assertTrue(active[1].description.contains("进水压力"))
        assertTrue(active[2].description.contains("水箱液位"))
        assertTrue(active[3].description.contains("未知"))
        assertEquals(emptyList<MachineAlarms.Alarm>(), MachineAlarms.active(0))
    }

    @Test fun freshAndExpiredAlarmStatesCannotBeConfused() {
        assertEquals("尚未收到机器告警状态", MachineAlarms.describe(null, null, 2000))
        assertEquals("当前无告警", MachineAlarms.describe(0, 1000, 2000))
        assertTrue(MachineAlarms.describe(1, 1000, 2000).startsWith("当前告警"))
        assertTrue(MachineAlarms.describe(1, 1000, 2501).startsWith("告警状态已过期"))
        assertEquals("告警状态已过期", MachineAlarms.describe(0, 1000, 2501))
        assertEquals("告警状态已过期", MachineAlarms.describe(0, 3000, 2501))
    }
}
