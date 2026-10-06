package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MachineWriteDisconnectionTest(private val kind: MachineWriteRecoveryState.Kind) {
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
        val first=(HoyiCodec.decode(hex("8340FE0A00071E0A00071E0A00071E0A00071E3D")) as DecodeResult.Valid).value as SleepPart
        val second=(HoyiCodec.decode(hex("83800A00071E0A00071E0A00071E10")) as DecodeResult.Valid).value as SleepPart
        val plan = requireNotNull(WeeklySleepSchedule.fromReadback(first,second))
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
        val disconnection = MachineWriteDisconnection(setting,cup,schedule,sleep,brew)
        val samples = MachineWriteDisconnection.Samples(11,13,17,19,23,29)
        fun disconnect() = disconnection.apply(samples)
        fun observeFresh(): Boolean = when(kind) {
            MachineWriteRecoveryState.Kind.SETTING -> setting.observe(12,settings)
            MachineWriteRecoveryState.Kind.CUP_RESET -> { cup.observeSettings(14,0);cup.observeIdle(18,0) }
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> schedule.observe(20,24,first,second)
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> sleep.observe(30,1)
            MachineWriteRecoveryState.Kind.BREW_WAIT -> brew.observe(30,9300)
            else -> error("unsupported")
        }
        val settings=(HoyiCodec.decode(hex("830113FD5D007D0F350019006F")) as DecodeResult.Valid).value as Settings
        fun hex(s:String)=s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        fun preservedIntent() {
            assertTrue(recovery.pending); assertEquals(kind,recovery.kind)
            assertEquals(MachineWriteRecoveryState.Record(kind,"AA:BB:CC:DD:EE:01"),disk.record)
            assertEquals(1,disk.writes)
        }
    }
    @Test fun writingBecomesUnknownWithoutClearingIntent() {
        val f=Fixture();f.disconnect();assertEquals("UNKNOWN",f.state());f.preservedIntent()
        assertEquals(listOf("IDLE","IDLE","IDLE","IDLE"), listOf(f.setting.state.name,f.cup.state.name,
            f.schedule.state.name,f.sleep.state.name,f.brew.state.name).filterIndexed { index,_ ->
                index != when(kind) { MachineWriteRecoveryState.Kind.SETTING -> 0
                    MachineWriteRecoveryState.Kind.CUP_RESET -> 1; MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> 2
                    MachineWriteRecoveryState.Kind.SLEEP_NOW -> 3; else -> 4 } })
    }
    @Test fun successfulTransportStillBecomesUnknownOnDisconnect() {
        val f=Fixture();f.apply(OperationResult.Success());f.disconnect()
        assertEquals("UNKNOWN",f.state());f.preservedIntent()
    }
    @Test fun lateTransportCannotTurnDisconnectIntoSuccess() {
        val f=Fixture();f.disconnect()
        for(result in listOf(OperationResult.Success(),OperationResult.Failed("late"),OperationResult.Unknown("late"))) {
            assertEquals(MachineWriteResult.Outcome.IGNORED,f.apply(result));assertEquals("UNKNOWN",f.state())
        }
        f.disconnect();assertEquals("UNKNOWN",f.state());f.preservedIntent()
    }
    @Test fun definiteFailurePreservedExceptActivePreheatBecomesUnknown() {
        val f=Fixture();f.apply(OperationResult.Failed("not submitted"));f.disconnect()
        assertEquals(if(kind==MachineWriteRecoveryState.Kind.BREW_WAIT) "UNKNOWN" else "FAILED",f.state());f.preservedIntent()
    }
    @Test fun completedOrdinaryWritePreservedButPreheatReadyInvalidated() {
        val f=Fixture();f.apply(OperationResult.Success());assertTrue(f.observeFresh());f.disconnect()
        assertEquals(if(kind==MachineWriteRecoveryState.Kind.BREW_WAIT) "UNKNOWN" else "CONFIRMED",f.state());f.preservedIntent()
    }
    @Test fun disconnectUsesItsOwnLatestReadbackBaselines() {
        val f=Fixture();f.apply(OperationResult.Success());f.disconnect()
        when(kind) {
            MachineWriteRecoveryState.Kind.SETTING -> { assertFalse(f.setting.observe(11,f.settings));assertTrue(f.setting.observe(12,f.settings)) }
            MachineWriteRecoveryState.Kind.CUP_RESET -> { assertFalse(f.cup.observeSettings(13,0));assertFalse(f.cup.observeIdle(17,0));assertFalse(f.cup.observeSettings(14,0));assertTrue(f.cup.observeIdle(18,0)) }
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> { assertFalse(f.schedule.observe(19,23,f.first,f.second));assertTrue(f.schedule.observe(20,24,f.first,f.second));assertEquals("RECONCILED",f.state()) }
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> { assertFalse(f.sleep.observe(29,1));assertTrue(f.sleep.observe(30,1)) }
            MachineWriteRecoveryState.Kind.BREW_WAIT -> assertFalse(f.brew.observe(30,9300))
            else -> error("unsupported")
        }
        f.preservedIntent()
    }
}

class PreheatWriteDisconnectionTest {
    @Test fun cancelInFlightAndWrittenBothRemainUnknownAfterDisconnect() {
        for (completedCancel in listOf(false,true)) {
            val brew=BrewPreparation()
            val start=requireNotNull(brew.begin("fixture",93))
            assertTrue(brew.written(start,OperationResult.Success(),7))
            assertTrue(brew.observe(8,9300))
            val cancel=requireNotNull(brew.beginCancel())
            if(completedCancel)assertTrue(brew.cancelled(cancel,OperationResult.Success()))
            val coordinator=MachineWriteDisconnection(SettingsWriteTracker(),CupResetTracker(),
                SleepScheduleWriteTracker(),SleepNowTracker(),brew)
            coordinator.apply(MachineWriteDisconnection.Samples(11,13,17,19,23,29))
            assertEquals(BrewPreparation.State.UNKNOWN,brew.state)
            assertFalse(brew.cancelled(cancel,OperationResult.Success()))
            assertFalse(brew.permitsWrite(cancel,0))
            assertFalse(brew.matches("fixture",93))
        }
    }
    @Test fun idlePreheatRemainsIdleAndNewExplicitPreparationIsAllowed() {
        val brew=BrewPreparation()
        val coordinator=MachineWriteDisconnection(SettingsWriteTracker(),CupResetTracker(),
            SleepScheduleWriteTracker(),SleepNowTracker(),brew)
        coordinator.apply(MachineWriteDisconnection.Samples(11,13,17,19,23,29))
        assertEquals(BrewPreparation.State.IDLE,brew.state)
        val next=requireNotNull(brew.begin("next",91))
        assertTrue(brew.permitsWrite(next,91))
    }
}
