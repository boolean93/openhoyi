package io.openhoyi.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** No Android/Bluetooth: failing display observers must not prevent GATT ownership release. */
class StateObserverCleanupTest {
    private class Driver : GattDriver {
        val closed = mutableListOf<Long>()
        val operations = mutableListOf<GattOperation>()
        override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
            operations += operation
            return true
        }
        override fun close(generation: Long) { closed += generation }
    }
    private fun verify(role: DeviceRole, terminal: DeviceState) {
        val driver = Driver()
        var failObserver = false
        val observerError = IllegalStateException("Intentional state observer failure")
        val session = DeviceSession(role, driver, { 0L }, stateChanged = {
            if (failObserver && it == terminal) throw observerError
        })
        val authentication = if (role == DeviceRole.COFFEE)
            CoffeeAuthentication(LocalDateTime.of(2026, 10, 4, 0, 0), "123456") else null
        session.connect("fixture-device", authentication)
        val generation = session.generation
        assertEquals(DeviceState.CONNECTING, session.state)
        val sent = driver.operations.toList()
        failObserver = true
        // Cleanup must complete before the original observer error reaches the caller.
        val failure = runCatching {
            if (terminal == DeviceState.DISCONNECTED) session.disconnect()
            else session.onDisconnected(generation, "fixture link loss")
        }.exceptionOrNull()
        assertSame(observerError, failure)
        assertEquals(terminal, session.state)
        assertTrue("State observer failure left GATT ownership open", generation in driver.closed)
        assertEquals(sent, driver.operations)
    }
    @Test fun coffeeDisconnectStillClosesAfterObserverFailure() = verify(DeviceRole.COFFEE, DeviceState.DISCONNECTED)
    @Test fun scaleDisconnectStillClosesAfterObserverFailure() = verify(DeviceRole.BOOKOO, DeviceState.DISCONNECTED)
    @Test fun coffeeLinkFailureStillClosesAfterObserverFailure() = verify(DeviceRole.COFFEE, DeviceState.FAILED)
    @Test fun scaleLinkFailureStillClosesAfterObserverFailure() = verify(DeviceRole.BOOKOO, DeviceState.FAILED)
}
