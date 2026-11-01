package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class CurveImportPreviewLayoutPolicyTest {
    @Test fun measuredTableGetsSpaceBeforeExpandableChart() {
        val layout = CurveImportPreviewLayoutPolicy.allocate(1100, 283, 356, 360)
        assertEquals(461, layout.chartHeight)
        assertEquals(1100, layout.contentHeight)
        assertFalse(layout.scrollRequired)
    }

    @Test fun largeTextKeepsEveryTableRowAndMinimumChartInLocalScroll() {
        val layout = CurveImportPreviewLayoutPolicy.allocate(1000, 300, 431, 360)
        assertEquals(360, layout.chartHeight)
        assertEquals(1091, layout.contentHeight)
        assertTrue(layout.scrollRequired)
    }

    @Test fun exactFitDoesNotScrollButOnePixelShortDoes() {
        assertFalse(CurveImportPreviewLayoutPolicy.allocate(1091, 300, 431, 360).scrollRequired)
        assertTrue(CurveImportPreviewLayoutPolicy.allocate(1090, 300, 431, 360).scrollRequired)
    }

    @Test fun evenZeroViewportRetainsAllMeasuredContent() {
        val layout = CurveImportPreviewLayoutPolicy.allocate(0, 300, 431, 360)
        assertEquals(1091, layout.contentHeight)
        assertEquals(360, layout.chartHeight)
        assertTrue(layout.scrollRequired)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeMeasuredTableIsNotValidEvidence() {
        CurveImportPreviewLayoutPolicy.allocate(1000, 300, -1, 360)
    }
}
