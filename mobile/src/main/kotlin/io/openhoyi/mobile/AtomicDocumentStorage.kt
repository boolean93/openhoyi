package io.openhoyi.mobile

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** App-private documents: sync first, atomic replace second. Never fall back to truncate/copy. */
internal class AtomicDocumentStorage(
    private val file: Path,
    private val sync: (FileOutputStream) -> Unit = { it.fd.sync() },
    private val replace: (Path, Path) -> Unit = { from, to ->
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    },
) {
    @Synchronized
    fun read(): ByteArray? = if (Files.notExists(file)) null else Files.readAllBytes(file)

    @Synchronized
    fun write(bytes: ByteArray) {
        val parent = requireNotNull(file.toAbsolutePath().parent)
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".hoyi-document-", ".pending")
        try {
            FileOutputStream(temporary.toFile()).use { stream -> stream.write(bytes); sync(stream) }
            replace(temporary, file)
        } finally {
            // Cleanup cannot turn a successfully committed replacement into a reported failure.
            runCatching { Files.deleteIfExists(temporary) }
        }
    }
}
