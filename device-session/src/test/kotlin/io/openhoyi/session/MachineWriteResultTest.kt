package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MachineWriteResultTest(private val kind: MachineWriteRecoveryState.Kind) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun kinds() = MachineWriteRecoveryState.Kind.entries.filter { it != MachineWriteRecoveryState.Kind.UNKNOWN }.map { arrayOf(it) }
    }
    private class Disk : MachineWriteRecoveryState.Storage {
        var record = MachineWriteRecoveryState.Record(null,null)
        var writes = 0
        override fun read() = record
        override fun write(record: MachineWriteRecoveryState.Record): Boolean { writes++; this.record=record; return true }
    }
    private inner class Fixture {
        val setting = SettingsWriteTracker(); val cup = CupResetTracker()
        val schedule = SleepScheduleWriteTracker(); val sleep = SleepNowTracker(); val brew = BrewPreparation()
        val plan = WeeklySleepSchedule(List(7) { WeeklySleepDay(false,SleepDay(22,0,7,0)) })
        val disk = Disk(); val recovery = MachineWriteRecoveryState(disk)
        val token = requireNotNull(when (kind) {
            MachineWriteRecoveryState.Kind.SETTING -> setting.begin(MachineSettingChange.BrewTemperature(93))
            MachineWriteRecoveryState.Kind.CUP_RESET -> cup.begin(25)
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> schedule.begin(plan,1,2)
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> sleep.begin()
            MachineWriteRecoveryState.Kind.BREW_WAIT -> brew.begin("fixture",93)
            else -> error("unsupported")
        })
        val request = when (kind) {
            MachineWriteRecoveryState.Kind.SETTING -> MachineWriteResult.Request.Setting(setting,7)
            MachineWriteRecoveryState.Kind.CUP_RESET -> MachineWriteResult.Request.CupReset(cup,7,8)
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> MachineWriteResult.Request.Schedule(schedule,7,8,null,null)
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> MachineWriteResult.Request.Sleep(sleep,7)
            MachineWriteRecoveryState.Kind.BREW_WAIT -> MachineWriteResult.Request.BrewWait(brew,7)
            else -> error("unsupported")
        }
        init { assertTrue(recovery.arm(kind,"AA:BB:CC:DD:EE:01")) }
        fun state() = when (kind) {
            MachineWriteRecoveryState.Kind.SETTING -> setting.state.name
            MachineWriteRecoveryState.Kind.CUP_RESET -> cup.state.name
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> schedule.state.name
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> sleep.state.name
            MachineWriteRecoveryState.Kind.BREW_WAIT -> brew.state.name
            else -> error("unsupported")
        }
        fun apply(result: OperationResult) = MachineWriteResult.apply(token,result,request)
        fun preservedIntent() {
            assertTrue(recovery.pending); assertEquals(kind,recovery.kind)
            assertEquals(MachineWriteRecoveryState.Record(kind,"AA:BB:CC:DD:EE:01"),disk.record)
            assertEquals(1,disk.writes)
        }
    }
    @Test fun successWaitsForReadbackAndRetainsIntent() {
        val f=Fixture(); assertEquals(MachineWriteResult.Outcome.WAITING,f.apply(OperationResult.Success()))
        assertTrue(f.state().startsWith("WAITING")); f.preservedIntent()
    }
    @Test fun definiteFailureAndCancellationDoNotAutomaticallyClearIntent() {
        for (result in listOf(OperationResult.Failed("not submitted"),OperationResult.Cancelled("not queued"))) {
            val f=Fixture(); assertEquals(MachineWriteResult.Outcome.FAILED,f.apply(result))
            assertEquals("FAILED",f.state()); f.preservedIntent()
        }
    }
    @Test fun unknownRemainsUnknownAndRetainsIntent() {
        val f=Fixture(); assertEquals(MachineWriteResult.Outcome.UNKNOWN,f.apply(OperationResult.Unknown("submission uncertain")))
        assertEquals("UNKNOWN",f.state()); f.preservedIntent()
    }
    @Test fun staleTokenCannotChangeStateOrDurableIntent() {
        val f=Fixture()
        for (result in listOf(OperationResult.Success(),OperationResult.Failed("old"),OperationResult.Unknown("old"))) {
            assertEquals(MachineWriteResult.Outcome.IGNORED,MachineWriteResult.apply(f.token+1,result,f.request))
            assertEquals("WRITING",f.state()); f.preservedIntent()
        }
    }
    @Test fun repeatedCallbacksCannotChangeFirstOutcome() {
        for (first in listOf(OperationResult.Success(),OperationResult.Failed("not sent"),OperationResult.Unknown("uncertain"))) {
            val f=Fixture(); assertNotEquals(MachineWriteResult.Outcome.IGNORED,f.apply(first))
            val state=f.state()
            for (later in listOf(OperationResult.Success(),OperationResult.Failed("late"),OperationResult.Unknown("late"))) {
                assertEquals(MachineWriteResult.Outcome.IGNORED,f.apply(later)); assertEquals(state,f.state()); f.preservedIntent()
            }
        }
    }
    @Test fun unchangedReadbackSerialsCannotConfirmSuccess() {
        val f=Fixture(); assertEquals(MachineWriteResult.Outcome.WAITING,f.apply(OperationResult.Success()))
        when (kind) {
            MachineWriteRecoveryState.Kind.SETTING -> {
                val settings=(HoyiCodec.decode("830113FD5D007D0F350019006F".chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value as Settings
                assertFalse(f.setting.observe(7,settings)); assertTrue(f.setting.observe(8,settings))
            }
            MachineWriteRecoveryState.Kind.CUP_RESET -> {
                assertFalse(f.cup.observeSettings(7,0)); assertFalse(f.cup.observeIdle(8,0))
                assertFalse(f.cup.observeSettings(8,0)); assertTrue(f.cup.observeIdle(9,0))
            }
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> assertFalse(f.schedule.observe(7,8,null,null))
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> { assertFalse(f.sleep.observe(7,1)); assertTrue(f.sleep.observe(8,1)) }
            MachineWriteRecoveryState.Kind.BREW_WAIT -> { assertFalse(f.brew.observe(7,9300)); assertTrue(f.brew.observe(8,9300)) }
            else -> error("unsupported")
        }
        f.preservedIntent()
    }
}
