package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShotSamplesStoreTest {
    @Test fun roundTripsSamplesAndPrunesOnlyKnownSampleFiles() {
        val dir = Files.createTempDirectory("openhoyi-samples").toFile()
        try {
            val store = ShotSamplesStore(dir)
            val points = listOf(
                ShotPoint(0, 90, 20, 0, 9200, null),
                ShotPoint(100, 91, 21, 10, 9201, -20),
            )
            store.save("shot-1", points)
            store.save("shot-2", points)
            assertEquals(points, store.load("shot-1"))
            dir.resolve("unrelated.txt").writeText("keep")
            store.prune(setOf("shot-2"))
            assertTrue(store.load("shot-1").isEmpty())
            assertEquals(points, store.load("shot-2"))
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
}
