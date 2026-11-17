package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class AiKeyCodecTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun encryptedWithFreshIvAndRoundTrips() {
        val key = key(); val token = "fake-token-never-a-real-key"
        val a = AiKeyCodec.encrypt(key, token); val b = AiKeyCodec.encrypt(key, token)
        assertNotEquals(a, b); assertFalse(a.contains(token)); assertEquals(token, AiKeyCodec.decrypt(key, a))
    }
    @Test fun tamperingAndWrongKeyNeverReturnPlaintext() {
        val key = key(); val blob = AiKeyCodec.encrypt(key, "fake-token-not-real")
        val bytes = java.util.Base64.getDecoder().decode(blob); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertTrue(runCatching { AiKeyCodec.decrypt(key, java.util.Base64.getEncoder().encodeToString(bytes)) }.isFailure)
        assertTrue(runCatching { AiKeyCodec.decrypt(key(), blob) }.isFailure)
        assertTrue(runCatching { AiKeyCodec.decrypt(key, "invalid") }.isFailure)
    }
    @Test fun boundedAsciiOnly() {
        assertFalse(AiKeyCodec.valid("")); assertFalse(AiKeyCodec.valid("abc\n123456")); assertFalse(AiKeyCodec.valid("k".repeat(513)))
        assertTrue(AiKeyCodec.valid("fake-key-123"))
    }
}
