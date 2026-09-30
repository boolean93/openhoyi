package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceStatusTextTest {
    @Test fun everyDeviceStateHasADistinctLabelResource() {
        val labels = DeviceState.entries.map(DeviceStatusText::resource)
        assertEquals(DeviceState.entries.size, labels.distinct().size)
        assertEquals(R.string.device_state_ready, DeviceStatusText.resource(DeviceState.READY))
        assertEquals(R.string.device_state_failed, DeviceStatusText.resource(DeviceState.FAILED))
    }
}
