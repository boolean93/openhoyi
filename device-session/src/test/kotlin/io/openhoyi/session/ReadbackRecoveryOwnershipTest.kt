package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ReadbackRecoveryOwnershipTest(private val kind:MachineWriteRecoveryState.Kind) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun kinds()=listOf(MachineWriteRecoveryState.Kind.SETTING,
            MachineWriteRecoveryState.Kind.CUP_RESET,MachineWriteRecoveryState.Kind.SLEEP_NOW,MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE).map { arrayOf(it) }
    }
    private class F(val kind:MachineWriteRecoveryState.Kind) {
        val address="AA:BB:CC:DD:EE:01"
        var disk=MachineWriteRecoveryState.Record(null,null);var clears=0;var fail=false
        val recovery=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read()=disk
            override fun write(record:MachineWriteRecoveryState.Record):Boolean {
                if(!record.pending){clears++;if(fail)return false};disk=record;return true
            }
        })
        val settings=SettingsWriteTracker();val cup=CupResetTracker();val sleep=SleepNowTracker();val schedule=SleepScheduleWriteTracker()
        fun decode(hex:String)=(HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value
        val first=decode("8340FE0A00071E0A00071E0A00071E0A00071E3D") as SleepPart
        val second=decode("83800A00071E0A00071E0A00071E10") as SleepPart
        val frame=decode("830113FD5C007D0F350019006E") as Settings
        val events=mutableListOf<String>()
        val owner:MachineWriteRecoveryState.Ownership
        init {
            check(recovery.arm(kind,address));owner=requireNotNull(recovery.captureOwnership())
            when(kind) {
                MachineWriteRecoveryState.Kind.SETTING->check(settings.written(requireNotNull(settings.begin(MachineSettingChange.BrewTemperature(93))),OperationResult.Success(),1))
                MachineWriteRecoveryState.Kind.CUP_RESET->check(cup.written(requireNotNull(cup.begin(2)),OperationResult.Success(),1,1))
                MachineWriteRecoveryState.Kind.SLEEP_NOW->check(sleep.written(requireNotNull(sleep.begin()),OperationResult.Success(),1))
                else->check(schedule.written(requireNotNull(schedule.begin(requireNotNull(WeeklySleepSchedule.fromReadback(first,second)),1,1)),OperationResult.Success(),1,1,first,second))
            }
        }
        fun read(owner:MachineWriteRecoveryState.Ownership?=this.owner,serial:Long=2) {
            val clear={recovery.clear(owner)}
            when(kind) {
                MachineWriteRecoveryState.Kind.SETTING->OrdinaryWriteReadback.settings(settings,recovery,serial,frame.copy(brewTemperatureC=93),clear){events+=it.name}
                MachineWriteRecoveryState.Kind.SLEEP_NOW->OrdinaryWriteReadback.sleep(sleep,recovery,serial,1,clear){events+=it.name}
                MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE->OrdinaryWriteReadback.schedule(schedule,recovery,true,serial,serial,first,second,clear){events+=it.name}
                else->{events+=CupResetReadback.observe(cup,recovery,CupResetReadback.Sample.Settings(serial,0),clear).map { it.name }
                    events+=CupResetReadback.observe(cup,recovery,CupResetReadback.Sample.Idle(serial,0),clear).map { it.name }}
            }
        }
    }
    @Test fun originalReadbackClearsOwnRecord() {
        val f=F(kind);f.read();assertFalse(f.recovery.pending);assertEquals(1,f.clears);assertEquals(listOf("CONFIRMED"),f.events)
    }
    @Test fun sameKindAddressRearmCannotBeClearedByOldReadback() {
        val f=F(kind);assertTrue(f.recovery.clear());assertTrue(f.recovery.arm(kind,f.address));val clears=f.clears
        f.read();assertTrue(f.recovery.pending);assertEquals(clears,f.clears);assertEquals(listOf("CONFIRMED","CLEAR_FAILED"),f.events)
        f.read(serial=3);assertTrue(f.recovery.pending);assertEquals(clears,f.clears)
    }
    @Test fun missingOwnerCannotBorrowCurrentRecord() {
        val f=F(kind);f.read(null);assertTrue(f.recovery.pending);assertEquals(0,f.clears);assertEquals(listOf("CONFIRMED","CLEAR_FAILED"),f.events)
    }
    @Test fun failedOwnClearRetainsLocalRetryAndFeedbackOrder() {
        val f=F(kind);f.fail=true;f.read();assertTrue(f.recovery.pending);assertTrue(f.recovery.owns(f.owner))
        assertEquals(listOf("CONFIRMED","CLEAR_FAILED"),f.events)
        f.fail=false;f.events.clear();f.read(serial=3);assertFalse(f.recovery.pending);assertTrue(f.events.isEmpty())
    }
}
