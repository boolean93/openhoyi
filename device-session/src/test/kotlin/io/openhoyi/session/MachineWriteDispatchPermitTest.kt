package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MachineWriteDispatchPermitTest(private val kind:MachineWriteRecoveryState.Kind) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun kinds()=listOf(
            MachineWriteRecoveryState.Kind.SETTING,MachineWriteRecoveryState.Kind.CUP_RESET,
            MachineWriteRecoveryState.Kind.SLEEP_NOW,MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE).map { arrayOf(it) }
    }
    private val address="AA:BB:CC:DD:EE:01"
    private inner class F {
        val settings=SettingsWriteTracker();val cups=CupResetTracker()
        val sleep=SleepNowTracker();val schedule=SleepScheduleWriteTracker()
        val plan=WeeklySleepSchedule(List(7) { WeeklySleepDay(false,SleepDay(22,0,7,0)) })
        val request=when(kind) {
            MachineWriteRecoveryState.Kind.SETTING->MachineWriteDispatchPermit.Request.Setting(settings)
            MachineWriteRecoveryState.Kind.CUP_RESET->MachineWriteDispatchPermit.Request.CupReset(cups)
            MachineWriteRecoveryState.Kind.SLEEP_NOW->MachineWriteDispatchPermit.Request.Sleep(sleep)
            else->MachineWriteDispatchPermit.Request.Schedule(schedule)
        }
        fun begin():Long=requireNotNull(when(kind) {
            MachineWriteRecoveryState.Kind.SETTING->settings.begin(MachineSettingChange.BrewTemperature(93))
            MachineWriteRecoveryState.Kind.CUP_RESET->cups.begin(25)
            MachineWriteRecoveryState.Kind.SLEEP_NOW->sleep.begin()
            else->schedule.begin(plan,1,1)
        })
        val token=begin()
        val recovery=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read()=MachineWriteRecoveryState.Record(null,null)
            override fun write(record:MachineWriteRecoveryState.Record)=true
        }).also { check(it.arm(kind,address)) }
        fun context()=MachineWriteDispatchPermit.Context(recovery,address,address,false,false,false,false)
        fun allow(context:MachineWriteDispatchPermit.Context=context(),token:Long=this.token)=
            MachineWriteDispatchPermit.allows(token,request,context)
        fun result(value:OperationResult) {
            when(kind) {
                MachineWriteRecoveryState.Kind.SETTING->settings.written(token,value,1)
                MachineWriteRecoveryState.Kind.CUP_RESET->cups.written(token,value,1,1)
                MachineWriteRecoveryState.Kind.SLEEP_NOW->sleep.written(token,value,1)
                else->schedule.written(token,value,1,1,null,null)
            }
        }
    }
    @Test fun originalWritingTokenAndDurableIdentityPermit() {
        val f=F();assertTrue(f.allow());assertTrue(f.allow(f.context().copy(currentAddress=address.lowercase(),originalAddress=address.lowercase())))
    }
    @Test fun eachNewProductBlockRejectsWithoutMutatingRecord() {
        val f=F();val c=f.context()
        for(block in listOf(c.copy(manualShotActive=true),c.copy(shotRecoveryPending=true),c.copy(shotActive=true),c.copy(preparationActive=true))) {
            assertFalse(f.allow(block));assertEquals(kind,f.recovery.kind);assertEquals(address,f.recovery.address)
        }
        assertTrue(f.allow())
    }
    @Test fun missingOrChangedCurrentIdentityRejects() {
        val f=F();for(a in listOf(null,"AA:BB:CC:DD:EE:02","invalid"))assertFalse(f.allow(f.context().copy(currentAddress=a)))
    }
    @Test fun missingOrChangedCapturedIdentityRejects() {
        val f=F();for(a in listOf(null,"AA:BB:CC:DD:EE:02","invalid"))assertFalse(f.allow(f.context().copy(originalAddress=a)))
    }
    @Test fun clearedIntentRejects() { val f=F();check(f.recovery.clear());assertFalse(f.allow()) }
    @Test fun differentDurableKindRejects() {
        val f=F();check(f.recovery.clear());check(f.recovery.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,address));assertFalse(f.allow())
    }
    @Test fun changedDurableAddressRejects() {
        val f=F();check(f.recovery.clear());check(f.recovery.arm(kind,"AA:BB:CC:DD:EE:02"));assertFalse(f.allow())
    }
    @Test fun permissiveLegacyRecoveryIdentityCannotAuthorize() {
        val f=F();val legacy=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read()=MachineWriteRecoveryState.Record(kind,null)
            override fun write(record:MachineWriteRecoveryState.Record)=true
        });assertTrue(legacy.matchesDevice(address));assertFalse(f.allow(f.context().copy(recovery=legacy)))
    }
    @Test fun wrongTokenRejects() { val f=F();assertFalse(f.allow(token=f.token-1));assertFalse(f.allow(token=f.token+1)) }
    @Test fun transportWaitingCannotAuthorizeAnotherWrite() { val f=F();f.result(OperationResult.Success());assertFalse(f.allow()) }
    @Test fun unknownOutcomeCannotAuthorize() { val f=F();f.result(OperationResult.Unknown("uncertain"));assertFalse(f.allow()) }
    @Test fun failedAndReplacedTokenCannotAuthorize() {
        val f=F();f.result(OperationResult.Failed("unsent"));assertFalse(f.allow())
        val newer=f.begin();assertFalse(f.allow());assertTrue(f.allow(token=newer))
    }
}
