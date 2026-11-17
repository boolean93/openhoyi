package io.openhoyi.mobile

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID

/** Ciphertext publication inside a newly reserved, exclusively owned id directory. */
internal interface AiAttachmentPublishOps {
    fun writeAndSync(temporary: File, ciphertext: ByteArray)
    /** Rename only inside this operation's fresh id directory; an exception may mean unknown outcome. */
    fun publishOwnedPayload(temporary: File, target: File)
    fun syncDirectory(directory: File)
}

/** Uses the same owned-directory primitive as private images, never Android-restricted hard links. */
private object AndroidAiAttachmentPublishOps : AiAttachmentPublishOps {
    override fun writeAndSync(temporary: File, ciphertext: ByteArray) {
        FileOutputStream(temporary).use { it.write(ciphertext); it.fd.sync() }
    }
    override fun publishOwnedPayload(temporary: File, target: File) = AndroidAiPrivateImagePublishOps.renameOwnedPayload(temporary, target)
    override fun syncDirectory(directory: File) {
        // O_CLOEXEC is public from API27. API26 still closes this short-lived directory fd
        // in finally; do not use guessed flags or a newer fcntl API on that platform.
        val closeOnExec = if (Build.VERSION.SDK_INT >= 27) OsConstants.O_CLOEXEC else 0
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or closeOnExec, 0)
        try { check(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)); Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
}

/**
 * Atomic mkdir(id) reservation -> synced ciphertext -> owned payload rename -> id/root directory sync.
 * Legacy UUID regular ciphertext files remain readable; incomplete directories are never reused.
 * No startup GC: old pending files, unknown data and unreferenced published ciphertext stay intact.
 */
internal class AndroidAiAttachmentStorage(
    private val directory: File,
    private val publication: AiAttachmentPublishOps,
    private val trace: AiAttachmentTrace = AiAttachmentTrace(),
) : AiAttachmentAddressableStorage {
    constructor(context: Context, trace: AiAttachmentTrace = AiAttachmentTrace()) : this(File(context.applicationContext.noBackupFilesDir, "ai_attachments_v1"), AndroidAiAttachmentPublishOps, trace)
    constructor(directory: File) : this(directory, AndroidAiAttachmentPublishOps)
    constructor(directory: File, trace: AiAttachmentTrace) : this(directory, AndroidAiAttachmentPublishOps, trace)

    override fun <T> transaction(action: () -> T): T = synchronized(processLock) {
        trace.event(AiAttachmentStage.STORE_LOCK)
        check(directory.isDirectory || directory.mkdirs())
        val identity = directory.canonicalPath
        val held = heldDirectories.get()
        if (identity in held) action()
        else {
            val control = File(directory, CONTROL)
            check(!Files.exists(control.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                Files.isRegularFile(control.toPath(), LinkOption.NOFOLLOW_LINKS))
            FileOutputStream(control, true).channel.use { channel ->
            channel.lock().use {
                held.add(identity)
                try { action() } finally { held.remove(identity) }
            }
        }
        }
    }
    private fun file(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")) && UUID.fromString(id).toString() == id)
        return File(directory, id)
    }
    private fun entries(): List<File> = (directory.listFiles() ?: error("Unavailable")).filter {
        it.name != CONTROL || it.length() != 0L || !Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS)
    }
    override fun exists(id: String): Boolean = transaction { Files.exists(file(id).toPath(), LinkOption.NOFOLLOW_LINKS) }
    override fun hasAny(): Boolean = transaction { entries().isNotEmpty() }

    private fun quotaBytes(entries: List<File>): Long = entries.sumOf { entry ->
        when {
            Files.isRegularFile(entry.toPath(), LinkOption.NOFOLLOW_LINKS) -> entry.length()
            Files.isDirectory(entry.toPath(), LinkOption.NOFOLLOW_LINKS) -> {
                val children = entry.listFiles() ?: error("Unavailable")
                maxOf(1L, children.sumOf { child ->
                    check(Files.isRegularFile(child.toPath(), LinkOption.NOFOLLOW_LINKS)); child.length()
                })
            }
            else -> error("Unavailable") // Unknown types/links are retained, never traversed.
        }
    }
    override fun writeNew(id: String, ciphertext: ByteArray): Unit = transaction {
        require(ciphertext.size in 1..AiAttachmentVault.MAX_CIPHER_BYTES)
        val target = file(id)
        check(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS))
        trace.event(AiAttachmentStage.STORE_QUOTA)
        val siblings = entries()
        val usedBytes = quotaBytes(siblings)
        if (siblings.size >= 128 || usedBytes + ciphertext.size > 64L * 1024 * 1024) {
            throw AiAttachmentFailure(AiAttachmentProblem.STORAGE_QUOTA)
        }
        trace.event(AiAttachmentStage.STORE_TEMP)
        check(target.mkdir()) // A preexisting file, reservation, link or unknown entry is never reused.
        val temporary = File(target, "payload.pending")
        val payload = File(target, "payload")
        try {
            check(temporary.createNewFile())
            trace.event(AiAttachmentStage.STORE_WRITE)
            publication.writeAndSync(temporary, ciphertext)
            trace.event(AiAttachmentStage.STORE_PUBLISH)
            publication.publishOwnedPayload(temporary, payload)
            trace.event(AiAttachmentStage.STORE_SYNC)
            publication.syncDirectory(target)
            publication.syncDirectory(directory)
        } catch (failure: Exception) {
            trace.failed(AiAttachmentProblem.UNAVAILABLE, failure)
            // All reservations, old pending files and unknown outcomes remain intact. No retry/GC.
            throw failure
        }
    }
    override fun read(id: String): ByteArray = transaction {
        val target = file(id)
        val payload = when {
            Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS) -> target // Legacy ciphertext.
            Files.isDirectory(target.toPath(), LinkOption.NOFOLLOW_LINKS) -> File(target, "payload").also {
                check(Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS))
            }
            else -> error("Unavailable")
        }
        payload.inputStream().use { input ->
            val size = payload.length(); check(size in 30..AiAttachmentVault.MAX_CIPHER_BYTES.toLong())
            val bytes = ByteArray(size.toInt())
            DataInputStream(input).readFully(bytes); check(input.read() == -1); bytes
        }
    }
    override fun remove(id: String): Unit = transaction {
        val target = file(id)
        if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS)) check(target.delete())
            else {
                check(Files.isDirectory(target.toPath(), LinkOption.NOFOLLOW_LINKS))
                val children = target.listFiles() ?: error("Unavailable")
                check(children.size == 1 && children.single().name == "payload" &&
                    Files.isRegularFile(children.single().toPath(), LinkOption.NOFOLLOW_LINKS))
                check(children.single().delete()); check(target.delete())
            }
            publication.syncDirectory(directory)
        }
    }
    private companion object {
        const val CONTROL = ".publication.lock"
        private val processLock = Any()
        private val heldDirectories = ThreadLocal.withInitial { mutableSetOf<String>() }
    }
}
