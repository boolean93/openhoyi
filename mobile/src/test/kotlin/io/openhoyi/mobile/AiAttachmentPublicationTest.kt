package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Synthetic temp-directory bytes and JVM owned-directory moves. NOT Android Os/filesystem durability evidence. */
class AiAttachmentPublicationTest {
    private enum class Fault { NONE, WRITE, BEFORE_PUBLISH, AFTER_PUBLISH, DIRECTORY_SYNC }
    private open class Ops(private val fault: Fault = Fault.NONE) : AiAttachmentPublishOps {
        override fun writeAndSync(temporary: File, ciphertext: ByteArray) {
            FileOutputStream(temporary).use {
                if (fault == Fault.WRITE) { it.write(ciphertext, 0, ciphertext.size / 2); throw IOException("synthetic disk full") }
                it.write(ciphertext); it.fd.sync()
            }
        }
        override fun publishOwnedPayload(temporary: File, target: File) {
            if (fault == Fault.BEFORE_PUBLISH) throw IOException("unknown publication outcome")
            check(!Files.exists(target.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
            Files.move(temporary.toPath(), target.toPath())
            if (fault == Fault.AFTER_PUBLISH) throw IOException("unknown publication outcome")
        }
        override fun syncDirectory(directory: File) {
            if (fault == Fault.DIRECTORY_SYNC) throw IOException("synthetic directory sync failure")
            // Android adapter performs Os.fsync; the fake does not claim to test that primitive.
        }
    }
    private class Keys : AiKeySource {
        var key: SecretKey? = null; var creates = 0
        override fun existing() = key
        override fun create(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it; creates++ }
    }
    private fun id() = UUID.randomUUID().toString()
    private fun content(seed: Int = 1) = ByteArray(64) { (it + seed).toByte() }
    private fun names(directory: File) = directory.listFiles()!!.map { it.name }.toSet()
    private inline fun sandbox(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("openhoyi-publication-synthetic-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
    @Test fun completeSyncedContentAppearsOnlyAtPublishAndControlDoesNotBlockFirstKey() = sandbox { directory ->
        val targetId = id(); val bytes = content()
        val ops = object : Ops() {
            override fun writeAndSync(temporary: File, ciphertext: ByteArray) {
                assertTrue(File(directory, targetId).isDirectory); assertFalse(File(directory, "$targetId/payload").exists()); super.writeAndSync(temporary, ciphertext)
                assertFalse(File(directory, "$targetId/payload").exists()); assertArrayEquals(bytes, temporary.readBytes())
            }
        }
        val storage = AndroidAiAttachmentStorage(directory, ops)
        assertFalse(storage.hasAny()); assertEquals(setOf(".publication.lock"), names(directory))
        storage.writeNew(targetId, bytes)
        assertArrayEquals(bytes, storage.read(targetId)); assertEquals(setOf(".publication.lock", targetId), names(directory))
        assertTrue(storage.hasAny())
        assertTrue(runCatching { storage.writeNew(targetId, content(2)) }.isFailure)
        assertArrayEquals(bytes, storage.read(targetId))
    }
    @Test fun failedPublicationPreservesNewReservationsAndAllUnknownData() {
        for (fault in listOf(Fault.WRITE, Fault.BEFORE_PUBLISH, Fault.AFTER_PUBLISH, Fault.DIRECTORY_SYNC)) sandbox { directory ->
            val oldId = id(); val oldBytes = content(7); File(directory, oldId).writeBytes(oldBytes)
            val unknown = File(directory, ".pending-old-unknown.cipher").apply { writeBytes(byteArrayOf(3, 4)) }
            val newId = id(); val bytes = content()
            assertTrue(runCatching { AndroidAiAttachmentStorage(directory, Ops(fault)).writeNew(newId, bytes) }.isFailure)
            assertArrayEquals(oldBytes, File(directory, oldId).readBytes()); assertArrayEquals(byteArrayOf(3, 4), unknown.readBytes())
            val reservation = File(directory, newId)
            assertTrue(reservation.isDirectory)
            val published = fault in listOf(Fault.AFTER_PUBLISH, Fault.DIRECTORY_SYNC)
            assertEquals(published, File(reservation, "payload").exists())
            val ownNames = names(reservation)
            val preserved = names(directory)
            repeat(2) {
                val reopened = AndroidAiAttachmentStorage(directory, Ops())
                assertTrue(reopened.hasAny()); assertArrayEquals(oldBytes, reopened.read(oldId))
                if (published) {
                    assertArrayEquals(bytes, reopened.read(newId))
                    assertTrue(runCatching { reopened.writeNew(newId, content(9)) }.isFailure)
                }
                assertEquals(preserved, names(directory)) // No restart cleanup or final rollback.
                assertEquals(ownNames, names(reservation))
                assertTrue(runCatching { reopened.writeNew(newId, content(9)) }.isFailure)
            }
        }
    }
    @Test fun raceAfterExistenceCheckCannotReplaceAnotherPublishedId() = sandbox { directory ->
        val targetId = id(); val winner = content(9)
        val ops = object : Ops() {
            override fun publishOwnedPayload(temporary: File, target: File) {
                target.writeBytes(winner) // Another writer wins after the preflight existence check.
                super.publishOwnedPayload(temporary, target)
            }
        }
        assertTrue(runCatching { AndroidAiAttachmentStorage(directory, ops).writeNew(targetId, content()) }.isFailure)
        assertArrayEquals(winner, File(directory, "$targetId/payload").readBytes())
        assertTrue(File(directory, "$targetId/payload.pending").isFile)
    }
    @Test fun concurrentInstancesPublishOneWholeWinnerWithoutReplacement() = sandbox { directory ->
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2); val targetId = id()
        val versions = listOf(content(1), content(2))
        try {
            val jobs = versions.map { bytes -> pool.submit<Boolean> {
                start.await(); runCatching { AndroidAiAttachmentStorage(directory, Ops()).writeNew(targetId, bytes) }.isSuccess
            } }
            start.countDown(); assertEquals(1, jobs.count { it.get(2, TimeUnit.SECONDS) })
            val actual = AndroidAiAttachmentStorage(directory, Ops()).read(targetId)
            assertTrue(versions.any { it.contentEquals(actual) })
            assertEquals(setOf(".publication.lock", targetId), names(directory))
        } finally { pool.shutdownNow() }
    }
    @Test fun unknownFilesConsumeQuotaWithoutEvictionAndPathsStayBounded() = sandbox { directory ->
        repeat(128) { File(directory, id()).writeBytes(byteArrayOf(1)) }
        val storage = AndroidAiAttachmentStorage(directory, Ops()); val old = names(directory)
        assertTrue(runCatching { storage.writeNew(id(), content()) }.isFailure)
        assertEquals(old, names(directory) - ".publication.lock")
        assertTrue(runCatching { storage.writeNew("../outside", content()) }.isFailure)
        assertEquals(old, names(directory) - ".publication.lock")
    }
    @Test fun controlAloneAllowsFirstKeyButUnknownPendingAndNonemptyControlPreventReplacementKey() {
        sandbox { directory ->
            val keys = Keys(); val storage = AndroidAiAttachmentStorage(directory, Ops()); assertFalse(storage.hasAny())
            val bytes = "synthetic".toByteArray()
            AiPreparedAttachment(AiAttachment(id(), "a.txt", "text/plain", bytes.size, AiAttachmentKind.TEXT), bytes).use {
                AiAttachmentVault(storage, keys, id()).save(it)
            }
            assertEquals(1, keys.creates)
        }
        for (name in listOf(".pending-unknown.cipher", ".publication.lock")) sandbox { directory ->
            File(directory, name).writeBytes(byteArrayOf(1))
            val keys = Keys(); val bytes = "synthetic".toByteArray(); val storage = AndroidAiAttachmentStorage(directory, Ops())
            AiPreparedAttachment(AiAttachment(id(), "a.txt", "text/plain", bytes.size, AiAttachmentKind.TEXT), bytes).use {
                assertTrue(runCatching { AiAttachmentVault(storage, keys, id()).save(it) }.isFailure)
            }
            assertEquals(0, keys.creates); assertArrayEquals(byteArrayOf(1), File(directory, name).readBytes())
        }
    }
    @Test fun publishedCiphertextSurvivesFailedManifestSaveAndRepeatedRestore() = sandbox { directory ->
        val epoch = id(); val keys = Keys(); val storage = AndroidAiAttachmentStorage(directory, Ops())
        val vault = AiAttachmentVault(storage, keys, epoch); val bytes = "synthetic draft".toByteArray()
        val item = AiAttachment(id(), "draft.txt", "text/plain", bytes.size, AiAttachmentKind.TEXT)
        AiPreparedAttachment(item, bytes.copyOf()).use(vault::save)
        val manifestStore = AiSessionStore(object : AiSessionStorage {
            override fun read(): ByteArray? = null
            override fun write(bytes: ByteArray) { throw IOException("synthetic manifest write failure") }
        }, Keys())
        assertSame(AiSessionLoad.Empty, manifestStore.load(epoch))
        assertFalse(manifestStore.save(AiSession(epoch, emptyList(), AiProtocolHistory.empty(), "unsent", AiUsageSnapshot(), listOf(item))))
        repeat(2) {
            AiAttachmentVault(AndroidAiAttachmentStorage(directory, Ops()), keys, epoch).load(item).use { assertArrayEquals(bytes, it.bytes) }
            assertTrue(File(directory, item.id).exists())
        }
    }
    @Test fun legacyCiphertextFileAndNewDirectoryShareUnchangedAesFormat() = sandbox { directory ->
        val epoch = id(); val keys = Keys(); val bytes = "legacy synthetic text".toByteArray()
        val item = AiAttachment(id(), "old.txt", "text/plain", bytes.size, AiAttachmentKind.TEXT)
        var sealed: ByteArray? = null
        val memory = object : AiAttachmentStorage {
            override fun hasAny() = sealed != null
            override fun writeNew(id: String, ciphertext: ByteArray) { sealed = ciphertext.copyOf() }
            override fun read(id: String) = sealed!!.copyOf()
            override fun remove(id: String) { sealed = null }
        }
        AiPreparedAttachment(item, bytes.copyOf()).use { AiAttachmentVault(memory, keys, epoch).save(it) }
        File(directory, item.id).writeBytes(sealed!!)
        val storage = AndroidAiAttachmentStorage(directory, Ops()); val vault = AiAttachmentVault(storage, keys, epoch)
        vault.load(item).use { assertArrayEquals(bytes, it.bytes) }
        assertTrue(vault.remove(item)); assertFalse(File(directory, item.id).exists())
        val pdf = "%PDF-1.7\nsynthetic".toByteArray()
        val doc = AiAttachment(id(), "sample.pdf", "application/pdf", pdf.size, AiAttachmentKind.DOCUMENT)
        AiPreparedAttachment(doc, pdf.copyOf()).use(vault::save)
        assertTrue(File(directory, doc.id).isDirectory)
        assertEquals(1.toByte(), File(directory, "${doc.id}/payload").readBytes().first())
        vault.load(doc).use { assertArrayEquals(pdf, it.bytes) }
        assertTrue(vault.remove(doc)); assertFalse(File(directory, doc.id).exists())
        assertEquals(1, keys.creates)
    }
    @Test fun emptyReservationBlocksNewKeyAndUnknownChildrenPreventRemoval() = sandbox { directory ->
        val reservation = id(); assertTrue(File(directory, reservation).mkdir())
        val storage = AndroidAiAttachmentStorage(directory, Ops()); val keys = Keys()
        val bytes = "synthetic".toByteArray()
        val item = AiAttachment(id(), "a.txt", "text/plain", bytes.size, AiAttachmentKind.TEXT)
        AiPreparedAttachment(item, bytes).use { assertTrue(runCatching { AiAttachmentVault(storage, keys, id()).save(it) }.isFailure) }
        assertEquals(0, keys.creates); assertTrue(storage.exists(reservation))
        assertTrue(runCatching { storage.read(reservation) }.isFailure)
        val another = id(); storage.writeNew(another, content())
        File(directory, "$another/unknown").writeBytes(byteArrayOf(1))
        assertTrue(runCatching { storage.remove(another) }.isFailure)
        assertTrue(File(directory, "$another/payload").isFile)
    }
    @Test fun directoryQuotaAndSymlinkPayloadFailClosed() = sandbox { directory ->
        val reservation = id(); val folder = File(directory, reservation).apply { mkdir() }
        val payload = File(folder, "payload.pending")
        java.io.RandomAccessFile(payload, "rw").use { it.setLength(64L * 1024 * 1024) }
        val storage = AndroidAiAttachmentStorage(directory, Ops())
        assertTrue(runCatching { storage.writeNew(id(), content()) }.isFailure)
        assertEquals(64L * 1024 * 1024, payload.length())
        payload.delete()
        Files.createSymbolicLink(File(folder, "payload").toPath(), directory.toPath())
        assertTrue(runCatching { storage.read(reservation) }.isFailure)
        assertTrue(runCatching { storage.writeNew(id(), content()) }.isFailure)
        assertTrue(runCatching { storage.remove(reservation) }.isFailure)
    }

}
