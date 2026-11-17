package io.openhoyi.mobile

import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** JVM fake storage/AES only: not evidence of Android Keystore or real process-restart behavior. */
class AiKeyStoreTest {
    private class MemoryCiphertext : AiCiphertextStorage {
        var ciphertext: String? = null
        var commitSucceeds = true
        var readFailure = false
        var containsFailure = false
        var writeFailure = false
        var writeException: Exception? = null
        var reads = 0
        var writes = 0
        override fun contains(): Boolean {
            if (containsFailure) error("fake-private-storage-error")
            return ciphertext != null
        }
        override fun read(): String? {
            reads++
            if (readFailure) error("fake-private-storage-error")
            return ciphertext
        }
        override fun write(ciphertext: String?): Boolean {
            writes++
            writeException?.let { throw it }
            if (writeFailure) error("fake-private-storage-error")
            this.ciphertext = ciphertext // Models SharedPreferences memory changing before failed disk commit.
            return commitSucceeds
        }
    }
    private class FakeKeys : AiKeySource {
        var key: SecretKey? = null
        var unavailable = false
        var creates = 0
        var replacements = 0
        var replacementUnavailable = false
        val unrelatedCredential = byteArrayOf(17, 29, 41)
        override fun existing(): SecretKey? {
            if (unavailable) error("fake-private-provider-error")
            return key
        }
        override fun create(): SecretKey {
            creates++
            if (unavailable) error("fake-private-provider-error")
            return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        }
        override fun replacePermanentlyInvalidated(): SecretKey {
            replacements++
            key = null
            if (replacementUnavailable) throw KeyStoreException("fake-transient-provider-error")
            return create()
        }
    }
    private class FailingKey(private val failure: Exception) : SecretKey {
        override fun getAlgorithm() = "AES"
        override fun getFormat() = "RAW"
        override fun getEncoded(): ByteArray = throw failure
    }
    private lateinit var storage: MemoryCiphertext
    private lateinit var keys: FakeKeys
    private lateinit var store: AiKeyStore
    private val token = "fake-key-not-a-real-credential"

