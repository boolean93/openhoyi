package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class LegacyCurveWireAuditTest {
    private val encoderHash = "635ddbe7bbc63d8d74b5053cd5c2ca1d5e8a0dfab0f24447171e5043fd01855f"

    private fun sample(): Pair<ByteArray, String> {
        val curve = File("src/main/assets/factory_curves_v3.tsv").inputStream().use(FactoryCurveCatalog::load).first()
        val factory = File("src/main/assets/factory_curves_v3.tsv").inputStream().use(FactoryCurveCatalog::load)
        val oracle = File("src/main/assets/factory_wire_v1.tsv").inputStream().use { temp ->
            File("src/main/assets/factory_slot_wire_v1.tsv").inputStream().use { slots ->
                FactoryWireProof.load(temp, factory, slots)
            }
        }
        val row = JSONObject().put("name", curve.name).put("category", curve.category)
            .put("flow", curve.flowMl).put("weight", curve.weightTenthsGram)
            .put("wdelta", curve.weightCompensationTenthsGram).put("time", curve.preinfusionSeconds)
            .put("temp", curve.temperatureC).put("press", curve.pressureLogic)
            .put("chart", curve.variableFlowLogic).put("seg", curve.segmentCount)
            .put("time1", curve.firstDurationSeconds)
        for (part in 1..4) {
            row.put("press$part", curve.targets[part - 1])
            row.put("flow$part", curve.segmentFlowMl[part - 1])
        }
        val bytes = JSONObject().put("format", "openhoyi-legacy-curves-v1")
            .put("items", JSONArray().put(JSONObject.NULL).put(row)).toString().toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val lines = mutableListOf("# legacy-curve-wire-v1\tsource-sha256=${"a".repeat(64)}\tencoder-sha256=$encoderHash\texport-sha256=$hash")
        for (slot in listOf(7, 1, 2, 3, 4, 5))
            lines += "1\t$slot\t${oracle.expected(curve.id, false, slot)}\t${oracle.expected(curve.id, true, slot)}"
        return bytes to lines.joinToString("\n", postfix = "\n")
    }

    @Test fun matchesEveryModeAndSlotAndPreservesEmptyPositions() {
        val (export, proof) = sample()
        val report = LegacyCurveWireAudit.verify(export, proof)
        assertEquals(1, report.curveCount)
        assertEquals(12, report.frameCount)
    }

    @Test fun rejectsChangedExportHashEvenWhenRowsLookValid() {
        val (export, proof) = sample()
        val changed = export.toString(Charsets.UTF_8).plus(" ").toByteArray()
        assertThrows(IllegalArgumentException::class.java) { LegacyCurveWireAudit.verify(changed, proof) }
    }

    @Test fun rejectsIncompleteOrChangedProof() {
        val (export, proof) = sample()
        val lines = proof.lines().filter { it.isNotEmpty() }
        assertThrows(IllegalArgumentException::class.java) {
            LegacyCurveWireAudit.verify(export, lines.dropLast(1).joinToString("\n", postfix = "\n"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LegacyCurveWireAudit.verify(export, proof.replace("\t1\t", "\t2\t"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LegacyCurveWireAudit.verify(export, proof.replace(encoderHash, "b".repeat(64)))
        }
    }

    @Test fun rejectsCurveThatCannotBecomeStrictNativeParameters() {
        val (export, proof) = sample()
        val root = JSONObject(export.toString(Charsets.UTF_8))
        root.getJSONArray("items").getJSONObject(1).remove("time1")
        val changed = root.toString().toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(changed).joinToString("") { "%02x".format(it) }
        val rebound = proof.replace(Regex("export-sha256=[a-f0-9]{64}"), "export-sha256=$hash")
        assertThrows(IllegalArgumentException::class.java) { LegacyCurveWireAudit.verify(changed, rebound) }
    }

    @Test fun auditsUserExportWhenPathsAreProvided() {
        val exportPath = System.getenv("HOYI_LEGACY_CURVE_EXPORT")
        val proofPath = System.getenv("HOYI_LEGACY_CURVE_PROOF")
        assumeTrue("Set both HOYI_LEGACY_CURVE_EXPORT and HOYI_LEGACY_CURVE_PROOF",
            !exportPath.isNullOrBlank() && !proofPath.isNullOrBlank())
        val report = LegacyCurveWireAudit.verify(File(exportPath!!).readBytes(), File(proofPath!!).readText())
        assertTrue(report.curveCount > 0)
        assertEquals(report.curveCount * 12, report.frameCount)
    }

    @Test fun edgeCorpusFromOldEncoderMatchesNativeAcrossAllSlotsAndScaleModes() {
        val export = File("src/test/resources/legacy_curve_edges.json").readBytes()
        val proof = File("src/test/resources/legacy_curve_edges.tsv").readText()
        val report = LegacyCurveWireAudit.verify(export, proof)
        assertEquals(128, report.curveCount)
        assertEquals(1536, report.frameCount)
    }
}
