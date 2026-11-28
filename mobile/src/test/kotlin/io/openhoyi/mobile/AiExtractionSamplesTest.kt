package io.openhoyi.mobile

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiExtractionSamplesTest {
    private val shot = BrewJournal.Observation("shot-1", "curve", 1, null, null, "UNKNOWN", "private error", null)
    private fun point(at: Long) = ShotPoint(at, 91, 23, 123, 9325, null)

    @Test fun unitsAndUnknownEvidenceArePreservedWithoutReconstruction() {
        val point = point(1234).copy(weightHundredthsGram = 3456, scaleFlowHundredths = 125,
            brewing = false, statusBits = 7, beverageHundredthsGram = 3210,
            beverageSource = BeverageWeightSource.entries.first(), beverageSegmentId = 2)
        val page = AiExtractionSamples.read("shot-1", 0, 100, { listOf(shot) }, { listOf(point, point(2234)) })
        val row = page.getJSONArray("items").getJSONObject(0)
        assertEquals(1234, row.getLong("elapsedMs"))
        assertEquals(9.1, row.getDouble("pressureBar"), 0.0)
        assertEquals(2.3, row.getDouble("machineFlowMlPerSecond"), 0.0)
        assertEquals(12.3, row.getDouble("waterMl"), 0.0)
        assertEquals(93.25, row.getDouble("temperatureC"), 0.0)
        assertEquals(34.56, row.getDouble("scaleWeightGrams"), 0.0)
        assertEquals(1.25, row.getDouble("estimatedScaleFlowGramsPerSecond"), 0.0)
        assertEquals(32.1, row.getDouble("beverageWeightGrams"), 0.0)
        assertEquals(point.beverageSource!!.name, row.getString("beverageWeightSource"))
        assertEquals(2, row.getInt("beverageSegmentId")); assertFalse(row.getBoolean("brewing"))
        val legacy = page.getJSONArray("items").getJSONObject(1)
        for (key in listOf("scaleWeightGrams", "estimatedScaleFlowGramsPerSecond", "brewing", "statusBits", "beverageWeightGrams", "beverageWeightSource", "beverageSegmentId")) assertTrue(legacy.isNull(key))
        assertFalse(page.toString().contains("private error"))
    }

    @Test fun allStoredSamplesPagePast500WithoutDuplicatesOrTruncatedJson() {
        val points = (0 until 2000).map { point(it.toLong()).copy(elapsedMs = it.toLong(),
            pressureTenthsBar = Int.MIN_VALUE, waterTenthsMl = Int.MAX_VALUE, weightHundredthsGram = Int.MAX_VALUE) }
        val seen = mutableListOf<Long>(); var offset = 0; var pages = 0
        while (true) {
            val page = AiExtractionSamples.read("shot-1", offset, 100, { listOf(shot) }, { points })
            assertTrue(page.toString().length <= 32000)
            val decoded = JSONObject(page.toString()); val items = decoded.getJSONArray("items")
            assertEquals(offset, decoded.getInt("offset")); assertEquals(2000, decoded.getInt("total"))
            assertTrue(items.length() in 1..100)
            for (i in 0 until items.length()) seen += items.getJSONObject(i).getLong("elapsedMs")
            pages++
            if (!decoded.getBoolean("truncated")) { assertTrue(decoded.isNull("nextOffset")); break }
            assertEquals(offset + items.length(), decoded.getInt("nextOffset"))
            offset = decoded.getInt("nextOffset")
        }
        assertEquals((0L until 2000L).toList(), seen); assertTrue(pages >= 20)
    }

    @Test fun invalidUnknownAndUnavailableDoNotReadOrLeakPrivateFiles() {
        var reads = 0
        fun read(id: String, offset: Int = 0, limit: Int = 100) = AiExtractionSamples.read(id, offset, limit,
            { listOf(shot) }, { reads++; throw java.io.IOException("private/path secret") })
        for (id in listOf("", "../shot-1", "x".repeat(65))) assertEquals("invalid_arguments", read(id).getString("status"))
        for ((offset, limit) in listOf(-1 to 1, 2001 to 1, 0 to 0, 0 to 101)) assertEquals("invalid_arguments", read("shot-1", offset, limit).getString("status"))
        assertEquals("not_found", read("orphan").getString("status")); assertEquals(0, reads)
        assertEquals("unavailable", read("shot-1").getString("status")); assertEquals(1, reads)
        assertEquals("samples_unavailable", AiExtractionSamples.read("shot-1", 0, 100, { listOf(shot) }, { emptyList() }).getString("status"))
    }

    @Test fun anExplicitHistoryReviewReadsOnlyItsSelectedSavedCurve() {
        val reads = mutableListOf<String>()
        fun read(id: String) = AiExtractionSamples.read(id, 0, 100, { listOf(shot) },
            { selected -> reads += selected; listOf(point(10)) }, selectedHistoryId = "older")

        assertEquals("not_found", read("shot-1").getString("status"))
        assertTrue(reads.isEmpty())
        assertEquals("older", read("older").getString("shotId"))
        assertEquals(listOf("older"), reads)
    }

    @Test fun realStoreReadsLegacyAndRejectsCorruptionWithoutWriting() {
        val dir = Files.createTempDirectory("ai-samples-").toFile()
        try {
            val raw = java.io.File(dir, "samples-shot-1.tsv")
            raw.writeText("# openhoyi-shot-points-v1\n10\t90\t20\t100\t9300\t\n")
            val before = raw.readBytes()
            val result = AiExtractionSamples.read("shot-1", 0, 100, { listOf(shot) }, ShotSamplesStore(dir)::load)
            assertEquals("available", result.getString("status")); assertArrayEquals(before, raw.readBytes())
            assertEquals(listOf(raw.name), dir.list()!!.toList())
            raw.writeText("broken private contents")
            val broken = raw.readBytes()
            assertEquals("unavailable", AiExtractionSamples.read("shot-1", 0, 100, { listOf(shot) }, ShotSamplesStore(dir)::load).getString("status"))
            assertArrayEquals(broken, raw.readBytes())
        } finally { dir.deleteRecursively() }
    }
}
