package io.openhoyi.lab

import io.openhoyi.trace.TraceStore
import io.openhoyi.trace.TraceFileIo
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream

class TraceRetentionTest {
    private fun directory() = Files.createTempDirectory("trace-retention").toFile()
    private fun raw(dir: File, index: Int = 1) = File(dir, "trace-${index.toString().padStart(20, '0')}.jsonl")
    private fun await(block: ((TraceStore.StorageInfo?, Throwable?) -> Unit) -> Unit): TraceStore.StorageInfo {
        val latch = CountDownLatch(1); var value: TraceStore.StorageInfo? = null; var failure: Throwable? = null
        block { info, error -> value = info; failure = error; latch.countDown() }
        assertTrue(latch.await(10, TimeUnit.SECONDS)); failure?.let { throw it }; return requireNotNull(value)
    }
    private fun export(store: TraceStore): Map<String, String> {
        val bytes = ByteArrayOutputStream(); val latch = CountDownLatch(1); var failure: Throwable? = null
        store.export({ bytes }) { failure = it; latch.countDown() }
        assertTrue(latch.await(10, TimeUnit.SECONDS)); failure?.let { throw it }
        return buildMap { ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes().toString(Charsets.UTF_8)) }
        } }
    }
    @Test fun oldSegmentsCompressWithoutChangingExportOrBirthTime() {
        val dir = directory(); val source = raw(dir).apply { writeText("old raw\n".repeat(1000)); setLastModified(1000) }
        val content = source.readText()
        TraceStore(dir, now = { 2000 }).use { store ->
            val archive = export(store)
            assertEquals(content, archive[source.name]); assertFalse(source.exists())
            val gzip = File(dir, source.name + ".gz")
            assertTrue(gzip.length() < content.length); assertEquals(1000L, gzip.lastModified())
        }
    }
    @Test fun crashRecoveryKeepsOneAuthoritativeSourceAndIgnoresUnownedFiles() {
        val dir = directory(); val source = raw(dir).apply { writeText("source bytes\n") }
        val zipped = File(dir, source.name + ".gz")
        GZIPOutputStream(zipped.outputStream()).use { it.write(source.readBytes()) }
        File(dir, source.name + ".gz.tmp").writeText("unfinished")
        val unrelated = File(dir, "user-note.txt").apply { writeText("keep") }
        TraceStore(dir).use { store ->
            val archive = export(store)
            assertEquals("source bytes\n", archive[source.name]); assertEquals(2, archive.size)
            assertFalse(source.exists()); assertFalse(File(dir, source.name + ".gz.tmp").exists())
            await(store::clear); assertTrue(unrelated.exists()); assertFalse(zipped.exists())
            store.record("after-clear"); assertTrue(export(store).values.any { "after-clear" in it })
        }
    }
    @Test fun corruptArchiveWithRawSourceRecoversButSoleCorruptArchiveFailsExport() {
        val dir = directory(); val source = raw(dir).apply { writeText("valid\n") }
        File(dir, source.name + ".gz").writeText("bad")
        TraceStore(dir).use { assertEquals("valid\n", export(it)[source.name]) }
        val broken = raw(directory()); File(broken.parentFile, broken.name + ".gz").writeText("bad")
        TraceStore(requireNotNull(broken.parentFile)).use { store ->
            try { export(store); fail("corrupt data must not silently disappear") } catch (_: java.io.IOException) { }
        }
    }
    @Test fun ageAppliesToActiveSegmentWithoutRenewingOnAppendAndClockBackwardsIsConservative() {
        val dir = directory(); var clock = 1000L
        TraceStore(dir, now = { clock }, maxAgeMs = 100).use { store ->
            store.record("first"); export(store)
            clock = 1050; store.record("second"); export(store)
            clock = 900; assertTrue(export(store).values.any { "first" in it })
            clock = 1101; store.record("new"); val data = export(store).values.joinToString()
            assertFalse("first" in data); assertFalse("second" in data); assertTrue("new" in data)
        }
    }
    @Test fun incompressiblePayloadsRespectBudgetAndRetainNewestWithoutEightFileLimit() {
        val dir = directory()
        TraceStore(dir, maxFileBytes = 1024, maxTotalBytes = 4096).use { store ->
            val random = java.util.Random(42)
            repeat(100) { i ->
                store.record("event-$i", mapOf("bytes" to ByteArray(200).also(random::nextBytes).joinToString("") { "%02x".format(it) }))
                val info = await(store::storageInfo)
                assertTrue(info.bytes <= 4096); assertTrue(dir.listFiles()!!.sumOf { it.length() } <= 4096)
            }
            assertTrue(export(store).values.any { "event-99" in it })
        }
        val frequent = directory()
        repeat(12) { n -> TraceStore(frequent).use { store -> store.record("restart-$n"); export(store) } }
        TraceStore(frequent).use { store -> assertEquals(12, export(store).keys.count { it.endsWith(".jsonl") }) }
    }
    @Test fun unknownLargeFilesAreNeverDeletedAndPreventBudgetOverrun() {
        val dir = directory(); val unknown = File(dir, "unowned.bin").apply { writeBytes(ByteArray(4096)) }
        TraceStore(dir, maxTotalBytes = 4096).use { store ->
            store.record("cannot-fit"); val info = await(store::storageInfo)
            assertEquals(4096L, info.bytes); assertTrue(unknown.exists()); assertTrue(store.status.contains("dropped=1"))
            await(store::clear); assertEquals(4096L, unknown.length())
        }
    }
    @Test fun clearQueuesBehindExportAndNeverInterleavesSnapshot() {
        val dir = directory(); TraceStore(dir).use { store ->
            store.record("before"); val entered = CountDownLatch(1); val release = CountDownLatch(1); val exported = CountDownLatch(1)
            val bytes = ByteArrayOutputStream()
            store.export({ entered.countDown(); release.await(); bytes }) { exported.countDown() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val cleared = CountDownLatch(1); store.clear { _, failure -> assertNull(failure); cleared.countDown() }
            store.record("after"); release.countDown()
            assertTrue(exported.await(5, TimeUnit.SECONDS)); assertTrue(cleared.await(5, TimeUnit.SECONDS))
            assertTrue(export(store).values.any { "after" in it }); assertFalse(export(store).values.any { "before" in it })
        }
    }
    @Test fun comparisonReadFailurePreservesBothCopiesAndDoesNotInferCorruption() {
        val dir = directory(); val source = raw(dir).apply { writeText("only evidence\n") }
        val zipped = File(dir, source.name + ".gz")
        GZIPOutputStream(zipped.outputStream()).use { it.write(source.readBytes()) }
        val io = object : TraceFileIo() {
            override fun open(file: File): java.io.InputStream {
                if (file == source) throw java.io.IOException("simulated transient read error")
                return super.open(file)
            }
        }
        TraceStore(dir, io = io).use { store ->
            await(store::storageInfo)
            assertTrue(source.exists()); assertTrue(zipped.exists())
            assertFalse(store.status.contains("errors=0"))
        }
        TraceStore(dir).use { store -> assertEquals("only evidence\n", export(store)[source.name]) }
    }
    @Test fun failedPublishOrDurabilityKeepsRawAndNeverExceedsBudgetIncludingScratch() {
        for (stage in listOf("rename", "sync", "ok")) {
            val dir = directory(); val source = raw(dir).apply { writeText("evidence\n".repeat(100)) }
            val original = source.readText(); var peak = 0L
            val io = object : TraceFileIo() {
                override fun commit(from: File, to: File) {
                    peak = dir.listFiles()!!.sumOf { it.length() }
                    assertTrue(peak <= 4096)
                    if (stage == "rename") throw java.io.IOException("simulated rename failure")
                    super.commit(from, to)
                }
                override fun syncDirectory(directory: File) {
                    if (stage == "sync") throw java.io.IOException("simulated durability failure")
                    super.syncDirectory(directory)
                }
            }
            TraceStore(dir, maxTotalBytes = 4096, io = io).use { store ->
                await(store::storageInfo)
                assertTrue(peak > source.length())
                if (stage != "ok") assertTrue(source.exists())
                assertFalse(File(dir, source.name + ".gz.tmp").exists())
                assertEquals(original, export(store)[source.name])
            }
            TraceStore(dir).use { store -> assertEquals(original, export(store)[source.name]) }
        }
    }

}
