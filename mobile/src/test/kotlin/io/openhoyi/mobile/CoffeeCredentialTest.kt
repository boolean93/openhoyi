package io.openhoyi.mobile

import javax.crypto.spec.SecretKeySpec
import io.openhoyi.session.DeviceState
import io.openhoyi.session.CoffeeReconnectPolicy
import org.junit.Assert.*
import org.junit.Test

class CoffeeCredentialTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test fun encryptedPasswordIsBoundToItsDevice() {
        val address = "AA:BB:CC:DD:EE:01"
        val encoded = CoffeeCredentialCodec.encrypt(key, address, "123456")
        assertFalse(encoded.contains("123456"))
        assertEquals("123456", CoffeeCredentialCodec.decrypt(key, address, encoded))
        assertNull(CoffeeCredentialCodec.decrypt(key, "AA:BB:CC:DD:EE:02", encoded))
        assertNull(CoffeeCredentialCodec.decrypt(key, address, "invalid"))
    }

    @Test fun ordinaryFailuresNeverInvalidateRememberedPassword() {
        val gate = CoffeeCredentialRetryGate()
        val address = "AA:BB:CC:DD:EE:01"
        repeat(5) { gate.connectionState(address, DeviceState.FAILED, true) }
        assertTrue(gate.mayUse(address.lowercase()))
    }

    @Test fun legacyFailureCountsDoNotBlockOrChangeStoredHistory() {
        val address = "AA:BB:CC:DD:EE:01"
        val counts = mutableMapOf(address to 2)
        val store = object : CoffeeCredentialFailureStore {
            override fun read(address: String) = counts[address] ?: 0
            override fun write(address: String, count: Int) { counts[address] = count }
        }
        val resumed = CoffeeCredentialRetryGate(store = store)
        assertTrue(resumed.mayUse(address.lowercase()))
        resumed.connectionState(address, DeviceState.FAILED, true)
        resumed.connectionState(address, DeviceState.READY, true)
        assertEquals(2, counts[address])
    }

    @Test fun automaticCallbackUsesFastRecoveryAfterRepeatedFailures() {
        val address = "AA:BB:CC:DD:EE:01"
        val credential = CoffeeCredentialRetryGate()
        val reconnect = CoffeeReconnectPolicy()
        reconnect.foreground(0); reconnect.ensureKnown(address, 0)
        var calls = 0
        fun attempt(now: Long): CoffeeReconnectPolicy.Dispatch = reconnect.dispatch(now, true, true) { requested ->
            assertEquals(address, requested)
            if (!credential.mayUse(requested)) { reconnect.disable(); false }
            else { calls++; true }
        }
        assertEquals(CoffeeReconnectPolicy.Dispatch.STARTED, attempt(0))
        credential.connectionState(address, DeviceState.FAILED, true); reconnect.connectionFailed(100)
        assertEquals(CoffeeReconnectPolicy.Dispatch.NONE, attempt(2099))
        assertEquals(CoffeeReconnectPolicy.Dispatch.STARTED, attempt(2100))
        credential.connectionState(address, DeviceState.FAILED, true); reconnect.connectionFailed(2200)
        assertEquals(CoffeeReconnectPolicy.Dispatch.NONE, attempt(5199))
        assertEquals(CoffeeReconnectPolicy.Dispatch.STARTED, attempt(5200))
        assertEquals(3, calls)
    }

    @Test fun onlyMissingCredentialsRequestPassword() {
        assertEquals(RememberedCoffeeConnection.PASSWORD_REQUIRED,
            RememberedCoffeeConnection.attempt(true, { CoffeeCredentialRead.Missing }) { error("No password") })
        assertEquals(RememberedCoffeeConnection.BLOCKED,
            RememberedCoffeeConnection.attempt(true, { CoffeeCredentialRead.Unavailable }) { error("No password") })
        assertEquals(RememberedCoffeeConnection.BLOCKED,
            RememberedCoffeeConnection.attempt(false, { error("Blocked before credential access") }) { error("Blocked") })
        assertEquals(RememberedCoffeeConnection.STARTED,
            RememberedCoffeeConnection.attempt(true, { CoffeeCredentialRead.Available("123456") }) {
                assertEquals("123456", it); true
            })
        assertEquals(RememberedCoffeeConnection.BLOCKED,
            RememberedCoffeeConnection.attempt(true, { CoffeeCredentialRead.Available("123456") }) { false })
    }

    @Test fun missingAndTemporarilyUnreadableCredentialsStayDistinct() {
        val address = "AA:BB:CC:DD:EE:01"
        val encoded = CoffeeCredentialCodec.encrypt(key, address, "123456")
        assertEquals(CoffeeCredentialRead.Missing,
            CoffeeCredentialCodec.read(null, address) { error("Missing has no key") })
        assertEquals(CoffeeCredentialRead.Unavailable,
            CoffeeCredentialCodec.read(encoded, address) { throw java.security.KeyStoreException("Locked") })
        assertEquals(CoffeeCredentialRead.Available("123456"),
            CoffeeCredentialCodec.read(encoded, address) { key })
        assertEquals(CoffeeCredentialRead.Unavailable,
            CoffeeCredentialCodec.read("invalid", address) { key })
    }

    @Test fun newPasswordIsEligibleForSavingOnlyAfterSameDeviceReady() {
        val address = "AA:BB:CC:DD:EE:01"
        val pending = PendingCoffeeCredential(address, "654321", false)
        assertNull(pending.passwordToSave(address, DeviceState.FAILED))
        assertNull(pending.passwordToSave("AA:BB:CC:DD:EE:02", DeviceState.READY))
        assertNull(pending.passwordToSave(null, DeviceState.READY))
        assertEquals("654321", pending.passwordToSave(address.lowercase(), DeviceState.READY))
        assertNull(pending.copy(remembered = true).passwordToSave(address, DeviceState.READY))
    }


    @Test fun credentialObjectsNeverPrintPasswords() {
        assertFalse(CoffeeCredentialRead.Available("123456").toString().contains("123456"))
        assertFalse(PendingCoffeeCredential("AA:BB:CC:DD:EE:01", "654321", false).toString().contains("654321"))
    }

    @Test fun automaticCallbackRetriesTemporarilyUnreadableCredentials() {
        val reconnect = CoffeeReconnectPolicy()
        reconnect.foreground(0); reconnect.ensureKnown("AA:BB:CC:DD:EE:01", 0)
        var readable = false
        var calls = 0
        fun attempt(now: Long) = reconnect.dispatch(now, true, true) {
            val result = RememberedCoffeeConnection.attempt(true, {
                if (readable) CoffeeCredentialRead.Available("123456") else CoffeeCredentialRead.Unavailable
            }) { calls++; true }
            if (result == RememberedCoffeeConnection.PASSWORD_REQUIRED) reconnect.disable()
            result == RememberedCoffeeConnection.STARTED
        }
        assertEquals(CoffeeReconnectPolicy.Dispatch.REJECTED, attempt(0))
        readable = true
        assertEquals(CoffeeReconnectPolicy.Dispatch.NONE, attempt(1999))
        assertEquals(CoffeeReconnectPolicy.Dispatch.STARTED, attempt(2000))
        assertEquals(1, calls)
    }

}
