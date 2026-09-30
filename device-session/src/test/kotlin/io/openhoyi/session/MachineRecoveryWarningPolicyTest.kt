package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class MachineRecoveryWarningPolicyTest {
    @Test fun activePreheatDefersRecoveryWarningWithoutClearingItsKind() {
        for (state in listOf(BrewPreparation.State.WRITING, BrewPreparation.State.WAITING_TEMP, BrewPreparation.State.READY))
            assertNull(MachineRecoveryWarningPolicy.warningKind(MachineWriteRecoveryState.Kind.BREW_WAIT,state))
        for (state in listOf(BrewPreparation.State.IDLE, BrewPreparation.State.CANCELLING,
            BrewPreparation.State.CANCEL_WRITTEN, BrewPreparation.State.FAILED, BrewPreparation.State.UNKNOWN))
            assertEquals(MachineWriteRecoveryState.Kind.BREW_WAIT,
                MachineRecoveryWarningPolicy.warningKind(MachineWriteRecoveryState.Kind.BREW_WAIT,state))
    }

    @Test fun otherPendingWritesAndUnknownRecordsAlwaysRemainVisible() {
        MachineWriteRecoveryState.Kind.entries.filter { it != MachineWriteRecoveryState.Kind.BREW_WAIT }.forEach { kind ->
            BrewPreparation.State.entries.forEach { phase ->
                assertEquals(kind,MachineRecoveryWarningPolicy.warningKind(kind,phase))
            }
        }
    }

    @Test fun noPendingRecordDoesNotInventAMachineWarning() {
        BrewPreparation.State.entries.forEach { phase -> assertNull(MachineRecoveryWarningPolicy.warningKind(null,phase)) }
    }
}
