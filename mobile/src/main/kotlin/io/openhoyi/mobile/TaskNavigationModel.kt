package io.openhoyi.mobile

internal enum class TaskDestination { BREW, CURVES, HISTORY, BEANS, SCALE, SETTINGS }

internal object TaskNavigationModel {
    val primary = listOf(TaskDestination.BREW, TaskDestination.CURVES, TaskDestination.HISTORY, TaskDestination.BEANS)
    val tools = listOf(TaskDestination.SCALE, TaskDestination.SETTINGS)
    private val pages = buildMap {
        fun group(destination: TaskDestination, names: String) = names.split(' ').forEach { put(it + "Activity", destination) }
        group(TaskDestination.BREW, "Home Extraction")
        group(TaskDestination.CURVES, "Curve CurveEditor CurveShare CurveImport CurveImportPreview LegacyCurve")
        group(TaskDestination.HISTORY, "History HistoryDetail BrewComparison BrewReview LegacyHistory LegacyHistoryDetail")
        group(TaskDestination.BEANS, "BeanInventory BeanEntry BeanBatch BeanLedger BeanPreparation")
        group(TaskDestination.SCALE, "Scale ScaleConnection ScaleSettings")
        group(TaskDestination.SETTINGS, "MachineSettings AppSettings")
    }
    fun forPage(simpleName: String): TaskDestination? = pages[simpleName]
}
