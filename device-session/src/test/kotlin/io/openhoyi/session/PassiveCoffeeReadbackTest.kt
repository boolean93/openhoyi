package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

class PassiveCoffeeReadbackTest {
    private class Rig {
        val f=CoffeeSessionFixture(trackCoffeeDelivery=true)
        val s=f.session
        val source=requireNotNull(s.coffeeReadbackCursor).source
        var result:OperationResult?=null
        var cut:PassiveCoffeeReadback?=null
        fun done(value:OperationResult) {
            result=value
            if(value is OperationResult.Success) cut=PassiveCoffeeReadback.after(source,s.coffeeReadbackCursor,f.now)
        }
        fun receive(hex:String,now:Long,captured:Long=now,generation:Long=s.generation) =
            receive(f.hex(hex),now,captured,generation)
        private fun receive(frame:ByteArray,now:Long,captured:Long=now,generation:Long=s.generation) {
            f.now=now;s.onNotification(generation,KnownGatt.coffeeNotify,frame,captured)
        }
        fun idle(now:Long,count:Int=25,sleep:Int=0,captured:Long=now,generation:Long=s.generation) =
            receive(f.hex("400024BF2F1C770B%02X0000000000%04X032100".format(sleep,count))
                .withCoffeeNotificationChecksum(),now,captured,generation)
        fun settings(now:Long,count:Int=25) = receive(f.hex("830113FD5C007D0F35%04X0000".format(count))
            .withCoffeeNotificationChecksum(),now)
        fun weekly(first:String,second:String,now:Long) {
            receive(first,now)
            receive(second,now+1)
        }
        fun proof()=requireNotNull(cut)
    }
    private val p1="8340FE0A00071E0A00071E0A00071E0A00071E3D"
    private val p2="83800A00071E0A00071E0A00071E10"
    private fun schedule(f:CoffeeSessionFixture,head:String=p1):WeeklySleepSchedule {
        fun part(hex:String)=((HoyiCodec.decode(f.hex(hex)) as DecodeResult.Valid).value as SleepPart)
        return requireNotNull(WeeklySleepSchedule.fromReadback(part(head),part(p2)))
    }

    @Test fun weeklyWaitsForRealSecondResultAndPairedFreshPostCutEvidence()=with(Rig()) {
        weekly(p1,p2,10);idle(20)
        val expected=schedule(f)
        val firstTarget="8340FE0B00071E0A00071E0A00071E0A00071E3C"
        val target=schedule(f,firstTarget)
        s.writeSleepSchedule(target,expected,::done)
        f.complete()
        assertNull(result)
        f.now=520;s.tick() // actual second command submitted on the same fake driver
        assertArrayEquals(CoffeeCommands.sleepSchedule(target).last().frame.toByteArray(),
            (f.calls.last().third as GattOperation.Write).bytes)
        weekly(firstTarget,p2,521)
        assertNull(cut)
        f.now=523;f.complete()
        assertTrue(result is OperationResult.Success)
        fun pair()=proof().weeklyPair(s.latestNativeWeeklyFirst,s.latestNativeWeeklySecond,f.now)
        assertNull(pair())
        receive(p2,524)
        assertNull(pair())
        receive(firstTarget,525)
        receive(p2,526,captured=524)
        assertNull(pair())
        receive(p2,527)
        assertEquals(target.days,pair()?.days)
        receive(p1,528) // New first supersedes a once-valid second.
        assertNull(pair())
        receive(p2,529)
        assertEquals(expected.days,pair()?.days)
    }

