package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShotSamplesStoreTest {
    @Test fun recentOrphanedSamplesSurviveMissingHistoryIndexButExpiredOnesArePruned() {
        val dir = Files.createTempDirectory("openhoyi-samples").toFile()
        try {
            val now=System.currentTimeMillis()
            val store = ShotSamplesStore(dir) { now }
            val points = listOf(
                ShotPoint(0, 90, 20, 0, 9200, null),
                ShotPoint(100, 91, 21, 10, 9201, -20, 135),
            )
            store.save("shot-1", points)
            store.save("shot-2", points)
            assertEquals(points, store.load("shot-1"))
            dir.resolve("unrelated.txt").writeText("keep")
            store.prune(emptySet())
            assertEquals(points,store.load("shot-1"))
            assertEquals(points,store.load("shot-2"))
            store.prune(setOf("shot-2"))
            assertEquals(points,store.load("shot-1"))
            assertEquals(points, store.load("shot-2"))
            assertTrue(dir.resolve("samples-shot-1.tsv").setLastModified(now - 31L * 24 * 60 * 60 * 1000))
            store.prune(setOf("shot-2"))
            assertTrue(store.load("shot-1").isEmpty())
            assertTrue(dir.resolve("unrelated.txt").exists())
            assertThrows(IllegalArgumentException::class.java) { store.load("../outside") }
        } finally { dir.deleteRecursively() }
    }

    @Test fun interruptedShotRetainsLastCompleteCheckpoint() {
        val dir = Files.createTempDirectory("openhoyi-partial").toFile()
        try {
            val point = ShotPoint(100, 90, 20, 0, 9200, null)
            val firstProcess = ShotSamplesStore(dir)
            firstProcess.save("shot-interrupted", listOf(point))
            val afterRestart = ShotSamplesStore(dir)
            assertEquals(listOf(point), afterRestart.load("shot-interrupted"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun readsLegacySixColumnSamplesWithoutInventingScaleFlow() {
        val dir = Files.createTempDirectory("openhoyi-samples-v1").toFile()
        try {
            dir.resolve("samples-shot-old.tsv").writeText(
                "# openhoyi-shot-points-v1\n100\t90\t20\t30\t9200\t2500\n")
            val point = ShotSamplesStore(dir).load("shot-old").single()
            assertEquals(2500, point.weightHundredthsGram)
            assertNull(point.scaleFlowHundredths)
        } finally { dir.deleteRecursively() }
    }

    @Test fun malformedExistingSampleFileIsNotReportedAsEmpty() {
        val dir = Files.createTempDirectory("openhoyi-corrupt-samples").toFile()
        try {
            val store = ShotSamplesStore(dir)
            val file = dir.resolve("samples-shot-corrupt.tsv")
            file.writeText("invalid header\n")
            assertThrows(IOException::class.java) { store.load("shot-corrupt") }
            file.writeText("# openhoyi-shot-points-v1\n100\t90\t20\t30\t9200\t\ninvalid row\n")
            assertThrows(IOException::class.java) { store.load("shot-corrupt") }
            file.writeText("# openhoyi-shot-points-v1\n100\t90\t20\t30\t9200\t\n100\t91\t21\t31\t9201\t\n")
            assertThrows(IOException::class.java) { store.load("shot-corrupt") }
            file.writeText("# openhoyi-shot-points-v2\n100\t90\t20\t30\t9200\t2500\tbad\n")
            assertThrows(IOException::class.java) { store.load("shot-corrupt") }
        } finally { dir.deleteRecursively() }
    }

    @Test fun oversizedExistingSampleFileIsNotSilentlyTruncated() {
        val dir = Files.createTempDirectory("openhoyi-oversized-samples").toFile()
        try {
            val store = ShotSamplesStore(dir)
            dir.resolve("samples-shot-large.tsv").writeText("x".repeat(512 * 1024 + 1))
            assertThrows(IOException::class.java) { store.load("shot-large") }
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedAsyncSaveDoesNotMasqueradeAsPersistedSamples() {
        val parent = Files.createTempFile("openhoyi-unwritable-samples", ".tmp").toFile()
        try {
            val failure = CountDownLatch(1)
            val repository = ShotSamplesRepository(parent) { failure.countDown() }
            repository.save("shot-1", listOf(ShotPoint(0, 90, 20, 0, 9200, null)))
            assertTrue(failure.await(3, TimeUnit.SECONDS))
            assertTrue(repository.load("shot-1").isEmpty())
        } finally { parent.delete() }
    }
    @Test fun v3RoundTripsKnownPhaseAndLegacyVersionsKeepUnknownPhase() {
        val dir=Files.createTempDirectory("phase-samples").toFile()
        try {
            val store=ShotSamplesStore(dir)
            val points=listOf(ShotPoint(0,90,20,0,9200,0,brewing=false),
                ShotPoint(500,90,20,0,9200,100,brewing=true),
                ShotPoint(1000,90,20,0,9200,200))
            store.save("phase",points)
            assertEquals("# openhoyi-shot-points-v3",dir.resolve("samples-phase.tsv").readLines().first())
            assertEquals(points,store.load("phase"))
            dir.resolve("samples-old1.tsv").writeText("# openhoyi-shot-points-v1\n100\t90\t20\t0\t9200\t100\n")
            dir.resolve("samples-old2.tsv").writeText("# openhoyi-shot-points-v2\n100\t90\t20\t0\t9200\t100\t5\n")
            assertNull(store.load("old1").single().brewing);assertNull(store.load("old2").single().brewing)
        } finally {dir.deleteRecursively()}
    }
    @Test fun v3RejectsGuessedOrMalformedPhaseAndScaleFlow() {
        val dir=Files.createTempDirectory("invalid-phase-samples").toFile()
        try {
            val file=dir.resolve("samples-bad.tsv");val store=ShotSamplesStore(dir)
            file.writeText("# openhoyi-shot-points-v3\n0\t90\t20\t0\t9200\t100\t5\tbrewing\n")
            assertThrows(IOException::class.java) {store.load("bad")}
            file.writeText("# openhoyi-shot-points-v3\n0\t90\t20\t0\t9200\t100\tbad\ttrue\n")
            assertThrows(IOException::class.java) {store.load("bad")}
        } finally {dir.deleteRecursively()}
    }

}
