package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class OrdinaryWriteReadbackTest(private val kind:MachineWriteRecoveryState.Kind) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun kinds()=listOf(
            MachineWriteRecoveryState.Kind.SETTING,MachineWriteRecoveryState.Kind.SLEEP_NOW,
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE).map { arrayOf(it) }
    }
    private fun decode(hex:String)=(HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value
    private val setting=decode("830113FD5C007D0F350019006E") as Settings
    private val first=decode("8340FE0A00071E0A00071E0A00071E0A00071E3D") as SleepPart
    private val second=decode("83800A00071E0A00071E0A00071E10") as SleepPart
    private inner class Fixture(recordKind:MachineWriteRecoveryState.Kind=kind) {
        var disk=MachineWriteRecoveryState.Record(null,null)
        var mode=0
        var clears=0
        val order=mutableListOf<String>()
        val events=mutableListOf<OrdinaryWriteReadback.Event>()
        val recovery=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read()=disk
            override fun write(record:MachineWriteRecoveryState.Record):Boolean {
                if(!record.pending){clears++;order.add("clear");if(mode==1)return false;if(mode==2)error("clear fault")}
                disk=record;return true
            }
        })
        val settings=SettingsWriteTracker()
        val sleep=SleepNowTracker()
        val schedule=SleepScheduleWriteTracker()
        val token=when(kind) {
            MachineWriteRecoveryState.Kind.SETTING -> settings.begin(MachineSettingChange.BrewTemperature(93))
            MachineWriteRecoveryState.Kind.SLEEP_NOW -> sleep.begin()
            else -> schedule.begin(requireNotNull(WeeklySleepSchedule.fromReadback(first,second)),7,11)
        }!!
        init { assertTrue(recovery.arm(recordKind,"AA:BB:CC:DD:EE:01")) }
        fun written(result:OperationResult=OperationResult.Success()) {
            assertTrue(when(kind) {
                MachineWriteRecoveryState.Kind.SETTING -> settings.written(token,result,7)
                MachineWriteRecoveryState.Kind.SLEEP_NOW -> sleep.written(token,result,7)
                else -> schedule.written(token,result,7,11,first,second)
            })
        }
        fun observe(serial:Long=8,secondSerial:Long=12,match:Boolean=true,fresh:Boolean=true):Boolean {
            val publish:(OrdinaryWriteReadback.Event)->Unit={events.add(it);order.add(it.name)}
            return when(kind) {
                MachineWriteRecoveryState.Kind.SETTING -> OrdinaryWriteReadback.settings(settings,recovery,serial,setting.copy(brewTemperatureC=if(match)93 else 92),publish)
                MachineWriteRecoveryState.Kind.SLEEP_NOW -> OrdinaryWriteReadback.sleep(sleep,recovery,serial,if(match)1 else 0,publish)
                else -> OrdinaryWriteReadback.schedule(schedule,recovery,fresh,serial,secondSerial,if(match)first else decode("83407E0A00071E0A00071E0A00071E0A00071E3D") as SleepPart,second,publish)
            }
        }
        fun retained(){assertTrue(recovery.pending);assertTrue(disk.pending);assertEquals("AA:BB:CC:DD:EE:01",recovery.address)}
    }
    @Test fun freshConfirmationPublishesBeforeClearingAndRefreshesOnce() {
        val f=Fixture();f.written();assertTrue(f.observe())
        assertEquals(listOf("CONFIRMED","clear"),f.order);assertFalse(f.recovery.pending);assertFalse(f.disk.pending)
        assertFalse(f.observe(9,13));assertEquals(1,f.clears);assertEquals(1,f.events.size)
    }
    @Test fun transportNotCompletedOrOldReadCannotClear() {
        val f=Fixture();assertFalse(f.observe());f.retained();f.written()
        assertFalse(f.observe(7,11));f.retained();assertTrue(f.events.isEmpty());assertEquals(0,f.clears)
    }
    @Test fun mismatchedReadCannotConfirmWaitingWrite() {
        val f=Fixture();f.written();assertFalse(f.observe(match=false));f.retained()
        assertTrue(f.events.isEmpty());assertEquals(0,f.clears)
        assertTrue(f.observe(9,13));assertFalse(f.recovery.pending)
    }
    @Test fun clearFalseOrThrowRetainsGateAndFailureLast() {
        for(mode in listOf(1,2)) {
            val f=Fixture();f.mode=mode;f.written();assertTrue(f.observe());f.retained()
            assertEquals(listOf("CONFIRMED","clear","CLEAR_FAILED"),f.order);assertEquals(1,f.clears)
        }
    }
    @Test fun repeatedReadOnlyRetriesLocalClearWithoutRepeatingConfirmation() {
        val f=Fixture();f.mode=1;f.written();f.observe();f.events.clear();f.order.clear()
        assertTrue(f.observe(9,13));assertEquals(listOf("clear","CLEAR_FAILED"),f.order);f.retained()
        f.mode=0;f.events.clear();f.order.clear();assertTrue(f.observe(10,14))
        assertEquals(listOf("clear"),f.order);assertFalse(f.recovery.pending);assertEquals(3,f.clears)
    }
    @Test fun unrelatedRecordCannotBeCleared() {
        val f=Fixture(MachineWriteRecoveryState.Kind.CUP_RESET);f.written();assertFalse(f.observe())
        assertEquals(listOf(OrdinaryWriteReadback.Event.CONFIRMED),f.events);f.retained();assertEquals(0,f.clears)
    }
    @Test fun unknownNonmatchingReadReconcilesAndKeepsDurableGate() {
        val f=Fixture();f.written(OperationResult.Unknown("link"));assertFalse(f.observe(match=false))
        assertEquals(listOf(OrdinaryWriteReadback.Event.RECONCILED),f.events);f.retained();assertEquals(0,f.clears)
        assertFalse(f.observe(9,13,match=false));assertEquals(1,f.events.size)
    }
    @Test fun unknownMatchingReadPreservesDistinctTrackerSemantics() {
        val f=Fixture();f.written(OperationResult.Unknown("link"));val refreshed=f.observe()
        if(kind==MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE) {
            assertFalse(refreshed);assertEquals(listOf(OrdinaryWriteReadback.Event.RECONCILED),f.events);f.retained();assertEquals(0,f.clears)
        } else {
            assertTrue(refreshed);assertEquals(listOf(OrdinaryWriteReadback.Event.CONFIRMED),f.events);assertFalse(f.recovery.pending)
        }
    }
    @Test fun freshnessAndPairGuardAppliesOnlyToSchedule() {
        if(kind!=MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE) {
            val f=Fixture();f.written();assertTrue(f.observe(fresh=false));assertFalse(f.recovery.pending);return
        }
        val f=Fixture();f.written();assertFalse(f.observe(fresh=false));assertFalse(f.observe(secondSerial=11))
        f.retained();assertTrue(f.events.isEmpty());assertTrue(f.observe(9,12));assertFalse(f.recovery.pending)
    }
}
