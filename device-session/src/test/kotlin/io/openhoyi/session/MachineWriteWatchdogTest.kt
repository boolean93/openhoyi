package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MachineWriteWatchdogTest(private val kind: MachineWriteRecoveryState.Kind) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun kinds()=listOf(MachineWriteRecoveryState.Kind.SETTING,MachineWriteRecoveryState.Kind.CUP_RESET,
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE,MachineWriteRecoveryState.Kind.SLEEP_NOW).map { arrayOf(it) }
    }
    private inner class Fixture {
        val setting=SettingsWriteTracker(); val cup=CupResetTracker()
        val schedule=SleepScheduleWriteTracker(); val sleep=SleepNowTracker()
        fun hex(s:String)=s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val first=(HoyiCodec.decode(hex("8340FE0A00071E0A00071E0A00071E0A00071E3D")) as DecodeResult.Valid).value as SleepPart
        val second=(HoyiCodec.decode(hex("83800A00071E0A00071E0A00071E10")) as DecodeResult.Valid).value as SleepPart
        val plan=requireNotNull(WeeklySleepSchedule.fromReadback(first,second))
        val observedSettings=(HoyiCodec.decode(hex("830113FD5D007D0F350019006F")) as DecodeResult.Valid).value as Settings
        var serials=MachineWriteWatchdog.Serials(7,11,13,17,19,23)
        var now=100L
        var reads=0
        val tasks=mutableListOf<Pair<Long,()->Unit>>()
        val expired=mutableListOf<MachineWriteWatchdog.Serials>()
        var disk=MachineWriteRecoveryState.Record(null,null)
        var writes=0
        val recovery=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read()=disk
            override fun write(record:MachineWriteRecoveryState.Record):Boolean { writes++;disk=record;return true }
        })
        val watchdog=MachineWriteWatchdog({ delay, callback -> tasks+=now+delay to callback },{ reads++;serials })
        val request=when(kind) {
            MachineWriteRecoveryState.Kind.SETTING -> MachineWriteWatchdog.Request.Setting(setting)
            MachineWriteRecoveryState.Kind.CUP_RESET -> MachineWriteWatchdog.Request.CupReset(cup)
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> MachineWriteWatchdog.Request.Schedule(schedule)
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> MachineWriteWatchdog.Request.Sleep(sleep)
            else -> error("unsupported")
        }
        val delay=when(kind) {
            MachineWriteRecoveryState.Kind.SETTING -> 6000L
            MachineWriteRecoveryState.Kind.CUP_RESET -> 12000L
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> 8000L
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> 12000L
            else -> error("unsupported")
        }
        val token=begin()
        init { success(token); assertTrue(recovery.arm(kind,"AA:BB:CC:DD:EE:01")) }
        fun begin():Long=requireNotNull(when(kind) {
            MachineWriteRecoveryState.Kind.SETTING -> setting.begin(MachineSettingChange.BrewTemperature(93))
            MachineWriteRecoveryState.Kind.CUP_RESET -> cup.begin(25)
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> schedule.begin(plan,serials.sleepFirst,serials.sleepSecond)
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> sleep.begin()
            else -> error("unsupported")
        })
        fun success(token:Long) {
            val result=OperationResult.Success()
            assertTrue(when(kind) {
                MachineWriteRecoveryState.Kind.SETTING -> setting.written(token,result,serials.settings)
                MachineWriteRecoveryState.Kind.CUP_RESET -> cup.written(token,result,serials.cupSettings,serials.cupIdle)
                MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> schedule.written(token,result,serials.sleepFirst,serials.sleepSecond,first,second)
                MachineWriteRecoveryState.Kind.SLEEP_NOW -> sleep.written(token,result,serials.sleep)
                else -> error("unsupported")
            })
        }
        fun state()=when(kind) {
            MachineWriteRecoveryState.Kind.SETTING -> setting.state.name
            MachineWriteRecoveryState.Kind.CUP_RESET -> cup.state.name
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> schedule.state.name
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> sleep.state.name
            else -> error("unsupported")
        }
        fun observe(s:MachineWriteWatchdog.Serials):Boolean=when(kind) {
            MachineWriteRecoveryState.Kind.SETTING -> setting.observe(s.settings,observedSettings)
            MachineWriteRecoveryState.Kind.CUP_RESET -> { cup.observeSettings(s.cupSettings,0);cup.observeIdle(s.cupIdle,0) }
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> schedule.observe(s.sleepFirst,s.sleepSecond,first,second)
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> sleep.observe(s.sleep,1)
            else -> error("unsupported")
        }
        fun fresh()=serials.let { MachineWriteWatchdog.Serials(it.settings+1,it.cupSettings+1,it.cupIdle+1,it.sleepFirst+1,it.sleepSecond+1,it.sleep+1) }
        fun arm(token:Long=this.token)=watchdog.await(token,request) { expired+=it }
        fun advance(time:Long) {
            now=time
            val ready=tasks.filter { it.first<=now }.sortedBy { it.first }
            tasks.removeAll(ready.toSet());ready.forEach { it.second() }
        }
        fun retained() { assertEquals(1,writes);assertTrue(recovery.pending);assertEquals(kind,recovery.kind);assertEquals(kind,disk.kind) }
    }
    @Test fun exactOriginalDeadlineTransitionsOnceAndRetainsIntent() {
        val f=Fixture();f.arm()
        assertEquals(1,f.tasks.size);assertEquals(100+f.delay,f.tasks.single().first)
        f.advance(100+f.delay-1);assertTrue(f.state().startsWith("WAITING"));assertTrue(f.expired.isEmpty())
        f.advance(100+f.delay);assertEquals("UNKNOWN",f.state());assertEquals(listOf(f.serials),f.expired);f.retained()
    }
    @Test fun expiryUsesLiveSerialsAndRequiresNewerRecoveryEvidence() {
        val f=Fixture();f.arm();assertEquals(0,f.reads)
        f.serials=MachineWriteWatchdog.Serials(70,110,130,170,190,230)
        f.advance(100+f.delay);assertEquals(1,f.reads);assertEquals(listOf(f.serials),f.expired)
        assertFalse(f.observe(f.serials));assertEquals("UNKNOWN",f.state())
        assertTrue(f.observe(f.fresh()));f.retained()
    }
    @Test fun staleTokenDoesNotMutateActiveTransaction() {
        val f=Fixture();f.arm(f.token+1);f.advance(100+f.delay)
        assertTrue(f.state().startsWith("WAITING"));assertTrue(f.expired.isEmpty());f.retained()
    }
    @Test fun confirmedTransactionDoesNotBecomeUnknown() {
        val f=Fixture();f.arm();assertTrue(f.observe(f.fresh()));assertEquals("CONFIRMED",f.state())
        f.advance(100+f.delay);assertEquals("CONFIRMED",f.state());assertTrue(f.expired.isEmpty());f.retained()
    }
    @Test fun oldDeadlineCannotTimeoutNewTransaction() {
        val f=Fixture();f.arm();assertTrue(f.observe(f.fresh()))
        f.serials=f.fresh();val newer=f.begin();assertTrue(newer>f.token);f.success(newer)
        f.advance(100+f.delay);assertTrue(f.state().startsWith("WAITING"));assertTrue(f.expired.isEmpty());f.retained()
    }
    @Test fun repeatedSchedulerCallbackCannotPublishDuplicateUnknown() {
        val f=Fixture();f.arm();val callback=f.tasks.single().second
        f.advance(100+f.delay);callback()
        assertEquals(1,f.expired.size);assertEquals("UNKNOWN",f.state());f.retained()
    }
}
