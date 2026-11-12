package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class LateDeviceRecoveryTest {
    private class Fixture {
        var now=0L
        var permit=true
        val calls=mutableListOf<Triple<Long,Long,GattOperation>>()
        val frames=mutableListOf<io.openhoyi.protocol.HoyiMessage>()
        val session=DeviceSession(DeviceRole.COFFEE,object:GattDriver {
            override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
                calls+=Triple(generation,token,operation);return true
            }
            override fun close(generation:Long) {}
        },{now},coffeeFrame={frame,_->frames+=frame},synchronizationRefreshPermit={permit})
        fun complete(result:OperationResult=OperationResult.Success()) {
            val (g,t,_)=calls.last();session.onComplete(g,t,result)
        }
        fun begin() {
            session.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,11,12,21,42),"123456"))
            complete();complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
        }
        fun frame(hex:String)=session.onNotification(session.generation,KnownGatt.coffeeNotify,
            hex.chunked(2).map{it.toInt(16).toByte()}.toByteArray())
        fun idle()=frame("400009b50a1b00000000ca0200000027275277")
    }
    @Test fun capturedLateIdleFramesTriggerOneBoundedSyncAndSettingsAloneUnlocksReady() {
        val f=Fixture();f.begin()
        // Relative to captured 21:42:13.175 authentication success.
        val captured=listOf(7562L to "400009b50a1b00000000ca0200000027275277",
            8005L to "400009b40a1800000002a20700000027275e16",
            9028L to "400009b30a1800000000000000000027275fb7",
            10017L to "400009b30a1600000000000000000027276284")
        captured.forEachIndexed { index,(at,hex)->
            f.now=at;f.frame(hex);f.session.tick();if(index==0)f.complete()
            assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        }
        assertEquals(2,f.calls.count{it.third is GattOperation.Write});assertTrue(f.frames.isEmpty())
        f.now=11_000;f.frame("830113fd5c00780f3500270055")
        assertEquals(DeviceState.READY,f.session.state);assertEquals(1,f.frames.size)
    }
    @Test fun staleOrHostBlockedIdleDoesNotExtendOriginalDeadline() {
        for(stale in listOf(false,true)) {
            val f=Fixture();f.begin();f.now=7000;f.idle()
            if(stale)f.now=8501 else f.permit=false
            f.session.tick();assertEquals(1,f.calls.count{it.third is GattOperation.Write})
            f.now=10_000;f.session.tick();assertEquals(DeviceState.FAILED,f.session.state)
        }
    }
    @Test fun retryCannotRollDeadlineAndLateCompletionCannotReviveSession() {
        val f=Fixture();f.begin();f.now=9000;f.idle();f.session.tick();f.complete()
        f.now=14_999;f.idle();f.session.tick();assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        f.now=15_000;f.session.tick();assertEquals(DeviceState.FAILED,f.session.state)
        f.complete();assertEquals(DeviceState.FAILED,f.session.state)
    }
    @Test fun failedRetryAndDisconnectDropOwnerWithoutLeakingIntoReplacement() {
        val f=Fixture();f.begin();f.now=7000;f.idle();f.session.tick()
        val old=f.calls.last();f.session.disconnect();f.begin()
        f.session.onComplete(old.first,old.second,OperationResult.Success())
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        f.now=8000;f.idle();f.session.tick();f.complete(OperationResult.Failed("rejected"))
        assertEquals(DeviceState.FAILED,f.session.state)
    }
    @Test fun unsafeMalformedAndOldOwnerIdleCannotQualifyRefresh() {
        for(kind in 0..3) {
            val f=Fixture();f.begin();f.now=7000
            val bytes="400009b50a1b00000000ca0200000027275277".chunked(2).map{it.toInt(16).toByte()}.toByteArray()
            when(kind) {0->bytes[8]=1;1->bytes[13]=1;else->Unit}
            f.session.onNotification(if(kind==3)f.session.generation-1 else f.session.generation,
                KnownGatt.coffeeNotify,if(kind==2)bytes.copyOf(18) else bytes)
            f.session.tick();assertEquals(1,f.calls.count{it.third is GattOperation.Write})
        }
    }
    @Test fun lateRefreshCompletionWithoutTickCannotAcceptCachedSettings() {
        val f=Fixture();f.begin();f.now=7000;f.idle();f.session.tick()
        f.now=7100;f.frame("830113fd5c00780f3500270055")
        f.now=12_000;f.complete()
        assertEquals(DeviceState.FAILED,f.session.state);assertTrue(f.frames.isEmpty())
    }
    @Test fun extractionCancelsWholeSynchronizationRefreshEpisode() {
        val f=Fixture();f.begin();f.now=7000;f.idle()
        f.frame("80080700000000052421325103");f.session.tick()
        f.now=7500;f.idle();f.session.tick()
        assertEquals(1,f.calls.count{it.third is GattOperation.Write})
        f.now=10_000;f.session.tick();assertEquals(DeviceState.FAILED,f.session.state)
    }
    @Test fun settingsDuringRefreshCannotOpenReadyBeforeWriteSuccessOrSurviveFailure() {
        for(success in listOf(false,true)) {
            val f=Fixture();f.begin();f.now=7000;f.idle();f.session.tick()
            f.now=7100;f.frame("830113fd5c00780f3500270055")
            assertEquals(DeviceState.SYNCHRONIZING,f.session.state);assertTrue(f.frames.isEmpty())
            f.now=7200;f.complete(if(success)OperationResult.Success() else OperationResult.Failed("rejected"))
            assertEquals(if(success)DeviceState.READY else DeviceState.FAILED,f.session.state)
            assertEquals(if(success)7100L else null,f.session.settingsObservedAtMs)
        }
    }
    @Test fun matchedScanCancellationDoesNotFinishGattAttemptAndRepeatedHitsDoNotRenewBudget() {
        val p=ReconnectPolicy();p.foreground(0,true)
        assertTrue(p.shouldAttempt(120_000,false));p.scanStarted()
        p.observeScaleState(120_050,DeviceState.FAILED);p.observeScaleState(120_100,DeviceState.DISCONNECTED)
        assertFalse(p.shouldAttempt(120_200,false));p.discovered(120_400);p.scanFinished(120_400)
        assertFalse(p.shouldAttempt(130_000,false));p.observeScaleState(120_800,DeviceState.FAILED)
        assertFalse(p.shouldAttempt(129_999,false));assertTrue(p.shouldAttempt(130_000,false))
        p.discovered(130_400);p.scanFinished(130_400);p.observeScaleState(130_800,DeviceState.FAILED)
        assertTrue(p.shouldAttempt(140_000,false));p.discovered(140_400);p.observeScaleState(151_000,DeviceState.FAILED)
        p.foreground(151_001,true) // Activity may shorten next but cannot re-arm the fast window.
        assertTrue(p.shouldAttempt(151_001,false));p.discovered(151_100);p.observeScaleState(151_200,DeviceState.FAILED)
        assertFalse(p.shouldAttempt(161_001,false));assertTrue(p.shouldAttempt(181_001,false))
    }
}
