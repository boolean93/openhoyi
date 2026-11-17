package io.openhoyi.mobile

import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test

/** JVM AES and ciphertext-only fake storage, not Android Keystore/noBackup evidence. */
class AiAttachmentVaultTest {
    private class Storage : AiAttachmentStorage {
        val entries = mutableMapOf<String, ByteArray>()
        var failWrite = false
        override fun hasAny() = entries.isNotEmpty()
        override fun writeNew(id: String, ciphertext: ByteArray) { check(!failWrite && id !in entries); entries[id] = ciphertext.copyOf() }
        override fun read(id: String) = entries.getValue(id).copyOf()
        override fun remove(id: String) { entries.remove(id) }
    }
    private class Keys : AiKeySource {
        var key: SecretKey? = null; var creates = 0
        override fun existing() = key
        override fun create(): SecretKey { creates++; return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it } }
    }
    private val epoch = UUID.randomUUID().toString()
    private fun fixture() = "synthetic private 中文".toByteArray().let { AiPreparedAttachment(AiAttachment(UUID.randomUUID().toString(), "private.txt", "text/plain", it.size, AiAttachmentKind.TEXT), it) }
    private fun unavailable(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure is AiAttachmentFailure); assertEquals("UNAVAILABLE", failure!!.message); assertNull(failure.cause)
    }
    @Test fun roundTripSealsMetadataAndContentAndRemovesOnlyOwnedId() {
        val storage = Storage(); val keys = Keys(); val vault = AiAttachmentVault(storage, keys, epoch)
        fixture().use { a ->
            vault.save(a)
            assertFalse(storage.entries.getValue(a.attachment.id).toString(Charsets.UTF_8).contains("private"))
            AiAttachmentVault(storage, keys, epoch).load(a.attachment).use { b -> assertArrayEquals(a.bytes, b.bytes) }
            assertEquals(1, keys.creates)
            val unrelated = UUID.randomUUID().toString(); storage.entries[unrelated] = byteArrayOf(1, 2)
            assertTrue(vault.remove(a.attachment)); assertTrue(unrelated in storage.entries)
        }
    }
    @Test fun wrongEpochTamperAndMetadataMismatchFailClosed() {
        val storage = Storage(); val keys = Keys(); val vault = AiAttachmentVault(storage, keys, epoch)
        fixture().use { a ->
            vault.save(a)
            val wrongOwner = AiAttachmentVault(storage, keys, UUID.randomUUID().toString())
            unavailable { wrongOwner.load(a.attachment) }
            assertFalse(wrongOwner.remove(a.attachment)); assertTrue(a.attachment.id in storage.entries)
            val renamed = AiAttachment(a.attachment.id, "other.txt", "text/plain", a.attachment.size, AiAttachmentKind.TEXT)
            unavailable { vault.load(renamed) }
            assertFalse(vault.remove(renamed)); assertTrue(a.attachment.id in storage.entries)
            val bytes = storage.entries.getValue(a.attachment.id); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            unavailable { vault.load(a.attachment) }
            assertFalse(vault.remove(a.attachment)); assertTrue(a.attachment.id in storage.entries)
        }
    }
    @Test fun missingKeyNeverRecreatesOrOverwritesExistingCiphertext() {
        val storage = Storage(); val keys = Keys(); val vault = AiAttachmentVault(storage, keys, epoch)
        fixture().use { a ->
            vault.save(a); val saved = storage.entries.getValue(a.attachment.id).copyOf(); keys.key = null
            unavailable { vault.load(a.attachment) }
            fixture().use { b -> unavailable { vault.save(b) } }
            assertEquals(1, keys.creates); assertArrayEquals(saved, storage.entries.getValue(a.attachment.id))
        }
    }
    @Test fun duplicateWriteAndStorageFailureDoNotClaimSuccess() {
        val storage = Storage(); val keys = Keys(); val vault = AiAttachmentVault(storage, keys, epoch)
        fixture().use { a -> vault.save(a); unavailable { vault.save(a) } }
        storage.failWrite = true
        fixture().use { unavailable { vault.save(it) } }
        assertEquals(1, storage.entries.size)
    }
    @Test fun copiedEncryptedAttachmentRestoresWithoutReopeningRevokedSourceAndMissingCopyFailsClosed() {
        val storage = Storage(); val keys = Keys(); val vault = AiAttachmentVault(storage, keys, epoch)
        var sourceOpens = 0; var revoked = false
        val raw = "synthetic draft source".toByteArray()
        val preparer = AiAttachmentPreparer { _, _ -> error("Not an image") }
        val prepared = preparer.prepare("draft.txt", "text/plain", raw.size.toLong(), emptyList()) {
            sourceOpens++; check(!revoked); java.io.ByteArrayInputStream(raw)
        }
        val item = prepared.attachment
        prepared.use { vault.save(it) }
        revoked = true
        val encoded = AiSessionCodec.encode(AiSession(epoch, emptyList(), AiProtocolHistory.empty(), "unsent draft", AiUsageSnapshot(), listOf(item)))
        assertFalse(encoded.toString(Charsets.UTF_8).contains("content://"))
        val recovered = AiSessionCodec.decode(encoded)
        val metadata = recovered.draftAttachments.single()
        AiAttachmentVault(storage, keys, epoch).load(metadata).use { assertArrayEquals(raw, it.bytes) }
        assertEquals(1, sourceOpens); assertEquals("unsent draft", recovered.draft)
        val valid = storage.entries.getValue(item.id).copyOf()
        storage.entries[item.id] = valid.copyOf(10) // Interrupted/corrupt ciphertext is retained, never treated as a source URI.
        unavailable { AiAttachmentVault(storage, keys, epoch).load(metadata) }
        assertFalse(vault.remove(metadata)); assertTrue(item.id in storage.entries)
        storage.entries.remove(item.id)
        unavailable { AiAttachmentVault(storage, keys, epoch).load(metadata) }
        assertEquals(1, sourceOpens); assertEquals(item.id, recovered.draftAttachments.single().id)
    }

    @Test fun transactionExitFailureWipesUnreturnedPlaintext() {
        val storage = Storage(); val keys = Keys(); var failExit = false; var unreturned: ByteArray? = null
        val failing = object : AiAttachmentStorage by storage {
            override fun <T> transaction(action: () -> T): T {
                val result = action()
                if (failExit) {
                    if (result is AiPreparedAttachment) unreturned = result.bytes
                    throw java.io.IOException("synthetic lock release failure")
                }
                return result
            }
        }
        val vault = AiAttachmentVault(failing, keys, epoch)
        fixture().use { a ->
            vault.save(a); failExit = true
            unavailable { vault.load(a.attachment) }
            assertNotNull(unreturned); assertTrue(unreturned!!.all { it == 0.toByte() })
            assertTrue(a.attachment.id in storage.entries)
        }
    }

    @Test fun failedStoreRecordsOnlyClosedStageAndFailureKind() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val storage = Storage().apply { failWrite = true }
        val trace = AiAttachmentTrace { kind, fields -> events += kind to fields }
        val vault = AiAttachmentVault(storage, Keys(), epoch, trace)
        fixture().use { unavailable { vault.save(it) } }
        val failure = events.last { it.first == "ai.attachment.failed" }.second
        assertEquals("STORE_WRITE", failure["stage"])
        assertEquals("STATE", failure["failureKind"])
        assertEquals(setOf("stage", "problem", "failureKind"), failure.keys)
        assertFalse(failure.toString().contains("private"))
    }

}
