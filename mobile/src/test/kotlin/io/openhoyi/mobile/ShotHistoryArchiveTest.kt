package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ShotHistoryArchiveTest {
    @Test fun archiveKeepsUnknownOutcomeAndExportsAvailableSamplesWithoutPathInjection() {
        val entries = listOf(
            ShotHistory.Entry("../shot-1", "curve-1", 1_000, null, null, ShotHistory.Status.UNKNOWN,
                "link, \"lost\"\ncheck machine", null, 7),
            ShotHistory.Entry("shot-2", "curve-2", 2_000, 3_000, 1_000, ShotHistory.Status.ENDED,
                null, 2534, 1),
        )
        val output = ByteArrayOutputStream()
        val report = ShotHistoryArchive.write(entries, { id ->
            if (id == "../shot-1") listOf(ShotPoint(100, 90, 20, 30, 9200, null)) else emptyList()
        }, output)
        assertEquals(2, report.records)
        assertEquals(1, report.sampleFiles)
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(setOf("README.txt", "history.csv", "shots/0001.tsv"), files.keys)
        assertTrue(files.getValue("history.csv").contains("UNKNOWN"))
        assertTrue(files.getValue("history.csv").contains("\"link, \"\"lost\"\"\ncheck machine\""))
        assertTrue(files.getValue("history.csv").contains("shots/0001.tsv"))
        assertTrue(files.getValue("shots/0001.tsv").contains("100\t90\t20\t30\t9200\t"))
    }

    @Test fun oneUnreadableCurveDoesNotLoseTheRestOfHistory() {
        val entries = listOf("bad", "good").mapIndexed { index, id ->
            ShotHistory.Entry(id, "curve", index.toLong(), null, null,
                ShotHistory.Status.UNKNOWN, null, null)
        }
        val output = ByteArrayOutputStream()
        val report = ShotHistoryArchive.write(entries, { id ->
            if (id == "bad") error("damaged sample")
            listOf(ShotPoint(0, 90, 20, 30, 9200, null))
        }, output)
        assertEquals(1, report.unavailable)
        assertEquals(1, report.sampleFiles)
        val files = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files += entry.name
            }
        }
        assertTrue("history.csv" in files)
        assertTrue("shots/0002.tsv" in files)
    }
}
