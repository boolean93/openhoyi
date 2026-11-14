package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class CurveMetadataInputTest {
    @Test fun temperatureRequiresIntegerWithinExistingDraftBounds() {
        for (value in listOf("75", "93", "105")) assertEquals(value.toInt(), CurveMetadataInput.temperature(value))
        for (value in listOf("", "0", "74", "106", "92.5", "-1", "NaN", "1000000000000000000000"))
            assertTrue(value, runCatching { CurveMetadataInput.temperature(value) }.isFailure)
    }
    @Test fun weightRetainsUnsetZeroAndExactHundredthsWithoutClamping() {
        assertEquals(0, CurveMetadataInput.targetWeight("0"))
        assertEquals(1, CurveMetadataInput.targetWeight("0.01"))
        assertEquals(3655, CurveMetadataInput.targetWeight("36.55"))
        assertEquals(20000, CurveMetadataInput.targetWeight("200"))
        for (value in listOf("", "-1", "200.01", "1000", "36.555", "NaN", "1e2", "99999999999999999"))
            assertTrue(value, runCatching { CurveMetadataInput.targetWeight(value) }.isFailure)
    }
    @Test fun documentImportAndExportUseSameBoundsAndKeepZeroSentinel() {
        val doc = CustomCurveDocument(CustomCurveDocument.newId(), "Bounds", 75, 0,
            CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(90, 300)))
        assertEquals(doc, CustomCurveDocument.decode(doc.encode()))
        val upper = doc.copy(temperatureC = 105, targetHundredthsGram = 20000)
        assertEquals(upper, CustomCurveDocument.decode(upper.encode()))
        for (invalid in listOf(doc.copy(temperatureC = 0), doc.copy(temperatureC = 106), doc.copy(targetHundredthsGram = 20001)))
            assertTrue(runCatching { invalid.validate() }.isFailure)
    }
}
