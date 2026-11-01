package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class TaskNavigationModelTest {
    @Test fun destinationsFollowTaskOrder() {
        assertEquals(listOf(TaskDestination.BREW, TaskDestination.CURVES, TaskDestination.HISTORY, TaskDestination.BEANS), TaskNavigationModel.primary)
        assertEquals(listOf(TaskDestination.SCALE, TaskDestination.SETTINGS), TaskNavigationModel.tools)
    }
    @Test fun everyConcretePageHasItsTask() {
        val groups = mapOf(
            TaskDestination.BREW to "Home Extraction",
            TaskDestination.CURVES to "Curve CurveEditor CurveShare CurveImport CurveImportPreview LegacyCurve",
            TaskDestination.HISTORY to "History HistoryDetail BrewComparison BrewReview LegacyHistory LegacyHistoryDetail",
            TaskDestination.BEANS to "BeanInventory BeanEntry BeanBatch BeanLedger BeanPreparation",
            TaskDestination.SCALE to "Scale ScaleConnection ScaleSettings",
            TaskDestination.SETTINGS to "MachineSettings AppSettings")
        groups.forEach { (destination, pages) -> pages.split(' ').forEach { assertEquals(it, destination, TaskNavigationModel.forPage(it + "Activity")) } }
        assertNull(TaskNavigationModel.forPage("UnknownActivity"))
    }
}
