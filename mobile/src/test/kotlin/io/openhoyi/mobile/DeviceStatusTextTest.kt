package io.openhoyi.mobile

import io.openhoyi.session.CoffeeSessionFailure
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

    @Test fun coffeeStatusLineExplainsFailureOnlyForDisplay() {
        assertEquals(R.string.device_state_protocol_timeout,
            DeviceStatusText.coffeeResource(DeviceState.FAILED, CoffeeSessionFailure.PROTOCOL_TIMEOUT))
        assertEquals(R.string.device_state_auth_failed,
            DeviceStatusText.coffeeResource(DeviceState.FAILED, CoffeeSessionFailure.AUTH))
        assertEquals(R.string.device_state_failed,
            DeviceStatusText.coffeeResource(DeviceState.FAILED, CoffeeSessionFailure.CONNECT))
        assertEquals(R.string.device_state_failed, DeviceStatusText.coffeeResource(DeviceState.FAILED, null))
        assertEquals(R.string.home_firmware_readonly,
            DeviceStatusText.coffeeResource(DeviceState.UNSUPPORTED, null))
        assertEquals(R.string.device_state_ready, DeviceStatusText.coffeeResource(DeviceState.READY, null))
    }
}
