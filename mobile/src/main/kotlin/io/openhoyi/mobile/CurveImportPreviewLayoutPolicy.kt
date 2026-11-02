package io.openhoyi.mobile

internal data class CurveImportPreviewAllocation(
    val chartHeight: Int,
    val contentHeight: Int,
    val scrollRequired: Boolean,
)

/** Full measured table is never traded for a larger chart or silently removed. */
internal object CurveImportPreviewLayoutPolicy {
    fun allocate(availableHeight: Int, fixedHeight: Int, tableHeight: Int,
        minimumChartHeight: Int): CurveImportPreviewAllocation {
        require(availableHeight >= 0 && fixedHeight >= 0 && tableHeight >= 0 && minimumChartHeight > 0)
        val chartHeight = maxOf(minimumChartHeight, availableHeight - fixedHeight - tableHeight)
        val contentHeight = fixedHeight + tableHeight + chartHeight
        return CurveImportPreviewAllocation(chartHeight, contentHeight, contentHeight > availableHeight)
    }
}
