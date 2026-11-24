package io.openhoyi.mobile

import io.openhoyi.session.CupResetTracker
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepScheduleWriteTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsOperationFeedbackTest {
    @Test fun ordinaryWritesAndBackgroundReadbackLeaveSettingsQuiet() {
        for (state in listOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK)) {
            assertNull(SettingsOperationFeedback.setting(state))
            assertNull(SettingsOperationFeedback.badge(state.name))
            assertNull(SettingsOperationFeedback.lever(state))
        }
        for (state in listOf(SleepScheduleWriteTracker.State.WRITING, SleepScheduleWriteTracker.State.WAITING_READBACK)) {
            assertNull(SettingsOperationFeedback.schedule(state))
            assertNull(SettingsOperationFeedback.badge(state.name))
        }
        for (state in listOf(CupResetTracker.State.WRITING, CupResetTracker.State.WAITING_ZERO)) {
            assertNull(SettingsOperationFeedback.cups(state))
            assertNull(SettingsOperationFeedback.badge(state.name))
        }
        assertNull(SettingsOperationFeedback.target(BoilerTargetSteps.Feedback.QUEUED))
        assertNull(SettingsOperationFeedback.target(BoilerTargetSteps.Feedback.CONFIRMING))
    }

    @Test fun confirmationFailureAndUnresolvedFeedbackStayAvailable() {
        assertEquals(R.string.machine_settings_setting_confirmed,
            SettingsOperationFeedback.setting(SettingsWriteTracker.State.CONFIRMED))
        assertEquals(R.string.machine_settings_setting_failed,
            SettingsOperationFeedback.setting(SettingsWriteTracker.State.FAILED))
        assertEquals(R.string.machine_settings_schedule_unknown,
            SettingsOperationFeedback.schedule(SleepScheduleWriteTracker.State.UNKNOWN))
        assertEquals(R.string.machine_settings_cup_confirmed, SettingsOperationFeedback.cups(CupResetTracker.State.CONFIRMED))
        assertEquals(R.string.settings_target_unknown_unsent,
            SettingsOperationFeedback.target(BoilerTargetSteps.Feedback.UNKNOWN_WITH_UNSENT))
        assertEquals(R.string.home_setting_unknown, SettingsOperationFeedback.lever(SettingsWriteTracker.State.UNKNOWN))
        assertEquals(R.string.task_shell_machine_unknown, SettingsOperationFeedback.badge("UNKNOWN"))
    }
}
