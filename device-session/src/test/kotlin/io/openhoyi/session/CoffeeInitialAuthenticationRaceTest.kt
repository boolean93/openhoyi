package io.openhoyi.session

import io.openhoyi.protocol.HoyiMessage
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class CoffeeInitialAuthenticationRaceTest {
    private class Fixture {
        var now=0L
        val calls=mutableListOf<Triple<Long,Long,GattOperation>>()
        val frames=mutableListOf<Pair<HoyiMessage,Long>>()
        val states=mutableListOf<DeviceState>()
        var observer:(DeviceState)->Unit={}
        var execute: (GattOperation)->Unit={}
        val session=DeviceSession(DeviceRole.COFFEE,object:GattDriver {
            override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
                calls+=Triple(generation,token,operation);execute(operation);return true
            }
            override fun close(generation:Long) {}
        },{now},{states+=it;observer(it)},{frame,at->frames+=frame to at})
        fun connect()=session.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(result:OperationResult=OperationResult.Success()) {
            val (gen,token,_)=calls.last();session.onComplete(gen,token,result)
        }
        fun begin() {
            connect();complete();complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete()
        }
        fun settings(hex:String="830113FD5C007D0F350019006E")=session.onNotification(session.generation,
            KnownGatt.coffeeNotify,hex.chunked(2).map{it.toInt(16).toByte()}.toByteArray())
    }
    @Test fun earlySettingsArePublishedOnceAfterSuccessWithOriginalTimestamp() {
        val f=Fixture();f.begin();f.now=10;f.settings()
        assertEquals(DeviceState.INITIALIZING,f.session.state);assertTrue(f.frames.isEmpty())
        assertNull(f.session.observedFirmware);assertNull(f.session.settingsObservedAtMs)
        f.now=11;f.complete()
        assertEquals(DeviceState.READY,f.session.state);assertEquals(1,f.frames.size)
        assertEquals(10L,f.session.settingsObservedAtMs);assertEquals(10L,f.frames.single().second);assertEquals(1,f.states.count{it==DeviceState.READY})
    }
    @Test fun settingsBeforeActualDispatchAreIgnored() {
        val f=Fixture();f.observer={if(it==DeviceState.INITIALIZING)f.settings()};f.begin();f.complete()
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state);assertTrue(f.frames.isEmpty())
    }
    @Test fun synchronousDriverNotificationAndCompletionAreSupported() {
        val f=Fixture();f.execute={if(it is GattOperation.Write){f.settings();f.complete()}};f.begin()
        assertEquals(DeviceState.READY,f.session.state);assertEquals(1,f.frames.size)
    }
    @Test fun failedAuthenticationCannotPublishAndCannotLeakIntoReconnect() {
        val f=Fixture();f.begin();f.settings();f.complete(OperationResult.Failed("auth rejected"))
        assertEquals(DeviceState.FAILED,f.session.state);assertTrue(f.frames.isEmpty());assertNull(f.session.settingsObservedAtMs)
        f.begin();f.complete();assertEquals(DeviceState.SYNCHRONIZING,f.session.state);assertTrue(f.frames.isEmpty())
        f.settings();assertEquals(DeviceState.READY,f.session.state);assertEquals(1,f.frames.size)
    }
    @Test fun disconnectDropsCachedSettings() {
        val f=Fixture();f.begin();f.settings();f.session.disconnect();f.begin();f.complete()
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state);assertTrue(f.frames.isEmpty())
    }
    @Test fun lateSuccessCannotExtendAuthenticationDeadlineWithoutTick() {
        val f=Fixture();f.begin();f.settings();f.now=5000;f.complete()
        assertEquals(DeviceState.FAILED,f.session.state);assertTrue(f.frames.isEmpty());assertNull(f.session.settingsObservedAtMs)
    }
    @Test fun expiredInitializationCannotBeResetByAuthenticationSuccess() {
        val f=Fixture();f.observer={if(it==DeviceState.INITIALIZING)f.now=10_000};f.begin();f.complete()
        assertEquals(DeviceState.FAILED,f.session.state);assertTrue(f.frames.isEmpty());assertNull(f.session.settingsObservedAtMs)
    }
    @Test fun synchronizationObserverNewSettingsWinOverCachedSettings() {
        val f=Fixture();f.begin();f.settings();f.now=20
        f.observer={if(it==DeviceState.SYNCHRONIZING)f.settings("830113FD5C007D0F350019006E")};f.complete()
        assertEquals(DeviceState.READY,f.session.state);assertEquals(listOf(20L),f.frames.map{it.second})
    }
    @Test fun synchronizationObserverReconnectCannotPublishOldCache() {
        val f=Fixture();f.begin();f.settings();var replaced=false
        f.observer={if(it==DeviceState.SYNCHRONIZING&&!replaced){replaced=true;f.connect()}};f.complete()
        assertEquals(DeviceState.CONNECTING,f.session.state);assertTrue(f.frames.isEmpty());assertNull(f.session.settingsObservedAtMs)
    }
    @Test fun synchronizationObserverCannotMakeCachedFrameFreshAgain() {
        val f=Fixture();f.begin();f.settings();f.observer={if(it==DeviceState.SYNCHRONIZING)f.now=200_000};f.complete()
        assertEquals(DeviceState.FAILED,f.session.state);assertTrue(f.frames.isEmpty());assertNull(f.session.settingsObservedAtMs)
    }
    @Test fun normalPostSuccessSettingsStillPublishOnce() {
        val f=Fixture();f.begin();f.complete();f.settings()
        assertEquals(DeviceState.READY,f.session.state);assertEquals(1,f.frames.size)
    }
}
