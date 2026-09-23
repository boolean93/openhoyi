package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

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
}
