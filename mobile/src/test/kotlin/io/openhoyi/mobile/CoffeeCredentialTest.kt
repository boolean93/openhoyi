package io.openhoyi.mobile

import javax.crypto.spec.SecretKeySpec
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
}
