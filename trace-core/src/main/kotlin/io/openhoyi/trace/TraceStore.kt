package io.openhoyi.trace

import java.io.File
import java.io.OutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Build identity only: no device IDs, user data, paths or credentials. */
data class TraceAppInfo(
    val versionName: String,
    val versionCode: Long,
    val buildType: String,
) {
    init {
        require(versionName.length <= 256 && buildType.length <= 64 && versionCode >= 0)
    }
}

/** Filesystem seam for deterministic failure recovery tests; no caller/UI thread I/O. */
open class TraceFileIo {
    open fun open(file: File): InputStream = file.inputStream()
    open fun commit(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
    open fun syncDirectory(directory: File) {
        FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
    }
}

/**
 * Asynchronous, bounded diagnostic journal. Callers MUST redact credentials, passwords and raw
 * authentication packets before calling record; exports contain the supplied fields verbatim.
 * Accepted exports run after all previously accepted records. A full/closed queue throws
 * RejectedExecutionException synchronously; accepted exports always complete on the worker.
 */
class TraceStore(
    private val directory: File,
    queueCapacity: Int = 512,
    private val maxFileBytes: Long = 256L * 1024,
    private val maxFiles: Int = Int.MAX_VALUE,
    private val appInfo: TraceAppInfo? = null,
    private val maxTotalBytes: Long = 8L * 1024 * 1024,
    private val maxAgeMs: Long = TimeUnit.DAYS.toMillis(7),
    private val now: () -> Long = System::currentTimeMillis,
    private val io: TraceFileIo = TraceFileIo(),
) : AutoCloseable {
    init { require(queueCapacity > 0 && maxFileBytes > 0 && maxFiles > 0 && maxTotalBytes > 0 && maxAgeMs > 0) }
    private val sessionId = java.util.UUID.randomUUID().toString()
    private val retentionEvictedFiles = AtomicLong()
    private val dropped = AtomicLong()
    private val errors = AtomicLong()
    @Volatile private var lastError = ""
    private var sequence = 0L
    private var active: File? = null
    private var fileIndex = 0L
    private var initialized = false
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(queueCapacity),
        { task -> Thread(task, "trace-store").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())

    val status: String get() = "${if (worker.isShutdown) "closed" else "active"} dropped=${dropped.get()} errors=${errors.get()} retentionEvictedFiles=${retentionEvictedFiles.get()}" +
        if (lastError.isEmpty()) "" else " lastError=$lastError"

    /** Never performs disk I/O or waits for queue capacity on the calling thread. */
    fun record(kind: String, fields: Map<String, String> = emptyMap()) {
        // Bound the retained payload as well as the number of queued records.
        if (kind.length > 8192 || fields.size > 64 ||
            fields.entries.sumOf { it.key.length.toLong() + it.value.length } > 8192) {
            dropped.incrementAndGet(); return
        }
        val copied = fields.toMap()
        val wallclock = now()
        val monotonic = System.nanoTime()
        try {
            worker.execute {
                try {
                    sequence++
                    val json = "{\"sessionId\":${quote(sessionId)},\"seq\":$sequence,\"wallclockMs\":$wallclock,\"monotonicNs\":$monotonic," +
                        "\"app\":${appJson()},\"kind\":${quote(kind)},\"fields\":{" +
                        copied.entries.joinToString(",") { "${quote(it.key)}:${quote(it.value)}" } + "}}\n"
                    val bytes = json.toByteArray(Charsets.UTF_8)
                    if (bytes.size > maxFileBytes) { dropped.incrementAndGet(); return@execute }
                    prepare()
                    trim()
                    var target = active
                    if (target == null || target.length() + bytes.size > maxFileBytes) {
                        active = null
                        target?.let { compress(it) }
                        target = File(directory, "trace-${(++fileIndex).toString().padStart(20, '0')}.jsonl")
                        check(target.createNewFile()) { "Cannot create journal" }
                        active = target
                        trim()
                    }
                    reserve(bytes.size.toLong(), setOf(target.name))
                    target.appendBytes(bytes)
                    // Stable segment birth time: appending/compression must not extend retention.
                    if (target.length() == bytes.size.toLong()) check(target.setLastModified(wallclock))
                    else segmentBirth[target.name]?.let { check(target.setLastModified(it)) }
                    segmentBirth.putIfAbsent(target.name, target.lastModified())
                } catch (failure: Throwable) { dropped.incrementAndGet(); failed(failure) }
            }
        } catch (_: RejectedExecutionException) { dropped.incrementAndGet() }
    }

    fun export(open: () -> OutputStream, done: (Throwable?) -> Unit) {
        worker.execute {
            var failure: Throwable? = null
            try {
                prepare()
                trim()
                // No writer can interleave with this snapshot because both use the same queue.
                open().use { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("metadata.json"))
                    zip.write(("{\"schema\":1,\"sessionId\":${quote(sessionId)},\"retentionEvictedFiles\":${retentionEvictedFiles.get()},\"dropped\":${dropped.get()},\"errors\":${errors.get()}," +
                        "\"lastError\":${quote(lastError)},\"lastSequence\":$sequence,\"exporterApp\":${appJson()}}").toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    segments().forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name.removeSuffix(".gz")))
                        input(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                }
            } catch (error: Throwable) { failed(error); failure = error }
            try { done(failure) } catch (error: Throwable) { failed(error) }
        }
    }

