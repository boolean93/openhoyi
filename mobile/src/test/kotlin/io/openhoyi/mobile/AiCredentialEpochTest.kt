package io.openhoyi.mobile

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AiCredentialEpochTest {
    private class Storage : AiCiphertextStorage {
        var ciphertext: String? = "legacy-sealed-value"
        var epoch: String? = null
        var reads = 0
        var writes = 0
        var succeed = true
        override fun contains() = ciphertext != null
        override fun read(): String? { reads++; return ciphertext }
        override fun write(ciphertext: String?): Boolean {
            writes++; this.ciphertext = ciphertext; epoch = UUID.randomUUID().toString()
            return succeed
        }
        override fun credentialEpoch(): String = AiCredentialEpochPolicy.resolve(epoch)
    }
    private class Keys : AiKeySource {
        var key: SecretKey? = null
        var reads = 0
        override fun existing(): SecretKey? { reads++; return key }
        override fun create(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
    }
    private val storage = Storage()
    private val keys = Keys()
    private val store = AiKeyStore(storage, keys)
    @After fun clearFence() { storage.succeed = true; store.delete() }
    @Test fun legacyEpochDoesNotReadCredentialKeystoreOrWriteMetadata() {
        store.delete() // Reset process fence left by another fixture.
        storage.ciphertext = "legacy-sealed-value"; storage.epoch = null
        val writes = storage.writes
        val epoch = store.credentialEpoch()
        assertNotNull(epoch); assertEquals(epoch, AiKeyStore(storage, keys).credentialEpoch())
        assertEquals(AiCredentialEpochPolicy.LEGACY, epoch); assertNull(storage.epoch)
        assertEquals(writes, storage.writes)
        assertEquals(0, storage.reads); assertEquals(0, keys.reads)
    }
    @Test fun replacementDeletionAndFailedCommitFence() {
        assertTrue(store.delete()); val before = store.credentialEpoch()
        assertTrue(store.save("fake-key-not-real")); val replacement = store.credentialEpoch()
        assertNotEquals(before, replacement)
        assertTrue(store.delete()); assertNotEquals(replacement, store.credentialEpoch())
        storage.succeed = false
        assertFalse(store.save("another-fake-key"))
        // SharedPreferences can expose a new in-memory epoch after a failed disk commit.
        assertNotNull(storage.epoch)
        assertNull(AiKeyStore(storage, keys).credentialEpoch())
        storage.succeed = true
        assertTrue(store.delete()); assertNotNull(store.credentialEpoch())
    }
    @Test fun existingEpochIsPreservedAndMalformedEpochFailsClosed() {
        store.delete()
        val valid = storage.epoch
        val writes = storage.writes
        assertEquals(valid, store.credentialEpoch()); assertEquals(writes, storage.writes)
        storage.epoch = "invalid"
        assertNull(store.credentialEpoch()); assertEquals(writes, storage.writes)
        assertEquals(0, storage.reads); assertEquals(0, keys.reads)
    }
    @Test fun missingKeyCannotSendAndExplicitSaveLeavesLegacyEpoch() {
        store.delete(); storage.epoch = null
        assertEquals(AiCredentialEpochPolicy.LEGACY, store.credentialEpoch())
        assertEquals(AiKeyStatus.MISSING, store.status())
        assertTrue(runCatching { store.readForRequest() }.isFailure)
        assertTrue(store.save("fake-only-new-key"))
        assertNotEquals(AiCredentialEpochPolicy.LEGACY, store.credentialEpoch())
        val saved = store.credentialEpoch()
        assertTrue(store.delete()); assertNotEquals(saved, store.credentialEpoch())
    }

}
