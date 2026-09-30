package io.openhoyi.mobile

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.MachineRecoveryWarningPolicy
import io.openhoyi.session.MachineWriteRecoveryState

/** Translates a shared warning kind; it never acknowledges or clears a machine write. */
object MachineRecoveryText {
    fun resource(kind: MachineWriteRecoveryState.Kind?, preparation: BrewPreparation.State): Int? =
        when (MachineRecoveryWarningPolicy.warningKind(kind, preparation)) {
            MachineWriteRecoveryState.Kind.CUP_RESET -> R.string.machine_recovery_cup_reset
            MachineWriteRecoveryState.Kind.SETTING -> R.string.machine_recovery_setting
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> R.string.machine_recovery_sleep_schedule
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> R.string.machine_recovery_sleep_now
            MachineWriteRecoveryState.Kind.BREW_WAIT -> R.string.machine_recovery_brew_wait
            MachineWriteRecoveryState.Kind.UNKNOWN -> R.string.machine_recovery_unknown
            null -> null
        }
}