    // Repeated per record so rotation cannot discard the only version marker. Old records
    // are copied verbatim on export; exporterApp never describes historical sessions.
    private fun appJson(): String = appInfo?.let {
        "{\"versionName\":${quote(it.versionName)},\"versionCode\":${it.versionCode},\"buildType\":${quote(it.buildType)}}"
    } ?: "null"

    data class StorageInfo(val bytes: Long, val files: Int)
    private val segmentBirth = mutableMapOf<String, Long>()
    private val namePattern = Regex("trace-[0-9]{20}\\.jsonl(?:\\.gz(?:\\.tmp)?)?")
    private fun owned(file: File) = file.name.matches(namePattern) &&
        Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
    private fun allFiles(): List<File> = directory.listFiles()?.filter {
        Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS)
    } ?: throw IOException("Cannot list journal directory")
    private fun ownedFiles() = allFiles().filter(::owned)
    private fun bytesUsed() = allFiles().sumOf { it.length() }
    private fun input(file: File) = if (file.name.endsWith(".gz")) GZIPInputStream(io.open(file)) else io.open(file)
    private fun segments() = ownedFiles().filterNot { it.name.endsWith(".tmp") }
        .groupBy { it.name.removeSuffix(".gz") }.toSortedMap().map { (_, group) ->
            // Raw is authoritative until the compression transaction has deleted it.
            group.firstOrNull { it.name.endsWith(".jsonl") } ?: group.single()
        }
    private fun remove(file: File, retention: Boolean = true) {
        check(file.delete() || !file.exists()) { "Cannot remove journal" }
        if (active?.name == file.name) active = null
        segmentBirth.remove(file.name)
        if (retention) retentionEvictedFiles.incrementAndGet()
    }
    private fun removeSegment(file: File): Long {
        val rawName = file.name.removeSuffix(".gz")
        return ownedFiles().filter { it.name == rawName || it.name == "$rawName.gz" }.sumOf {
            val bytes = it.length(); remove(it); bytes
        }
    }
    private fun reserve(extra: Long, protected: Set<String> = emptySet()) {
        if (extra > maxTotalBytes) throw IOException("Journal capacity exceeded")
        var used = bytesUsed()
        if (used <= maxTotalBytes - extra) return
        for (file in segments()) {
            if (file.name in protected || file.name.removeSuffix(".gz") in protected || file == active) continue
            used -= removeSegment(file)
            if (used <= maxTotalBytes - extra) return
        }
        throw IOException("Journal capacity unavailable")
    }
    private fun trim() {
        val time = now()
        segments().filter { time >= it.lastModified() && time - it.lastModified() >= maxAgeMs }.forEach(::removeSegment)
        val files = segments()
        files.take((files.size - maxFiles).coerceAtLeast(0)).forEach(::removeSegment)
        reserve(0)
    }
    /** Background-only, single-worker transaction; .tmp is never the only authoritative copy. */
    private fun compress(raw: File) {
        if (!raw.isFile || raw == active || raw.length() == 0L) return
        val zipped = File(directory, raw.name + ".gz")
        val temp = File(directory, raw.name + ".gz.tmp")
        try {
            // Deflate worst-case bound with generous headroom; account for temporary bytes before writing.
            val bound = raw.length() + raw.length() / 1000 + 128
            // Prefer retaining raw data over deleting it solely to create compression scratch space.
            if (bound > maxTotalBytes - bytesUsed() || directory.usableSpace < bound + 1024 * 1024) return
            check(!temp.exists() && !zipped.exists()) { "Unresolved journal transaction" }
            val sink = temp.outputStream()
            val output = object : FilterOutputStream(sink) {
                private var written = 0L
                override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
                override fun write(buffer: ByteArray, offset: Int, count: Int) {
                    if (written + count > bound) throw IOException("Compression bound exceeded")
                    out.write(buffer, offset, count); written += count
                }
            }
            GZIPOutputStream(output).use { out ->
                io.open(raw).use { it.copyTo(out) }
                out.finish(); sink.fd.sync()
            }
            check(sameContent(raw, temp)) { "Journal compression verification failed" }
            check(temp.setLastModified(raw.lastModified()))
            io.commit(temp, zipped)
            io.syncDirectory(directory)
            remove(raw, retention = false)
        } catch (error: Throwable) {
            failed(error)
            // Raw survives any failure; leave published archive for deterministic recovery.
        } finally {
            if (temp.exists() && !temp.delete()) failed(IOException("Cannot remove compression scratch"))
        }
    }
    private fun sameContent(raw: File, compressed: File): Boolean {
        io.open(raw).buffered().use { a -> GZIPInputStream(io.open(compressed)).buffered().use { b ->
            val left = ByteArray(8192); val right = ByteArray(8192)
            while (true) {
                val size = a.read(left)
                if (size < 0) return b.read() < 0
                var read = 0
                while (read < size) { val n = b.read(right, read, size - read); if (n < 0) return false; read += n }
                if (!left.copyOf(size).contentEquals(right.copyOf(size))) return false
            }
        } }
    }
    private fun prepare() {
        if (initialized) return
        check(!Files.isSymbolicLink(directory.toPath())) { "Invalid journal directory" }
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create journal directory")
        fileIndex = ownedFiles().mapNotNull { it.name.removePrefix("trace-").substringBefore('.').toLongOrNull() }.maxOrNull() ?: 0
        ownedFiles().filter { it.name.endsWith(".tmp") }.forEach { remove(it, retention = false) }
        ownedFiles().filter { it.name.endsWith(".jsonl") }.forEach { raw ->
            val zipped = File(directory, raw.name + ".gz")
            if (owned(zipped)) {
                try {
                    if (sameContent(raw, zipped)) {
                        io.syncDirectory(directory)
                        remove(raw, retention = false)
                    } else remove(zipped, retention = false) // Only a completed comparison can reject a duplicate.
                } catch (error: Throwable) {
                    // Read/verification/durability failure is not proof that either copy is disposable.
                    failed(error)
                }
            }
        }
        trim()
        segments().filter { it.name.endsWith(".jsonl") }.forEach(::compress)
        initialized = true
    }
    /** Callback runs on the journal worker. Rejected submissions throw synchronously. */
    fun storageInfo(done: (StorageInfo?, Throwable?) -> Unit) = maintenance(false, done)
    fun clear(done: (StorageInfo?, Throwable?) -> Unit) = maintenance(true, done)
    private fun maintenance(clear: Boolean, done: (StorageInfo?, Throwable?) -> Unit) {
        worker.execute {
            var info: StorageInfo? = null
            var failure: Throwable? = null
            try {
                prepare()
                if (clear) ownedFiles().forEach { remove(it, retention = false) } else trim()
                info = StorageInfo(bytesUsed(), ownedFiles().size)
            } catch (error: Throwable) { failed(error); failure = error }
            try { done(info, failure) } catch (error: Throwable) { failed(error) }
        }
    }
    private fun failed(error: Throwable) {
        errors.incrementAndGet()
        // Avoid persisting exception messages, which can contain sensitive input or paths.
        lastError = error.javaClass.simpleName
    }
    /** Returns immediately; already accepted operations drain normally. */
    override fun close() { worker.shutdown() }
    fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = worker.awaitTermination(timeout, unit)

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                else -> if (c < ' ' || c.isSurrogate()) append("\\u${c.code.toString(16).padStart(4, '0')}") else append(c)
            }
        }
        append('"')
    }
}
