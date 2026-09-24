package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files

class LegacyHistoryTest {
    private fun json(id: String = "abc123", t: String = "[0,1,1,2]", press: String = "[0,2.1,4.0,3.2]") =
        """{"version":1,"items":[{"id":"$id","createdAt":1720000000000,"durationSec":18,
        "chartSlot":6,"profileName":"手动","snapshot":{"tempBrewSv":93},
        "points":{"t":$t,"press":$press,"flow":[0,1,2,1],
        "wFlow":[0.1,0.2,0.3,0.4],"wTrend":[0.02,0.04,0.06,0.08]}}]}"""

    @Test fun parsesOldArraysWithoutInventingNativeMeasurements() {
        val shot = LegacyHistoryCodec.parse(json()).single()
        assertEquals("abc123", shot.id)
        assertEquals(6, shot.chartSlot)
        assertEquals(18, shot.durationSec)
        assertEquals(listOf(0.0, 1.0, 1.0, 2.0), shot.points.map(LegacyPoint::seconds))
        assertEquals(4.0, shot.points[2].pressureBar, 0.0001)
        assertEquals(0.06, shot.points[2].oldWTrend, 0.0001)
        assertTrue(shot.snapshotJson?.contains("tempBrewSv") == true)
        assertEquals(shot, LegacyHistoryCodec.parse(LegacyHistoryCodec.encode(listOf(shot))).single())
    }

    @Test fun rejectsPartialOrContradictoryHistoryWithoutDroppingRows() {
        assertThrows(IOException::class.java) { LegacyHistoryCodec.parse(json(t = "[0,1,2]")) }
        assertThrows(IOException::class.java) { LegacyHistoryCodec.parse(json(t = "[0,2,1,3]")) }
        assertThrows(IOException::class.java) { LegacyHistoryCodec.parse(json(press = "[0,2.1,NaN,3.2]")) }
        assertThrows(IOException::class.java) { LegacyHistoryCodec.parse(json().replace("\"version\":1", "\"version\":2")) }
    }

    @Test fun importIsIdempotentAndConflictDoesNotReplaceExistingRecord() {
        val directory = Files.createTempDirectory("legacy-history-test").toFile()
        try {
            val store = LegacyHistoryStore(directory.resolve("history.json"))
            fun import(value: String) = store.import(ByteArrayInputStream(value.toByteArray()))
            assertEquals(1, import(json()).added)
            assertEquals(0, import(json()).added)
            assertThrows(IOException::class.java) { import(json(press = "[0,2.1,9.0,3.2]")) }
            assertEquals(4.0, store.list().single().points[2].pressureBar, 0.0001)
        } finally { directory.deleteRecursively() }
    }
}
