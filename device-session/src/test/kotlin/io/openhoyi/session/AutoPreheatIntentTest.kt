package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class AutoPreheatIntentTest {
    @Test fun leaveIsOnceAndRevokesPositiveWriteBeforeCancellation() {
        val device = Any()
        val intent = AutoPreheatIntent("a", 92, 7, device, 1)
        assertTrue(intent.permitsStart(device, 1))
        assertTrue(intent.leave(100))
        assertFalse(intent.permitsStart(device, 1))
        assertFalse(intent.leave(101))
        assertTrue(intent.permitsCancel(device, 1, 101))
        assertFalse(intent.permitsCancel(device, 1, 1601))
        assertFalse(intent.permitsCancel(device, 1, 99))
    }

    @Test fun replacementConnectionAndConfigurationCannotReceiveOldWrites() {
        val device = Any()
        val intent = AutoPreheatIntent("a", 92, 7, device, 1)
        assertFalse(intent.permitsStart(Any(), 1))
        assertFalse(intent.permitsStart(device, 2))
        assertTrue(intent.leave(100))
        assertFalse(intent.permitsCancel(Any(), 1, 101))
        assertFalse(intent.permitsCancel(device, 2, 101))
        assertFalse(intent.permitsCancel(null, 1, 101))
    }

    @Test fun observedExtractionOrDisconnectionPermanentlyRevokesEvenIfStateReturns() {
        val device = Any()
        val intent = AutoPreheatIntent("a", 92, 7, device, 1)
        intent.invalidate()
        assertFalse(intent.permitsStart(device, 1))
        assertTrue(intent.leave(100))
        assertFalse(intent.permitsCancel(device, 1, 101))
    }
}
