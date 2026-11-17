package io.openhoyi.mobile

import android.content.Context
import android.system.Os
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID

/** Private image payload publication. The enclosing id directory must be newly and exclusively owned. */
internal interface AiPrivateImagePublishOps {
    fun writeAndSync(file: File, bytes: ByteArray)
    fun renameOwnedPayload(pending: File, payload: File)
    fun syncDirectory(directory: File)
}
internal object AndroidAiPrivateImagePublishOps : AiPrivateImagePublishOps {
    override fun writeAndSync(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    }
    override fun renameOwnedPayload(pending: File, payload: File) {
        check(!Files.exists(payload.toPath(), LinkOption.NOFOLLOW_LINKS))
        Os.rename(pending.path, payload.path)
    }
    override fun syncDirectory(directory: File) {
        val fd = Os.open(directory.path, android.system.OsConstants.O_RDONLY or android.system.OsConstants.O_NOFOLLOW, 0)
        try { check(android.system.OsConstants.S_ISDIR(Os.fstat(fd).st_mode)); Os.fsync(fd) } finally { Os.close(fd) }
    }
}
/**
 * mkdir(id) reserves the name without replacement. Readers share the root transaction lock.
 * A complete payload is the commit marker. Crashes/unknown rename or fsync leave the reservation
 * intact, never reusable and never eligible for legacy fallback. No hard links, public paths or keys.
 */
