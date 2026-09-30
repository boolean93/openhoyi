package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import org.junit.Assert.*
import org.junit.Test

class MobileSnapshotMessageTest {
    @Test fun initialMessageUsesDisplayResourcesWithoutChangingConnectionState() {
        val snapshot = MobileSnapshot()
        assertNull(snapshot.message)
        assertEquals("translated initial", snapshot.messageForDisplay { id ->
            assertEquals(R.string.device_initial_message, id)
            "translated initial"
        })
        assertEquals(DeviceState.DISCONNECTED, snapshot.coffeeState)
        assertEquals(DeviceState.DISCONNECTED, snapshot.scaleState)
        assertNull(snapshot.message)
    }

    @Test fun existingEventIncludingEmptyTranslationIsNeverReplacedByInitialHint() {
        for (message in listOf("", "结果未知", "message %1\$s")) {
            val snapshot = MobileSnapshot(message = message)
            assertEquals(message, snapshot.messageForDisplay { error("Unexpected fallback") })
            assertEquals(message, snapshot.copy(scanning = true).message)
        }
    }
}
