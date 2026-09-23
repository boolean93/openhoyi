package io.openhoyi.trace

import java.io.File
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Asynchronous, bounded diagnostic journal. Callers MUST redact credentials, passwords and raw
 * authentication packets before calling record; exports contain the supplied fields verbatim.
 * Accepted exports run after all previously accepted records. A full/closed queue throws
 * RejectedExecutionException synchronously; accepted exports always complete on the worker.
 */
class TraceStore(
    private val directory: File,
    queueCapacity: Int = 512,
    private val maxFileBytes: Long = 4L * 1024 * 1024,
    private val maxFiles: Int = 8,
) : AutoCloseable {
    init { require(queueCapacity > 0 && maxFileBytes > 0 && maxFiles > 0) }
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
        val wallclock = System.currentTimeMillis()
        val monotonic = System.nanoTime()
        try {
            worker.execute {
                try {
                    sequence++
                    val json = "{\"sessionId\":${quote(sessionId)},\"seq\":$sequence,\"wallclockMs\":$wallclock,\"monotonicNs\":$monotonic," +
                        "\"kind\":${quote(kind)},\"fields\":{" +
                        copied.entries.joinToString(",") { "${quote(it.key)}:${quote(it.value)}" } + "}}\n"
                    val bytes = json.toByteArray(Charsets.UTF_8)
                    if (bytes.size > maxFileBytes) { dropped.incrementAndGet(); return@execute }
                    prepare()
                    var target = active
                    if (target == null || target.length() + bytes.size > maxFileBytes) {
                        target = File(directory, "trace-${(++fileIndex).toString().padStart(20, '0')}.jsonl")
                        check(target.createNewFile()) { "Cannot create journal" }
                        active = target
                        trim()
                    }
                    target.appendBytes(bytes)
                } catch (failure: Throwable) { dropped.incrementAndGet(); failed(failure) }
            }
        } catch (_: RejectedExecutionException) { dropped.incrementAndGet() }
    }

    fun export(open: () -> OutputStream, done: (Throwable?) -> Unit) {
        worker.execute {
            var failure: Throwable? = null
            try {
                prepare()
                // No writer can interleave with this snapshot because both use the same queue.
                open().use { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("metadata.json"))
                    zip.write(("{\"schema\":1,\"sessionId\":${quote(sessionId)},\"retentionEvictedFiles\":${retentionEvictedFiles.get()},\"dropped\":${dropped.get()},\"errors\":${errors.get()}," +
                        "\"lastError\":${quote(lastError)},\"lastSequence\":$sequence}").toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    files().forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                }
            } catch (error: Throwable) { failed(error); failure = error }
            try { done(failure) } catch (error: Throwable) { failed(error) }
        }
    }

    private fun prepare() {
        if (initialized) return
        if (!directory.isDirectory && !directory.mkdirs()) throw java.io.IOException("Cannot create journal directory")
        fileIndex = files().mapNotNull { it.name.removePrefix("trace-").removeSuffix(".jsonl").toLongOrNull() }.maxOrNull() ?: 0
        trim()
        initialized = true
    }
    private fun files() = directory.listFiles()?.filter { it.isFile && it.name.matches(Regex("trace-[0-9]{20}\\.jsonl")) }?.sortedBy { it.name }
        ?: throw java.io.IOException("Cannot list journal directory")
    private fun trim() {
        val all = files()
        all.take((all.size - maxFiles).coerceAtLeast(0)).forEach {
            check(it.delete()) { "Cannot rotate journal" }
            retentionEvictedFiles.incrementAndGet()
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
