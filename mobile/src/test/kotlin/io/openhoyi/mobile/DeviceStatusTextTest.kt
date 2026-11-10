package io.openhoyi.mobile

import io.openhoyi.session.CoffeeSessionFailure
import io.openhoyi.session.DeviceState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceStatusTextTest {
    @Test fun connectionPhasesShareShortLabelsAndEveryDisplayedStateFitsFourCharacters() {
        for(state in listOf(DeviceState.CONNECTING,DeviceState.DISCOVERING,DeviceState.SUBSCRIBING,DeviceState.INITIALIZING))
            assertEquals(R.string.device_state_connecting,DeviceStatusText.resource(state))
        for(state in DeviceState.entries)for(failure in listOf(null)+CoffeeSessionFailure.entries){
            val text=DefaultStringResources.template(DeviceStatusText.coffeeResource(state,failure))
            org.junit.Assert.assertTrue("$state/$failure: $text",text.length<=4)
            org.junit.Assert.assertFalse(text.contains('\n'))
        }
    }

    @Test fun coffeeStatusLineExplainsFailureOnlyForDisplay() {
        assertEquals(R.string.device_state_protocol_timeout,
            DeviceStatusText.coffeeResource(DeviceState.FAILED, CoffeeSessionFailure.PROTOCOL_TIMEOUT))
        assertEquals(R.string.device_state_auth_failed,
            DeviceStatusText.coffeeResource(DeviceState.FAILED, CoffeeSessionFailure.AUTH))
        assertEquals(R.string.device_state_failed,
            DeviceStatusText.coffeeResource(DeviceState.FAILED, CoffeeSessionFailure.CONNECT))
        assertEquals(R.string.device_state_failed, DeviceStatusText.coffeeResource(DeviceState.FAILED, null))
        assertEquals(R.string.device_state_unsupported,
            DeviceStatusText.coffeeResource(DeviceState.UNSUPPORTED, null))
        assertEquals(R.string.device_state_ready, DeviceStatusText.coffeeResource(DeviceState.READY, null))
    }
}
