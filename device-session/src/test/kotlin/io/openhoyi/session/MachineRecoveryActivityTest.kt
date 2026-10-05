package io.openhoyi.session

import org.junit.Assert.assertEquals
import org.junit.Test

class MachineRecoveryActivityTest {
    private data class Case(val kind: MachineWriteRecoveryState.Kind, val label: String,
        val busy: Boolean, val actualBusy: Boolean,
        val cup: CupResetTracker.State = CupResetTracker.State.WRITING,
        val setting: SettingsWriteTracker.State = SettingsWriteTracker.State.WRITING,
        val schedule: SleepScheduleWriteTracker.State = SleepScheduleWriteTracker.State.WRITING,
        val sleep: SleepNowTracker.State = SleepNowTracker.State.WRITING,
        val brew: BrewPreparation.State = BrewPreparation.State.WRITING)
    private fun cases(): List<Case> =
        CupResetTracker.State.entries.map { Case(MachineWriteRecoveryState.Kind.CUP_RESET, it.name,
            it.name in listOf("WRITING", "WAITING_ZERO"), MachineRecoveryActivity.isBusy(it), cup = it) } +
        SettingsWriteTracker.State.entries.map { Case(MachineWriteRecoveryState.Kind.SETTING, it.name,
            it.name in listOf("WRITING", "WAITING_READBACK"), MachineRecoveryActivity.isBusy(it), setting = it) } +
        SleepScheduleWriteTracker.State.entries.map { Case(MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE, it.name,
            it.name in listOf("WRITING", "WAITING_READBACK"), MachineRecoveryActivity.isBusy(it), schedule = it) } +
        SleepNowTracker.State.entries.map { Case(MachineWriteRecoveryState.Kind.SLEEP_NOW, it.name,
            it.name in listOf("WRITING", "WAITING_ASLEEP"), MachineRecoveryActivity.isBusy(it), sleep = it) } +
        BrewPreparation.State.entries.map { Case(MachineWriteRecoveryState.Kind.BREW_WAIT, it.name,
            it.name !in listOf("IDLE", "FAILED", "UNKNOWN", "CANCEL_WRITTEN"), MachineRecoveryActivity.isBusy(it), brew = it) }

    @Test fun everyTypedStateKeepsTheExistingActivityRule() {
        for (c in cases()) assertEquals("${c.kind}/${c.label}", c.busy, c.actualBusy)
    }
    @Test fun ownTransactionAndBrewShotPriorityControlAvailability() {
        for (c in cases()) for (pending in listOf(false, true)) for (active in listOf(false, true)) {
            // Unrelated trackers deliberately remain WRITING: availability is advisory,
            // not a replacement for the separate actual clear evidence or control gates.
            val expected = !c.busy && (c.kind != MachineWriteRecoveryState.Kind.BREW_WAIT || (!pending && !active))
            assertEquals("${c.kind}/${c.label}/pending=$pending/active=$active", expected,
                MachineRecoveryActivity.available(c.kind, c.cup, c.setting, c.schedule, c.sleep, c.brew, pending, active))
        }
    }
    @Test fun emptyOrUnknownRecordNeverOffersAcknowledgement() {
        for (kind in listOf(null, MachineWriteRecoveryState.Kind.UNKNOWN))
            for (pending in listOf(false, true)) for (active in listOf(false, true))
                assertEquals(false, MachineRecoveryActivity.available(kind, CupResetTracker.State.IDLE,
                    SettingsWriteTracker.State.IDLE, SleepScheduleWriteTracker.State.IDLE,
                    SleepNowTracker.State.IDLE, BrewPreparation.State.IDLE, pending, active))
    }
}
