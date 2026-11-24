package io.openhoyi.mobile

import io.openhoyi.session.CupResetTracker
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepScheduleWriteTracker

/** Ordinary transport progress stays in diagnostics. Settings shows the operation's outcome. */
internal object SettingsOperationFeedback {
    fun badge(state: String?): Int? = when (state) {
        "UNKNOWN" -> R.string.task_shell_machine_unknown
        "FAILED" -> R.string.home_setting_failed
        else -> null
    }

    fun setting(state: SettingsWriteTracker.State?): Int? = when (state) {
        SettingsWriteTracker.State.CONFIRMED -> R.string.machine_settings_setting_confirmed
        SettingsWriteTracker.State.FAILED -> R.string.machine_settings_setting_failed
        SettingsWriteTracker.State.UNKNOWN -> R.string.machine_settings_setting_unknown
        SettingsWriteTracker.State.RECONCILED -> R.string.machine_settings_setting_reconciled
        else -> null
    }

    fun schedule(state: SleepScheduleWriteTracker.State?): Int? = when (state) {
        SleepScheduleWriteTracker.State.CONFIRMED -> R.string.machine_settings_schedule_confirmed
        SleepScheduleWriteTracker.State.FAILED -> R.string.machine_settings_schedule_failed
        SleepScheduleWriteTracker.State.UNKNOWN -> R.string.machine_settings_schedule_unknown
        SleepScheduleWriteTracker.State.RECONCILED -> R.string.machine_settings_schedule_reconciled
        else -> null
    }

    fun cups(state: CupResetTracker.State?): Int? = when (state) {
        CupResetTracker.State.CONFIRMED -> R.string.machine_settings_cup_confirmed
        CupResetTracker.State.FAILED -> R.string.machine_settings_cup_failed
        CupResetTracker.State.UNKNOWN -> R.string.machine_settings_cup_unknown
        CupResetTracker.State.RECONCILED -> R.string.machine_settings_cup_reconciled
        else -> null
    }

    fun lever(state: SettingsWriteTracker.State): Int? = when (state) {
        SettingsWriteTracker.State.FAILED -> R.string.home_setting_failed
        SettingsWriteTracker.State.UNKNOWN -> R.string.home_setting_unknown
        else -> null
    }

    fun target(feedback: BoilerTargetSteps.Feedback?): Int? = when (feedback) {
        BoilerTargetSteps.Feedback.FAILED -> R.string.machine_settings_setting_failed
        BoilerTargetSteps.Feedback.UNKNOWN -> R.string.machine_settings_setting_unknown
        BoilerTargetSteps.Feedback.UNKNOWN_WITH_UNSENT -> R.string.settings_target_unknown_unsent
        BoilerTargetSteps.Feedback.FAILED_WITH_UNSENT -> R.string.settings_target_failed_unsent
        BoilerTargetSteps.Feedback.NOT_SENT -> R.string.settings_target_not_sent
        else -> null
    }
}
