package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class MachineAlarmsTest {
    private val alarms = MachineAlarms(DefaultStringResources::resolve)
    @Test fun structuredRejectionKeepsAllAlarmPermissionsWithoutResolvingDisplay() {
        val noDisplay = MachineAlarms { _, _ -> error("Building rejection must not resolve display") }
        for (bits in 0..0xFFFF) {
            val message = noDisplay.startBlockMessage(bits)
            assertEquals(bits == 0 || bits == 0x4000, message == null)
            if (message != null) {
                assertEquals("", message.render { _, _ -> "" })
                assertNotNull(message)
            }
        }
        val unknown = requireNotNull(noDisplay.startBlockMessage(0xC000))
        val captured = SnapshotMessage.resource(unknown, DefaultStringResources::resolve)
        assertTrue(captured.initialText.contains("bit15"))
        assertTrue(captured.initialText.contains("0xC000"))
        assertEquals("blocked:bit15:unknown:0xC000", captured.render { id, args -> when(id) {
            R.string.alarm_unknown_bit -> "unknown:${args.single()}"
            R.string.alarm_start_block -> "blocked:${args[0]}:${args[1]}"
            else -> error("Unexpected alarm resource")
        } })
        assertTrue(captured.initialText.contains("未知告警"))
    }

    @Test fun translatedOrEmptyDescriptionsCannotPermitABlockingAlarm() {
        for (translation in listOf("translated", "")) {
            val localized = MachineAlarms { _, _ -> translation }
            for (bits in 0..0xFFFF) {
                assertEquals(bits == 0 || bits == 0x4000, localized.startBlock(bits) == null)
            }
            assertTrue(localized.banner(1, 1000, 2000)!!.blocking)
            assertFalse(localized.banner(0x4000, 1000, 2000)!!.blocking)
        }
    }

    @Test fun everyWireAlarmCombinationMatchesControlMaskAndFirstFaultPriority() {
        for (bits in 0..0xFFFF) {
            val permitted = bits == 0 || bits == 0x4000
            assertEquals("alarm 0x${bits.toString(16)}", permitted, alarms.startBlock(bits) == null)
        }
        for (bit in 0..15) {
            if (bit == 14) continue
            val message = requireNotNull(alarms.startBlock((1 shl bit) or 0x4000))
            assertTrue(message.contains(if (bit == 15) "未知告警" else "C${bit + 1} "))
        }
        assertTrue(requireNotNull(alarms.startBlock(0xFFFF)).contains("C1 "))
    }

    @Test fun homepageBannerUsesOnlyFreshAlarmsAndDistinguishesTailWater() {
        assertEquals(null, alarms.banner(1, 1000, 2501))
        assertEquals(null, alarms.banner(0, 1000, 2000))
        val warning = alarms.banner(1 shl 14, 1000, 2000)!!
        assertEquals(false, warning.blocking)
        assertTrue(warning.message.contains("水位预警"))
        val fault = alarms.banner((1 shl 14) or 1, 1000, 2000)!!
        assertEquals(true, fault.blocking)
        assertTrue(fault.message.contains("暂不启动萃取"))
        assertTrue(fault.message.contains("C1"))
    }
    @Test fun legacyBitOrderMapsC15SlotToC16AndPreservesUnknownHighBit() {
        val active = alarms.active(0xC201)
        assertEquals(listOf("C1", "C10", "C16", "bit15"), active.map { it.code })
        assertTrue(active[1].description.contains("进水压力"))
        assertTrue(active[2].description.contains("水箱液位"))
        assertTrue(active[3].description.contains("未知"))
        assertEquals(emptyList<MachineAlarms.Alarm>(), alarms.active(0))
    }

    @Test fun freshAndExpiredAlarmStatesCannotBeConfused() {
        assertEquals("尚未收到机器告警状态", alarms.describe(null, null, 2000))
        assertEquals("当前无告警", alarms.describe(0, 1000, 2000))
        assertTrue(alarms.describe(1, 1000, 2000).startsWith("当前告警"))
        assertTrue(alarms.describe(1, 1000, 2501).startsWith("告警状态已过期"))
        assertEquals("告警状态已过期", alarms.describe(0, 1000, 2501))
        assertEquals("告警状态已过期", alarms.describe(0, 3000, 2501))
    }
}
