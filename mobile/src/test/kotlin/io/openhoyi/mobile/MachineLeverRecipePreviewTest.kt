package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class MachineLeverRecipePreviewTest {
    @Test fun storedSnapshotDoesNotDependOnMutableCurveLibrary() {
        val snapshot = "08825c0046031e5a0000009608019000000000d8"
        val preview = requireNotNull(MachineLeverRecipePreview.from(snapshot))
        assertEquals(listOf(30, 90), preview.stages.map { it.target })
        assertTrue(preview.stages.all { it.unit == CurveStageParameters.Unit.PRESSURE_BAR })
    }
    @Test fun badChecksumUnknownFrameAndTruncationNeverProduceMachinePreview() {
        assertNull(MachineLeverRecipePreview.from(""))
        assertNull(MachineLeverRecipePreview.from("08825c0046031e5a0000009608019000000000d9"))
        assertNull(MachineLeverRecipePreview.from("02825c0046031e5a0000009608019000000000d2"))
    }
}