    @Test fun cupCountsHaveToComeFromBothCurrentNativeFamiliesAndResolveAtomically()=with(Rig()) {
        settings(9);idle(10)
        s.resetCupCount(25,::done)
        f.now=11;f.complete()
        settings(12,0)
        fun pair()=proof().cupPair(s.latestOrdinaryCoffeeSettings,s.latestNativeIdle,f.now)
        assertFalse(pair())
        idle(13,count=0,captured=10)
        assertFalse(pair())
        idle(14,count=25)
        assertTrue(pair()) // The claims are present, but they do not match the reset target.
        val tracker=CupResetTracker()
        val token=requireNotNull(tracker.begin(25))
        tracker.written(token,OperationResult.Success(),1,1)
        assertFalse(tracker.observePair(2,0,1,0)) // baseline half cannot confirm.
        assertFalse(tracker.observePair(2,0,2,25))
        assertFalse(tracker.observePair(3,25,3,0)) // must not mix counts from the prior pair.
        idle(15,count=0)
        assertTrue(pair())
        assertTrue(tracker.observePair(4,0,4,0))
        assertEquals(CupResetTracker.State.CONFIRMED,tracker.state)
    }

    @Test fun expiryCutDoesNotReuseANativeHalfEvenWhenALegacySerialWouldAdvance()=with(Rig()) {
        settings(9);idle(10)
        s.resetCupCount(25,::done)
        f.now=11;f.complete()
        settings(12,0)
        val expired=requireNotNull(PassiveCoffeeReadback.after(source,s.coffeeReadbackCursor,13))
        idle(14,count=0)
        assertFalse(expired.cupPair(s.latestOrdinaryCoffeeSettings,s.latestNativeIdle,f.now))
        receive("830113FD5C007D0F3500000077",15,captured=12)
        assertFalse(expired.cupPair(s.latestOrdinaryCoffeeSettings,s.latestNativeIdle,f.now))
        settings(16,0)
        assertTrue(expired.cupPair(s.latestOrdinaryCoffeeSettings,s.latestNativeIdle,f.now))
    }

    @Test fun authenticatedBootstrapCanCalibrateButIsNeverAWriteReceipt()=with(Rig()) {
        val bootstrap=requireNotNull(s.preheatSettingsBaseline)
        assertNull(s.latestOrdinaryCoffeeSettings)
        assertSame(source,bootstrap.cursor.source)
        assertTrue(BrewSettingsEvidence.isAvailable(true,true,bootstrap.capturedAtMs,200_000L))
        receive("830113FD5C147D0F350019007A", 101)
        val newer=requireNotNull(s.preheatSettingsBaseline)
        assertEquals(20,newer.value.brewCompensationTenthsC)
        receive("830113FD5C007D0F350019006E", 102,captured=100)
        assertSame(newer.value,s.preheatSettingsBaseline!!.value)
    }

    @Test fun sleepAndPositivePreheatRequireTheirOwnLiveObservations() {
        with(Rig()) {
            idle(10);s.enterSleep(::done);f.now=11;f.complete()
            fun eligible(part:NativeCoffeeSample<IdleTelemetry>)=proof().idle(part,part.value,f.now)
            idle(12,sleep=1,captured=10)
            assertFalse(eligible(requireNotNull(s.latestNativeIdle)))
            idle(13,sleep=1,generation=s.generation-1)
            assertFalse(eligible(requireNotNull(s.latestNativeIdle)))
            idle(14,sleep=0)
            assertTrue(eligible(requireNotNull(s.latestNativeIdle)))
            assertEquals(0,s.latestNativeIdle!!.value.sleepStateRaw)
            idle(15,sleep=1)
            assertTrue(eligible(requireNotNull(s.latestNativeIdle)))
            assertEquals(1,s.latestNativeIdle!!.value.sleepStateRaw)
            f.now=2000
            assertFalse(eligible(requireNotNull(s.latestNativeIdle)))
        }
        with(Rig()) {
            idle(10);s.setBrewWait(92,{true},::done);f.now=11;f.complete()
            val waiting="80080800000000052421322003"
            receive(waiting,12,captured=10)
            val old=requireNotNull(s.latestNativeExtraction)
            assertFalse(proof().machineWaiting(old,old.value,f.now))
            receive(waiting,13)
            val observed=requireNotNull(s.latestNativeExtraction)
            assertTrue(proof().machineWaiting(observed,observed.value,f.now))
            assertNull(s.latestNativeIdle)
            idle(14)
            assertNull(s.latestNativeExtraction)
            assertTrue(proof().idle(s.latestNativeIdle,s.latestNativeIdle!!.value,f.now))
        }
    }
}
