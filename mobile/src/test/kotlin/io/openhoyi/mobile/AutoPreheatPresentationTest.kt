package io.openhoyi.mobile

import io.openhoyi.session.BrewPreparation
import org.junit.Assert.*
import org.junit.Test

class AutoPreheatPresentationTest {
    private fun facts() = AutoPreheatPresentation.Facts(true, true, BrewPreparation.State.WAITING_TEMP,
        "curve", 93, "curve", 93, 7000, 5000)
    @Test fun missingSettingsRetainsOnlyExistingAutomaticPreparationStatus() {
        assertFalse(AutoPreheatPresentation.showStudioStatus(null, false, null))
        assertFalse(AutoPreheatPresentation.showStudioStatus(null, false, " "))
        val retained = AutoPreheatPresentation.showStudioStatus(null, true, null)
        assertTrue(retained)
        val result = AutoPreheatPresentation.describe(facts().copy(studio = retained,
            connected = false, state = BrewPreparation.State.UNKNOWN, current = null))
        assertEquals(AutoPreheatPresentation.Phase.DISCONNECTED, result.phase)
        assertNull(result.progress)
        assertTrue(AutoPreheatPresentation.showStudioStatus(null, false, "Cancel not sent"))
        assertFalse(AutoPreheatPresentation.showStudioStatus(0, true, "Cancel not sent"))
        assertTrue(AutoPreheatPresentation.showStudioStatus(0x04, false, null))
    }
    @Test fun heatingUsesTemperatureEvidenceAndRestoresStartWithinOneDegree() {
        assertEquals(.4651f, AutoPreheatPresentation.describe(facts()).progress!!, .001f)
        assertEquals(AutoPreheatPresentation.Phase.HIDDEN,
            AutoPreheatPresentation.describe(facts().copy(current = 9200)).phase)
    }
    @Test fun coolingCannotAppearCompleteBeforeTarget() {
        val result = AutoPreheatPresentation.describe(facts().copy(current = 9500, initial = 10000))
        assertEquals(AutoPreheatPresentation.Phase.COOLING, result.phase)
        assertTrue(result.progress!! < 1f)
        assertNull(AutoPreheatPresentation.describe(facts().copy(current = 9500)).progress)
    }
    @Test fun missingFreshTemperatureHasNoNumericProgress() {
        val result = AutoPreheatPresentation.describe(facts().copy(current = null))
        assertEquals(AutoPreheatPresentation.Phase.TEMPERATURE_MISSING, result.phase)
        assertNull(result.progress)
    }
    @Test fun changedTargetDiscardsOldProgress() {
        val result = AutoPreheatPresentation.describe(facts().copy(targetC = 95))
        assertEquals(AutoPreheatPresentation.Phase.TARGET_CHANGED, result.phase)
        assertNull(result.progress)
    }
    @Test fun transportStatesNeverClaimReadyEvenAtTarget() {
        for ((state, expected) in listOf(BrewPreparation.State.WRITING to AutoPreheatPresentation.Phase.SENDING,
            BrewPreparation.State.UNKNOWN to AutoPreheatPresentation.Phase.UNKNOWN,
            BrewPreparation.State.FAILED to AutoPreheatPresentation.Phase.FAILED,
            BrewPreparation.State.CANCEL_WRITTEN to AutoPreheatPresentation.Phase.CANCEL_WRITTEN)) {
            val result = AutoPreheatPresentation.describe(facts().copy(state = state, current = 9300))
            assertEquals(expected, result.phase)
            assertNull(result.progress)
        }
        assertEquals(AutoPreheatPresentation.Phase.DISCONNECTED,
            AutoPreheatPresentation.describe(facts().copy(connected = false)).phase)
    }
}
