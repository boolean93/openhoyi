package io.openhoyi.session

/** Selects the existing recovery warning without storing, clearing, confirming or replaying a write.
 * Normal preheat progress is displayed elsewhere; hiding its advisory never clears the durable kind.
 */
object MachineRecoveryWarningPolicy {
    fun warningKind(kind: MachineWriteRecoveryState.Kind?, preparation: BrewPreparation.State): MachineWriteRecoveryState.Kind? =
        when (kind) {
            MachineWriteRecoveryState.Kind.BREW_WAIT -> if (preparation in setOf(
                BrewPreparation.State.WRITING, BrewPreparation.State.WAITING_TEMP,
                BrewPreparation.State.READY)) null else kind
            else -> kind
        }
}