internal class AndroidAiPrivateImageStorage(
    private val directory: File,
    private val ops: AiPrivateImagePublishOps,
    private val trace: AiAttachmentTrace = AiAttachmentTrace(),
) : AiAttachmentAddressableStorage {
    constructor(context: Context, trace: AiAttachmentTrace = AiAttachmentTrace()) : this(
        File(context.applicationContext.noBackupFilesDir, "ai_images_v2"), AndroidAiPrivateImagePublishOps, trace)
    private val locks = AndroidAiAttachmentStorage(directory, trace = trace)
    override fun <T> transaction(action: () -> T): T {
        check(!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS) ||
            Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS))
        return locks.transaction(action)
    }
    private fun entry(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")) && UUID.fromString(id).toString() == id)
        return File(directory, id)
    }
    override fun exists(id: String) = transaction { Files.exists(entry(id).toPath(), LinkOption.NOFOLLOW_LINKS) }
    override fun hasAny() = transaction { entries().isNotEmpty() }
    private fun entries() = (directory.listFiles() ?: error("Unavailable")).filter {
        if (it.name != ".publication.lock") true
        else { check(Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS) && it.length() == 0L); false }
    }
    private fun quotaBytes(): Long = entries().sumOf { folder ->
        check(Files.isDirectory(folder.toPath(), LinkOption.NOFOLLOW_LINKS))
        val children = folder.listFiles() ?: error("Unavailable")
        // Unknown files count; unknown subdirectories/symlinks fail closed, never followed or deleted.
        val bytes = children.sumOf { child ->
            check(Files.isRegularFile(child.toPath(), LinkOption.NOFOLLOW_LINKS)); child.length()
        }
        // Only the published, bounded payload is a complete entry. Charge actual disk bytes;
        // pending/empty/unknown reservations still reserve a whole maximum payload, never reclaim them.
        val complete = children.size == 1 && children.single().name == "payload" && bytes in 1..MAX_BYTES.toLong()
        if (complete) bytes else maxOf(bytes, MAX_BYTES.toLong())
    }
    override fun writeNew(id: String, ciphertext: ByteArray): Unit = transaction {
        require(ciphertext.size in 1..MAX_BYTES)
        trace.event(AiAttachmentStage.STORE_QUOTA)
        val usedBytes = quotaBytes() // Validate unknown types/links before classifying a capacity failure.
        if (entries().size >= 128 || usedBytes + ciphertext.size > 64L * 1024 * 1024) {
            throw AiAttachmentFailure(AiAttachmentProblem.STORAGE_QUOTA)
        }
        val owned = entry(id)
        trace.event(AiAttachmentStage.STORE_TEMP)
        check(owned.mkdir()) // Existing, incomplete, symlink or other unknown name: never reuse.
        val pending = File(owned, "payload.pending")
        val payload = File(owned, "payload")
        try {
            check(pending.createNewFile())
            trace.event(AiAttachmentStage.STORE_WRITE)
            ops.writeAndSync(pending, ciphertext)
            trace.event(AiAttachmentStage.STORE_PUBLISH)
            ops.renameOwnedPayload(pending, payload)
            trace.event(AiAttachmentStage.STORE_SYNC)
            ops.syncDirectory(owned)
            ops.syncDirectory(directory)
        } catch (failure: Exception) {
            trace.failed(AiAttachmentProblem.UNAVAILABLE, failure)
            // Preserve every reservation and unknown outcome. No recursive cleanup or retry.
            throw failure
        }
    }
    override fun read(id: String): ByteArray = transaction {
        val owned = entry(id); check(Files.isDirectory(owned.toPath(), LinkOption.NOFOLLOW_LINKS))
        val payload = File(owned, "payload")
        check(Files.isRegularFile(payload.toPath(), LinkOption.NOFOLLOW_LINKS))
        val size = payload.length(); check(size in 1..MAX_BYTES.toLong())
        payload.inputStream().use { input -> ByteArray(size.toInt()).also { bytes ->
            DataInputStream(input).readFully(bytes); check(input.read() == -1)
        } }
    }
    override fun remove(id: String): Unit = transaction {
        val owned = entry(id); check(Files.isDirectory(owned.toPath(), LinkOption.NOFOLLOW_LINKS))
        // Vault authenticated the exact envelope before calling. Unknown children prevent deletion.
        val children = owned.listFiles() ?: error("Unavailable")
        check(children.size == 1 && children.single().name == "payload" &&
            Files.isRegularFile(children.single().toPath(), LinkOption.NOFOLLOW_LINKS))
        check(children.single().delete()); check(owned.delete())
        ops.syncDirectory(directory)
    }
    companion object { const val MAX_BYTES = AiAttachmentPolicy.MAX_IMAGE_BYTES + 1024 }
}
/** SHA-256 detects accidental corruption; it is not encryption or authentication against app access. */
internal class AiPrivateImageVault(private val storage: AiAttachmentAddressableStorage,
    private val epoch: String, private val trace: AiAttachmentTrace = AiAttachmentTrace()) {
    fun save(prepared: AiPreparedAttachment) {
        var encoded: ByteArray? = null
        try { storage.transaction {
            val a = prepared.attachment
            check(a.kind == AiAttachmentKind.IMAGE && prepared.bytes.size == a.size &&
                AiAttachmentPolicy.imageSignature(prepared.bytes, "image/jpeg"))
            trace.event(AiAttachmentStage.STORE_ENCODE)
            val out = AiAttachmentBuffer()
            try {
                DataOutputStream(out).use { data ->
                    data.writeInt(MAGIC); data.writeUTF(epoch)
                    data.writeUTF(a.id); data.writeUTF(a.name); data.writeUTF(a.mimeType)
                    data.writeInt(a.size); data.writeUTF(a.kind.name)
                    data.write(MessageDigest.getInstance("SHA-256").digest(prepared.bytes)); data.write(prepared.bytes)
                }
                encoded = out.toByteArray()
            } finally { out.wipe() }
            storage.writeNew(a.id, encoded!!)
        } } catch (failure: AiAttachmentFailure) { trace.failed(failure.problem, failure); throw failure }
        catch (failure: Exception) { trace.failed(AiAttachmentProblem.UNAVAILABLE, failure); throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE) }
        finally { encoded?.fill(0) }
    }
    fun load(expected: AiAttachment): AiPreparedAttachment {
        var encoded: ByteArray? = null; var content: ByteArray? = null
        try {
            val result = storage.transaction {
                check(expected.kind == AiAttachmentKind.IMAGE)
                encoded = storage.read(expected.id)
                check(encoded!!.size in 1..AndroidAiPrivateImageStorage.MAX_BYTES)
                DataInputStream(ByteArrayInputStream(encoded)).use { data ->
                    check(data.readInt() == MAGIC && data.readUTF() == epoch)
                    check(data.readUTF() == expected.id && data.readUTF() == expected.name && data.readUTF() == expected.mimeType)
                    check(data.readInt() == expected.size && data.readUTF() == expected.kind.name)
                    val digest = ByteArray(32); data.readFully(digest)
                    content = ByteArray(expected.size); data.readFully(content); check(data.read() == -1)
                    check(MessageDigest.isEqual(digest, MessageDigest.getInstance("SHA-256").digest(content!!)))
                    check(AiAttachmentPolicy.imageSignature(content!!, "image/jpeg"))
                }
                AiPreparedAttachment(expected, content!!)
            }
            content = null; return result
        } catch (_: Exception) { throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE) }
        finally { encoded?.fill(0); content?.fill(0) }
    }
    fun remove(expected: AiAttachment): Boolean = runCatching { storage.transaction {
        load(expected).use { }; storage.remove(expected.id); true
    } }.getOrDefault(false)
    private companion object { const val MAGIC = 0x484f5932 }
}