    @Before fun setUp() {
        storage = MemoryCiphertext(); keys = FakeKeys(); store = AiKeyStore(storage, keys)
        assertTrue(store.delete()) // Reset the shared process mutation fence without touching Android.
    }
    @After fun tearDown() {
        storage.commitSucceeds = true
        storage.writeFailure = false
        storage.writeException = null
        store.delete()
    }
    private fun assertSafeKeyFailure(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure is AiFailure)
        assertEquals(AiProblem.KEY, (failure as AiFailure).problem)
        assertEquals("KEY", failure.message)
        assertNull(failure.cause)
    }
    @Test fun statusDoesNotReadOrDecryptCiphertext() {
        assertEquals(AiKeyStatus.MISSING, store.status())
        keys.create(); storage.ciphertext = "not-even-valid-base64"
        assertEquals(AiKeyStatus.SAVED, store.status())
        assertEquals(0, storage.reads)
        assertSafeKeyFailure { store.readForRequest() }
    }
    @Test fun saveAndReconstructedStoreReadCiphertextOnly() {
        assertTrue(store.save(token))
        assertNotNull(storage.ciphertext)
        assertFalse(storage.ciphertext!!.contains(token))
        val reconstructed = AiKeyStore(storage, keys)
        assertEquals(AiKeyStatus.SAVED, reconstructed.status())
        assertEquals(token, reconstructed.readForRequest())
        assertEquals(1, keys.creates)
    }
    @Test fun missingAliasNeverCreatesOnReadAndPreservesCiphertext() {
        assertTrue(store.save(token))
        val blob = storage.ciphertext
        val writes = storage.writes
        keys.key = null
        assertEquals(AiKeyStatus.UNAVAILABLE, store.status())
        assertSafeKeyFailure { store.readForRequest() }
        assertEquals(1, keys.creates)
        assertEquals(blob, storage.ciphertext)
        assertEquals(writes, storage.writes)
    }
    @Test fun wrongKeyCorruptionAndUnavailableKeySourceFailClosed() {
        assertTrue(store.save(token))
        val blob = storage.ciphertext
        val original = keys.key
        keys.create()
        assertSafeKeyFailure { store.readForRequest() }
        assertEquals(blob, storage.ciphertext)
        keys.key = original
        storage.ciphertext = "corrupt"
        assertSafeKeyFailure { store.readForRequest() }
        assertEquals("corrupt", storage.ciphertext)
        storage.ciphertext = blob
        keys.unavailable = true
        assertEquals(AiKeyStatus.UNAVAILABLE, store.status())
        assertSafeKeyFailure { store.readForRequest() }
        assertEquals(blob, storage.ciphertext)
    }
    @Test fun replacementAndDeletionInvalidateOldRequestsAndNotifyAcrossInstances() {
        assertTrue(store.save(token))
        val other = AiKeyStore(storage, keys)
        var notifications = 0
        val unsubscribe = other.observeMutation { notifications++ }
        try {
            val beforeReplace = store.revision()
            assertTrue(other.save("fake-replacement-key"))
            assertSafeKeyFailure { store.readForRequest(beforeReplace) }
            assertEquals("fake-replacement-key", store.readForRequest())
            val beforeDelete = store.revision()
            assertTrue(other.delete())
            assertSafeKeyFailure { store.readForRequest(beforeDelete) }
            assertSafeKeyFailure { store.readForRequest() }
            assertEquals(AiKeyStatus.MISSING, store.status())
            assertEquals(2, notifications)
        } finally { unsubscribe() }
        assertTrue(other.delete())
        assertEquals(2, notifications)
    }
    @Test fun failedCommitLocksChangedMemoryUntilSuccessfulReconfiguration() {
        assertTrue(store.save(token))
        val before = store.revision()
        var notifications = 0
        val unsubscribe = store.observeMutation { notifications++ }
        try {
            storage.commitSucceeds = false
            assertFalse(store.save("fake-unsaved-new-key"))
            assertEquals("fake-unsaved-new-key", AiKeyCodec.decrypt(keys.key!!, storage.ciphertext!!))
            val reconstructed = AiKeyStore(storage, keys)
            assertEquals(AiKeyStatus.UNAVAILABLE, reconstructed.status())
            assertSafeKeyFailure { reconstructed.readForRequest(before) }
            assertSafeKeyFailure { reconstructed.readForRequest() }
            assertEquals(1, notifications)
            storage.commitSucceeds = true
            assertTrue(reconstructed.save(token))
            assertEquals(AiKeyStatus.SAVED, store.status())
            assertEquals(token, store.readForRequest())
        } finally { unsubscribe() }
    }
    @Test fun storageExceptionsNeverEscapeWithPrivateCause() {
        assertTrue(store.save(token))
        storage.containsFailure = true
        assertEquals(AiKeyStatus.UNAVAILABLE, store.status())
        storage.containsFailure = false
        storage.readFailure = true
        assertSafeKeyFailure { store.readForRequest() }
        storage.readFailure = false
        storage.writeFailure = true
        assertFalse(store.save("fake-replacement-key"))
        assertEquals(AiKeyStatus.UNAVAILABLE, store.status())
        assertSafeKeyFailure { store.readForRequest() }
        assertFalse(store.delete())
    }
    @Test fun failedDeleteAlsoLocksMissingMemoryUntilSuccessfulDelete() {
        assertTrue(store.save(token))
        storage.commitSucceeds = false
        assertFalse(store.delete())
        assertNull(storage.ciphertext)
        val reconstructed = AiKeyStore(storage, keys)
        assertEquals(AiKeyStatus.UNAVAILABLE, reconstructed.status())
        assertSafeKeyFailure { reconstructed.readForRequest() }
        storage.commitSucceeds = true
        assertTrue(reconstructed.delete())
        assertEquals(AiKeyStatus.MISSING, store.status())
    }
    @Test fun permanentInvalidationRecoversOnlyOnExplicitResave() {
        assertTrue(store.save(token))
        val original = storage.ciphertext
        keys.key = FailingKey(KeyPermanentlyInvalidatedException())
        assertSafeKeyFailure { store.readForRequest() }
        assertEquals(original, storage.ciphertext)
        assertEquals(0, keys.replacements)
        val sibling = keys.unrelatedCredential.copyOf()
        assertTrue(store.save("fake-reentered-credential"))
        assertEquals(1, keys.replacements)
        assertEquals("fake-reentered-credential", store.readForRequest())
        assertNotEquals(original, storage.ciphertext)
        assertArrayEquals(sibling, keys.unrelatedCredential)
        // Clearing saved ciphertext never resets an alias; a subsequent explicit save still recovers.
        keys.key = FailingKey(InvalidKeyException("wrapped").apply { initCause(KeyPermanentlyInvalidatedException()) })
        assertTrue(store.delete())
        assertEquals(1, keys.replacements)
        assertTrue(store.save(token))
        assertEquals(2, keys.replacements)
        assertEquals(token, store.readForRequest())
        assertArrayEquals(sibling, keys.unrelatedCredential)
    }
    @Test fun transientInvalidKeyAndProviderFailuresNeverReplaceAlias() {
        assertTrue(store.save(token))
        val original = storage.ciphertext
        val material = keys.key
        keys.key = FailingKey(InvalidKeyException("permanently invalidated (untrusted text)"))
        assertFalse(store.save("fake-reentered-credential"))
        assertEquals(0, keys.replacements)
        assertEquals(original, storage.ciphertext)
        assertEquals(1, keys.creates)
        keys.key = material
        keys.unavailable = true
        assertFalse(store.save(token))
        assertEquals(0, keys.replacements)
        assertEquals(original, storage.ciphertext)
        keys.unavailable = false
        assertTrue(store.save(token))
        assertEquals(token, store.readForRequest())
        assertEquals(0, keys.replacements)
    }
    @Test fun failedPermanentRecoveryRetainsCiphertextAndRequiresExplicitRetry() {
        assertTrue(store.save(token))
        val original = storage.ciphertext
        keys.key = FailingKey(KeyPermanentlyInvalidatedException())
        keys.replacementUnavailable = true
        assertFalse(store.save("fake-reentered-credential"))
        assertEquals(original, storage.ciphertext)
        assertEquals(AiKeyStatus.UNAVAILABLE, store.status())
        assertSafeKeyFailure { store.readForRequest() }
        assertEquals(1, keys.replacements)
        keys.replacementUnavailable = false
        assertTrue(store.save("fake-reentered-credential"))
        assertEquals("fake-reentered-credential", store.readForRequest())
        assertEquals(1, keys.replacements)
    }
    @Test fun storageExceptionCannotAuthorizeAliasReplacement() {
        assertTrue(store.save(token))
        val original = storage.ciphertext
        val material = keys.key
        storage.writeException = KeyPermanentlyInvalidatedException()
        assertFalse(store.save("fake-reentered-credential"))
        assertEquals(0, keys.replacements)
        assertSame(material, keys.key)
        assertEquals(original, storage.ciphertext)
        assertSafeKeyFailure { store.readForRequest() }
    }

}
