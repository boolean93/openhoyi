package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceStatusTextTest {
    @Test fun everyDeviceStateHasAReadableLabel() {
        val labels = DeviceState.entries.map(DeviceStatusText::label)
        assertEquals(DeviceState.entries.size, labels.distinct().size)
        assertEquals("已就绪", DeviceStatusText.label(DeviceState.READY))
        assertEquals("连接失败", DeviceStatusText.label(DeviceState.FAILED))
    }
}
