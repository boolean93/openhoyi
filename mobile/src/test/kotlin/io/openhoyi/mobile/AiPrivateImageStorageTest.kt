package io.openhoyi.mobile

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test

class AiPrivateImageStorageTest {
    private val epoch = UUID.randomUUID().toString()
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
    private fun image() = AiPreparedAttachment(AiAttachment(UUID.randomUUID().toString(), "sample.jpg", "image/jpeg", jpeg.size, AiAttachmentKind.IMAGE), jpeg.copyOf())
    private class Memory : AiAttachmentAddressableStorage {
        val entries = mutableMapOf<String, ByteArray>()
        override fun exists(id: String) = id in entries
        override fun hasAny() = entries.isNotEmpty()
        override fun writeNew(id: String, ciphertext: ByteArray) { check(id !in entries); entries[id] = ciphertext.copyOf() }
        override fun read(id: String) = entries.getValue(id).copyOf()
        override fun remove(id: String) { entries.remove(id) }
    }
    private class Keys : AiKeySource {
        var key: SecretKey? = null; var calls = 0
        override fun existing(): SecretKey? { calls++; return key }
        override fun create(): SecretKey { calls++; return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it } }
    }
    private fun unavailable(action: () -> Unit) {
        val error = runCatching(action).exceptionOrNull()
        assertTrue(error is AiAttachmentFailure); assertEquals("UNAVAILABLE", error!!.message); assertNull(error.cause)
    }
    @Test fun newImagesNeedNoKeyAndFirstTextKeyIsNotBlockedByImages() {
        val legacy = Memory(); val images = Memory(); val keys = Keys()
        val vault = AiAttachmentVault(legacy, keys, epoch, images = images)
        image().use { prepared ->
            vault.save(prepared); assertEquals(0, keys.calls); assertFalse(legacy.hasAny())
            vault.load(prepared.attachment).use { assertArrayEquals(jpeg, it.bytes) }
            assertEquals(0, keys.calls)
            val text = "synthetic".toByteArray()
            AiPreparedAttachment(AiAttachment(UUID.randomUUID().toString(), "sample.txt", "text/plain", text.size, AiAttachmentKind.TEXT), text).use {
                vault.save(it); assertTrue(keys.calls > 0); assertTrue(legacy.exists(it.attachment.id))
                assertFalse(images.exists(it.attachment.id))
                vault.load(it.attachment).use { loaded -> assertArrayEquals(text, loaded.bytes) }
            }
            assertTrue(vault.remove(prepared.attachment)); assertFalse(images.exists(prepared.attachment.id))
        }
    }
    @Test fun legacyImagesStillDecryptAndCorruptV2NeverFallsBack() {
        val legacy = Memory(); val images = Memory(); val keys = Keys()
        image().use { prepared ->
            AiAttachmentVault(legacy, keys, epoch).save(prepared)
            val routed = AiAttachmentVault(legacy, keys, epoch, images = images)
            routed.load(prepared.attachment).use { assertArrayEquals(jpeg, it.bytes) }
            AiPrivateImageVault(images, epoch).save(prepared)
            images.entries.getValue(prepared.attachment.id).let { it[it.lastIndex] = 0 }
            unavailable { routed.load(prepared.attachment) }
            assertFalse(routed.remove(prepared.attachment)); assertTrue(legacy.exists(prepared.attachment.id))
            assertTrue(images.exists(prepared.attachment.id))
        }
    }
    @Test fun epochMetadataAndCrossDirectoryIdAreChecked() {
        val legacy = Memory(); val images = Memory(); val keys = Keys()
        image().use { prepared ->
            val routed = AiAttachmentVault(legacy, keys, epoch, images = images)
            routed.save(prepared)
            unavailable { AiAttachmentVault(legacy, keys, UUID.randomUUID().toString(), images = images).load(prepared.attachment) }
            val renamed = AiAttachment(prepared.attachment.id, "other.jpg", "image/jpeg", jpeg.size, AiAttachmentKind.IMAGE)
            unavailable { routed.load(renamed) }; assertFalse(routed.remove(renamed))
            unavailable { routed.save(prepared) }
            assertEquals(0, keys.calls)
        }
    }
    private class Ops(private val fault: String? = null) : AiPrivateImagePublishOps {
        val synced = mutableListOf<File>()
        override fun writeAndSync(file: File, bytes: ByteArray) {
            FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
            if (fault == "write") throw IOException("private path must not escape")
        }
        override fun renameOwnedPayload(pending: File, payload: File) {
            check(!payload.exists()); Files.move(pending.toPath(), payload.toPath())
            if (fault == "rename") throw IOException("unknown outcome")
        }
        override fun syncDirectory(directory: File) { synced += directory; if (fault == "sync") throw IOException("unknown outcome") }
    }
    private inline fun sandbox(action: (File) -> Unit) {
        val dir = Files.createTempDirectory("openhoyi-private-images-").toFile()
        try { action(dir) } finally { dir.deleteRecursively() }
    }
    @Test fun ownedDirectoryPublicationRoundTripsAndSyncsBothDirectories() = sandbox { root ->
        val ops = Ops(); val storage = AndroidAiPrivateImageStorage(root, ops)
        image().use { prepared ->
            val vault = AiPrivateImageVault(storage, epoch); vault.save(prepared)
            assertEquals(listOf(File(root, prepared.attachment.id), root), ops.synced)
            assertTrue(storage.exists(prepared.attachment.id)); vault.load(prepared.attachment).use { assertArrayEquals(jpeg, it.bytes) }
            unavailable { vault.save(prepared) }
            assertTrue(vault.remove(prepared.attachment)); assertFalse(storage.exists(prepared.attachment.id))
        }
    }
    @Test fun failuresPreserveReservationsAndNeverReuseOrFallback() {
        for (fault in listOf("write", "rename", "sync")) sandbox { root ->
            val storage = AndroidAiPrivateImageStorage(root, Ops(fault))
            image().use { prepared ->
                unavailable { AiPrivateImageVault(storage, epoch).save(prepared) }
                assertTrue(storage.exists(prepared.attachment.id))
                val names = File(root, prepared.attachment.id).listFiles()!!.map { it.name }.toSet()
                unavailable { AiPrivateImageVault(AndroidAiPrivateImageStorage(root, Ops()), epoch).save(prepared) }
                assertEquals(names, File(root, prepared.attachment.id).listFiles()!!.map { it.name }.toSet())
                if (fault == "write") unavailable { AiPrivateImageVault(storage, epoch).load(prepared.attachment) }
            }
        }
    }
    @Test fun unknownChildrenSymlinksAndEmptyReservationsAreNotDeletedOrReused() = sandbox { root ->
        val storage = AndroidAiPrivateImageStorage(root, Ops())
        image().use { prepared ->
            val vault = AiPrivateImageVault(storage, epoch); vault.save(prepared)
            File(root, "${prepared.attachment.id}/unknown").writeBytes(byteArrayOf(1))
            assertFalse(vault.remove(prepared.attachment)); assertTrue(storage.exists(prepared.attachment.id))
            val reserved = UUID.randomUUID().toString(); assertTrue(File(root, reserved).mkdir())
            assertTrue(storage.exists(reserved)); assertTrue(runCatching { storage.writeNew(reserved, byteArrayOf(1)) }.isFailure)
            val link = File(root, UUID.randomUUID().toString())
            Files.createSymbolicLink(link.toPath(), root.toPath())
            assertTrue(storage.exists(link.name)); assertTrue(runCatching { storage.writeNew(UUID.randomUUID().toString(), byteArrayOf(1)) }.isFailure)
        }
    }
    private fun quota(action: () -> Unit) {
        val failure = runCatching(action).exceptionOrNull()
        assertTrue(failure is AiAttachmentFailure)
        assertEquals(AiAttachmentProblem.STORAGE_QUOTA, (failure as AiAttachmentFailure).problem)
    }
    @Test fun completeSmallPayloadsUseActualBytesUpToCountLimit() = sandbox { root ->
        val storage = AndroidAiPrivateImageStorage(root, Ops())
        val first = UUID.randomUUID().toString()
        storage.writeNew(first, byteArrayOf(7))
        repeat(127) { storage.writeNew(UUID.randomUUID().toString(), byteArrayOf(1)) }
        val rejected = UUID.randomUUID().toString()
        quota { storage.writeNew(rejected, byteArrayOf(1)) }
        assertFalse(storage.exists(rejected))
        assertArrayEquals(byteArrayOf(7), storage.read(first))
    }
    @Test fun actualPayloadBytesStillEnforce64MiBLimit() = sandbox { root ->
        repeat(64) {
            val folder = File(root, UUID.randomUUID().toString()); assertTrue(folder.mkdir())
            java.io.RandomAccessFile(File(folder, "payload"), "rw").use { it.setLength(1024L * 1024) }
        }
        val storage = AndroidAiPrivateImageStorage(root, Ops())
        val id = UUID.randomUUID().toString()
        quota { storage.writeNew(id, byteArrayOf(1)) }
        assertFalse(storage.exists(id))
        assertEquals(64, root.listFiles()!!.count { it.isDirectory })
    }
    @Test fun unfinishedAndUnknownReservationsKeepConservativeBudget() {
        for (name in listOf("empty", "payload.pending", "unknown", "payload")) sandbox { root ->
            repeat(64) {
                val folder = File(root, UUID.randomUUID().toString()); assertTrue(folder.mkdir())
                if (name != "empty") File(folder, name).writeBytes(byteArrayOf())
            }
            val storage = AndroidAiPrivateImageStorage(root, Ops())
            quota { storage.writeNew(UUID.randomUUID().toString(), byteArrayOf(1)) }
            assertEquals(64, root.listFiles()!!.count { it.isDirectory })
        }
    }
    @Test fun vaultPreservesQuotaClassificationWithoutDeletingHistory() = sandbox { root ->
        repeat(128) { assertTrue(File(root, UUID.randomUUID().toString()).mkdir()) }
        val storage = AndroidAiPrivateImageStorage(root, Ops())
        val routed = AiAttachmentVault(Memory(), Keys(), epoch, images = storage)
        image().use { prepared -> quota { routed.save(prepared) }; assertFalse(storage.exists(prepared.attachment.id)) }
        assertEquals(128, root.listFiles()!!.count { it.isDirectory })
    }

}
