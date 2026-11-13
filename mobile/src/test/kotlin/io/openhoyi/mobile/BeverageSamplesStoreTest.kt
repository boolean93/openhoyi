package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class BeverageSamplesStoreTest {
    private fun points() = listOf(
        ShotPoint(0, 90, 20, 0, 9200, 12000, brewing=true, beverageHundredthsGram=null),
        ShotPoint(500, 90, 20, 10, 9200, 12050, brewing=true, beverageHundredthsGram=50, beverageSource=BeverageWeightSource.CONFIRMED),
        ShotPoint(1000, 90, 20, 20, 9200, null, brewing=true, beverageHundredthsGram=null),
        ShotPoint(1500, 90, 20, 30, 9200, 12100, brewing=true, beverageHundredthsGram=100, beverageSource=BeverageWeightSource.ESTIMATED))
    private fun raw(points: List<ShotPoint>) = points.map { it.copy(beverageHundredthsGram=null, beverageSource=null) }
    private fun inDirectory(block: (File, ShotSamplesStore) -> Unit) {
        val dir=Files.createTempDirectory("beverage-samples-test").toFile()
        try { block(dir,ShotSamplesStore(dir)) } finally { dir.deleteRecursively() }
    }
    private fun sidecar(dir: File) = dir.resolve("beverage-samples-cup.tsv")

    @Test fun roundtripKeepsNullGapsAndRawV3BytesIdenticalToLegacySave() = inDirectory { dir,store ->
        val points=points()
        store.save("cup",points)
        store.save("legacy",raw(points))
        assertEquals(points,store.load("cup"))
        assertArrayEquals(dir.resolve("samples-legacy.tsv").readBytes(),dir.resolve("samples-cup.tsv").readBytes())
        val rawLines=dir.resolve("samples-cup.tsv").readLines()
        assertEquals("# openhoyi-shot-points-v3",rawLines.first())
        assertTrue(rawLines.drop(1).all { it.split('\t').size==8 })
        assertFalse(dir.resolve("beverage-samples-legacy.tsv").exists())
        assertEquals("# raw-sha256=" + MessageDigest.getInstance("SHA-256")
            .digest(dir.resolve("samples-cup.tsv").readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) },
            sidecar(dir).readLines()[2])
    }
    @Test fun rawV4StatusAndOldV1V2StayReadableWithoutNet() = inDirectory { dir,store ->
        val points=points().map { it.copy(statusBits=128) }
        store.save("cup",points)
        assertEquals("# openhoyi-shot-points-v4",dir.resolve("samples-cup.tsv").readLines().first())
        assertTrue(dir.resolve("samples-cup.tsv").readLines().drop(1).all { it.split('\t').size==9 })
        assertEquals(points,store.load("cup"))
        dir.resolve("samples-old1.tsv").writeText("# openhoyi-shot-points-v1\n0\t90\t20\t0\t9200\t12000\n")
        dir.resolve("samples-old2.tsv").writeText("# openhoyi-shot-points-v2\n0\t90\t20\t0\t9200\t12000\t5\n")
        for (id in listOf("old1","old2")) {
            assertEquals(12000,store.load(id).single().weightHundredthsGram)
            assertNull(store.load(id).single().beverageHundredthsGram)
        }
    }
    @Test fun missingCorruptOrUnknownSidecarOnlyDropsNet() = inDirectory { dir,store ->
        val points=points();store.save("cup",points)
        val valid=sidecar(dir).readText()
        val lines=valid.lines()
        val corruptions=listOf("garbage\n", valid.replace("raw-sha256=", "wrong-sha256="),
            valid.replace("app-shot-baseline-v1", "future-source"),
            valid.replace("500\t50", "500\tnot-a-number"), valid.replace("CONFIRMED", "FUTURE"),
            valid.replace("500\t50\tCONFIRMED", "500\t50\t"),
            valid.replace("500\t50\tCONFIRMED", "500\t\tCONFIRMED"), valid.replace("500\t50", "500\t-1"), valid.replace("500\t50", "500\t2147483648"),
            valid.replace("500\t50", "0\t50"), valid.replace("500\t50", "501\t50"),
            valid.replace("500\t50", "500\t50\textra"), valid + "2000\t200\n",
            lines.dropLast(2).joinToString("\n") + "\n", "x".repeat(512*1024+1))
        corruptions.forEach { broken ->
            sidecar(dir).writeText(broken)
            assertEquals(raw(points),store.load("cup"))
        }
        sidecar(dir).delete()
        assertEquals(raw(points),store.load("cup"))
    }
    @Test fun staleHashAndMismatchedRawCannotAttachNet() = inDirectory { dir,store ->
        val points=points();store.save("cup",points)
        val raw=dir.resolve("samples-cup.tsv")
        raw.writeText(raw.readText().replace("12050", "13050"))
        val loaded=store.load("cup")
        assertEquals(13050,loaded[1].weightHundredthsGram)
        assertTrue(loaded.all { it.beverageHundredthsGram==null })
    }
    @Test fun sameRawOverwriteWithoutNetCannotResurrectOldSidecar() = inDirectory { dir,store ->
        val points=points();store.save("cup",points)
        val before=dir.resolve("samples-cup.tsv").readBytes()
        store.save("cup",raw(points))
        assertArrayEquals(before,dir.resolve("samples-cup.tsv").readBytes())
        assertFalse(sidecar(dir).exists())
        assertEquals(raw(points),ShotSamplesStore(dir).load("cup"))
    }
    @Test fun netRequiresAnObservedRawWeightAndUnknownFileIsNotOverwritten() = inDirectory { dir,store ->
        assertThrows(IllegalArgumentException::class.java) {
            store.save("cup",listOf(points()[0].copy(weightHundredthsGram=null,beverageHundredthsGram=10,beverageSource=BeverageWeightSource.ESTIMATED)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.save("cup",listOf(points()[1].copy(beverageSource=null)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.save("cup",listOf(points()[1].copy(beverageHundredthsGram=null)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.save("cup",listOf(points()[1].copy(beverageHundredthsGram=-1)))
        }
        val points=points();store.save("cup",points)
        val before=dir.resolve("samples-cup.tsv").readBytes()
        sidecar(dir).writeText("# future-format\nkeep\n")
        assertThrows(IllegalStateException::class.java) { store.save("cup",raw(points)) }
        assertEquals("# future-format\nkeep\n",sidecar(dir).readText())
        assertArrayEquals(before,dir.resolve("samples-cup.tsv").readBytes())
    }
    @Test fun pruneOnlyRemovesExpiredOwnedEvidenceAndKeepsRecentOrUnknownFiles() = inDirectory { dir,_ ->
        val now=System.currentTimeMillis();val old=now-31L*24*60*60*1000
        val store=ShotSamplesStore(dir) { now }
        listOf("expired","recent","retained","orphan","future").forEach { store.save(it,points()) }
        listOf("expired","retained").forEach { dir.resolve("samples-$it.tsv").setLastModified(old) }
        dir.resolve("samples-orphan.tsv").delete();dir.resolve("beverage-samples-orphan.tsv").setLastModified(old)
        dir.resolve("samples-future.tsv").delete()
        dir.resolve("beverage-samples-future.tsv").apply { writeText("# future-format\nkeep\n");setLastModified(old) }
        store.prune(setOf("retained"))
        assertFalse(dir.resolve("samples-expired.tsv").exists())
        assertFalse(dir.resolve("beverage-samples-expired.tsv").exists())
        assertFalse(dir.resolve("beverage-samples-orphan.tsv").exists())
        assertEquals(points(),store.load("recent"));assertEquals(points(),store.load("retained"))
        assertTrue(dir.resolve("beverage-samples-future.tsv").exists())
    }
}
