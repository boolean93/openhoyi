package io.openhoyi.mobile

import io.openhoyi.session.*
import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdateSafetyTest {
    private fun blocked(shot: ExtractionState = ExtractionState.IDLE, manual: Boolean = false,
        preflight: Boolean = false, brew: Boolean = false, tare: StandaloneTare.State = StandaloneTare.State.IDLE,
        shotRecovery: Boolean = false, machineRecovery: Boolean = false,
        setting: SettingsWriteTracker.State = SettingsWriteTracker.State.IDLE,
        cups: Boolean = false, schedule: Boolean = false, sleep: SleepNowTracker.State = SleepNowTracker.State.IDLE,
        leverFlight: Boolean = false, leverBlocked: Boolean = false) = releaseUpdateInterruptionBlocked(
        shot, manual, preflight, brew, tare, shotRecovery, machineRecovery, setting, cups, schedule, sleep, leverFlight, leverBlocked)
    @Test fun runningAndUnknownExtractionNeverInterruptAcrossAnyPage() {
        assertFalse(blocked())
        for (shot in ExtractionState.entries) {
            if (ShotGate.active(shot)) assertTrue("$shot", blocked(shot = shot))
        }
        assertTrue(blocked(manual = true)); assertTrue(blocked(preflight = true))
        // READY awaiting user start is still active preparation.
        val preparation = BrewPreparation()
        val token = preparation.begin("fixture", 93)!!
        preparation.written(token, OperationResult.Success(), 1)
        assertTrue(preparation.active); assertTrue(blocked(brew = preparation.active))
        assertTrue(preparation.observe(2, 9300))
        assertEquals(BrewPreparation.State.READY, preparation.state)
        assertTrue(blocked(brew = preparation.active))
        preparation.consumed(); assertFalse(blocked(brew = preparation.active))
    }
    @Test fun tareAndEveryCriticalWriteOrUnresolvedRecoveryBlocksAndSettledStateReleases() {
        for (tare in listOf(StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO, StandaloneTare.State.UNKNOWN))
            assertTrue(blocked(tare = tare))
        for (state in listOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK, SettingsWriteTracker.State.UNKNOWN))
            assertTrue(blocked(setting = state))
        for (state in listOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP, SleepNowTracker.State.UNKNOWN))
            assertTrue(blocked(sleep = state))
        assertTrue(blocked(cups = true)); assertTrue(blocked(schedule = true))
        assertTrue(blocked(shotRecovery = true)); assertTrue(blocked(machineRecovery = true))
        assertTrue(blocked(leverFlight = true)); assertTrue(blocked(leverBlocked = true))
        assertFalse(blocked(tare = StandaloneTare.State.CONFIRMED, setting = SettingsWriteTracker.State.CONFIRMED,
            sleep = SleepNowTracker.State.CONFIRMED, shot = ExtractionState.ENDED_OBSERVED))
    }
}
