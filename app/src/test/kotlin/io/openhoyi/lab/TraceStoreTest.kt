package io.openhoyi.lab

import io.openhoyi.trace.TraceStore

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class TraceStoreTest {
    private fun directory() = Files.createTempDirectory("trace-test").toFile().apply { deleteOnExit() }
    private fun snapshot(store: TraceStore): Map<String, String> {
        val bytes = ByteArrayOutputStream()
        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        store.export({ bytes }) { failure = it; latch.countDown() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        failure?.let { throw AssertionError(it) }
        val entries = linkedMapOf<String, String>()
        ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        return entries
    }

    @Test fun exportIncludesOrderedRecordsAndEscapedJson() {
        TraceStore(directory()).use { store ->
            store.record("first", mapOf("value" to "\"\\\n\t\r\b\u000c\u0000"))
            store.record("second")
            val archive = snapshot(store)
            val lines = archive.filterKeys { it.endsWith(".jsonl") }.values.joinToString("").lines().filter { it.isNotBlank() }
            assertEquals(2, lines.size)
            assertTrue(lines[0].contains("\"seq\":1"))
            assertTrue(lines[1].contains("\"seq\":2"))
            assertTrue(lines[0].contains("\\\"\\\\\\n\\t\\r\\b\\f\\u0000"))
            assertTrue(lines[0].contains("\"wallclockMs\":"))
            assertTrue(lines[0].contains("\"monotonicNs\":"))
            assertTrue(archive.getValue("metadata.json").contains("\"dropped\":0"))
        }
    }
    @Test fun closeDrainsAndRejectsFurtherWork() {
        val dir = directory()
        val store = TraceStore(dir)
        repeat(20) { store.record("event") }
        store.close()
        assertTrue(store.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(20, dir.listFiles()!!.filter { it.extension == "jsonl" }.sumOf { it.readLines().size })
        store.record("late")
        assertTrue(store.status.contains("dropped=1"))
        try { store.export({ ByteArrayOutputStream() }) {}; fail("Must reject export") } catch (_: RejectedExecutionException) {}
    }
    @Test fun ioFailureIsVisibleAndExportFailureCallsBack() {
        val file = File(directory(), "not-directory").apply { writeText("x") }
        TraceStore(file).use { store ->
            store.record("event")
            val latch = CountDownLatch(1)
            var failure: Throwable? = null
            store.export({ throw IOException("denied") }) { failure = it; latch.countDown() }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertTrue(failure is IOException)
            assertTrue(store.status.contains("errors="))
            assertFalse(store.status.contains("errors=0"))
        }
    }
    @Test fun exportClosesOutputAndCompletesOnWorkerEvenWhenWritingFails() {
        TraceStore(directory()).use { store ->
            val caller = Thread.currentThread()
            val latch = CountDownLatch(1)
            var closed = false
            var callbackThread: Thread? = null
            var failure: Throwable? = null
            val output = object : java.io.OutputStream() {
                override fun write(value: Int) { throw IOException("write failed") }
                override fun close() { closed = true }
            }
            store.export({ output }) { failure = it; callbackThread = Thread.currentThread(); latch.countDown() }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertTrue(failure is IOException)
            assertTrue(closed)
            assertNotSame(caller, callbackThread)
        }
    }
    @Test fun rotationRetainsOnlyBoundedFiles() {
        TraceStore(directory(), maxFileBytes = 300, maxFiles = 2).use { store ->
            repeat(8) { store.record("event", mapOf("value" to "x".repeat(80))) }
            val files = snapshot(store).filterKeys { it.endsWith(".jsonl") }
            assertEquals(2, files.size)
            assertTrue(store.status.contains("retentionEvictedFiles=6"))
            assertTrue(snapshot(store).getValue("metadata.json").contains("\"retentionEvictedFiles\":6"))
            assertTrue(files.values.all { it.toByteArray().size <= 300 })
        }
    }
    @Test fun reopenedDirectoryKeepsDistinctSessionIds() {
        val dir = directory()
        val first = TraceStore(dir)
        first.record("first-session")
        first.close()
        assertTrue(first.awaitTermination(5, TimeUnit.SECONDS))
        TraceStore(dir).use { second ->
            second.record("second-session")
            val archive = snapshot(second)
            val lines = archive.filterKeys { it.endsWith(".jsonl") }.values.flatMap { it.lines() }.filter { it.isNotBlank() }
            val ids = lines.map { Regex(""""sessionId":"([^"]+)"""").find(it)!!.groupValues[1] }
            assertEquals(2, ids.distinct().size)
            assertTrue(archive.getValue("metadata.json").contains(ids.last()))
        }
    }
    @Test fun fullQueueDropsRecordsButExplicitlyRejectsExport() {
        TraceStore(directory(), queueCapacity = 1).use { store ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            store.export({ entered.countDown(); release.await(5, TimeUnit.SECONDS); ByteArrayOutputStream() }) {}
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            try {
                store.record("queued")
                store.record("dropped")
                assertTrue(store.status.contains("dropped=1"))
                try { store.export({ ByteArrayOutputStream() }) {}; fail("Must reject export") } catch (_: RejectedExecutionException) {}
            } finally { release.countDown() }
        }
    }
}
