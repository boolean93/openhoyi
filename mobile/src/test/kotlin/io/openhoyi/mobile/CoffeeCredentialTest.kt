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

    @Test fun rememberedPasswordFallsBackAfterTwoFailedAttempts() {
        val gate = CoffeeCredentialRetryGate()
        val address = "AA:BB:CC:DD:EE:01"
        assertTrue(gate.mayUse(address))
        gate.failed(address); assertTrue(gate.mayUse(address))
        gate.failed(address.lowercase()); assertFalse(gate.mayUse(address))
        gate.succeeded(address.lowercase()); assertTrue(gate.mayUse(address))
    }

    @Test fun unsupportedFirmwareDoesNotConsumeRememberedCredentialFailures() {
        val gate = CoffeeCredentialRetryGate()
        val address = "AA:BB:CC:DD:EE:01"
        gate.failed(address)
        repeat(4) { gate.connectionState(address, DeviceState.UNSUPPORTED, true) }
        assertTrue(gate.mayUse(address))
        gate.connectionState(address, DeviceState.FAILED, true)
        assertFalse(gate.mayUse(address))
    }
    @Test fun onlyRememberedConnectionFailuresAffectFallbackAndReadyResetsThem() {
        val gate = CoffeeCredentialRetryGate()
        val address = "AA:BB:CC:DD:EE:01"
        gate.failed(address)
        for(state in listOf(DeviceState.CONNECTING, DeviceState.DISCONNECTED,
            DeviceState.SYNCHRONIZING)) gate.connectionState(address,state,true)
        gate.connectionState(address, DeviceState.FAILED, false)
        assertTrue(gate.mayUse(address))
        gate.connectionState(address, DeviceState.FAILED, true)
        assertFalse(gate.mayUse(address))
        gate.connectionState("AA:BB:CC:DD:EE:02", DeviceState.READY, true)
        assertFalse(gate.mayUse(address))
        gate.connectionState(address, DeviceState.READY, false)
        assertTrue(gate.mayUse(address))
    }
    @Test fun failureCountSurvivesAServiceRestart() {
        val counts = mutableMapOf<String, Int>()
        val store = object : CoffeeCredentialFailureStore {
            override fun read(address: String) = counts[address] ?: 0
            override fun write(address: String, count: Int) { counts[address] = count }
        }
        val address = "AA:BB:CC:DD:EE:01"
        CoffeeCredentialRetryGate(store = store).failed(address)
        val resumed = CoffeeCredentialRetryGate(store = store)
        assertTrue(resumed.mayUse(address.lowercase()))
        resumed.failed(address.lowercase())
        assertFalse(CoffeeCredentialRetryGate(store = store).mayUse(address))
        resumed.succeeded(address)
        assertTrue(CoffeeCredentialRetryGate(store = store).mayUse(address))
    }
    @Test fun automaticCallbackStopsAfterRememberedCredentialFailureLimit() {
        val address="AA:BB:CC:DD:EE:01"
        val credential=CoffeeCredentialRetryGate()
        val reconnect=CoffeeReconnectPolicy()
        reconnect.foreground(0);reconnect.ensureKnown(address,0)
        var calls=0
        fun attempt(now:Long):CoffeeReconnectPolicy.Dispatch = reconnect.dispatch(now,true,true) { requested ->
            assertEquals(address,requested)
            if(!credential.mayUse(requested)) {reconnect.disable();false}
            else {calls++;true}
        }
        assertEquals(CoffeeReconnectPolicy.Dispatch.STARTED,attempt(0))
        credential.connectionState(address,DeviceState.FAILED,true);reconnect.connectionFailed(100)
        assertEquals(CoffeeReconnectPolicy.Dispatch.STARTED,attempt(5100))
        credential.connectionState(address,DeviceState.FAILED,true);reconnect.connectionFailed(5200)
        assertEquals(CoffeeReconnectPolicy.Dispatch.REJECTED,attempt(15200))
        assertEquals(CoffeeReconnectPolicy.Dispatch.NONE,attempt(45200))
        assertEquals(2,calls)
    }
}
