package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.SleepPart
import org.junit.Assert.*
import org.junit.Test

class SleepScheduleSafetyTest {
    private val raw = ByteFrame(byteArrayOf())
    private val valid = SleepDay(22, 0, 8, 0)
    private fun first(days: List<SleepDay> = List(4) { valid }) = SleepPart(0, 0xFE, days, raw)
    private fun second(days: List<SleepDay> = List(3) { valid }) = SleepPart(4, null, days, raw)

    @Test fun enablingRequiresCompleteValidDeviceSchedule() {
        assertFalse(SleepScheduleSafety.canEnable(null, second()))
        assertFalse(SleepScheduleSafety.canEnable(first(), null))
        assertFalse(SleepScheduleSafety.canEnable(first(listOf(valid.copy(sleepHour = 25)) + List(3) { valid }), second()))
        assertFalse(SleepScheduleSafety.canEnable(first(), second(listOf(valid.copy(wakeMinute = 61)) + List(2) { valid })))
        assertTrue(SleepScheduleSafety.canEnable(first(), second()))
    }
}
