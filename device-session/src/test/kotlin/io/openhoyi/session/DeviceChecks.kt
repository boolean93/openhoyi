package io.openhoyi.session

import java.time.LocalDateTime
import io.openhoyi.protocol.StartParameters
import io.openhoyi.protocol.MachineSettingChange
private class SessionDriver:GattDriver {
    val calls=mutableListOf<Triple<Long,Long,GattOperation>>()
    override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {calls+=Triple(generation,token,operation);return true}
    override fun close(generation:Long){}
}
fun deviceChecks():Int {
    var tests=0
    fun case(name:String,f:()->Unit){f();tests++;println("PASS $name")}
    fun hex(s:String)=s.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
    case("coffee ready requires authentication transport and fresh settings on matching endpoint") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(r:OperationResult=OperationResult.Success()){val(g,t,_)=d.calls.last();s.onComplete(g,t,r)}
        complete();complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();check(s.state==DeviceState.INITIALIZING)
        val settings=hex("830113FD5C007D0F350019006E")
        s.onNotification(s.generation,KnownGatt.bookooNotify,settings);check(s.state!=DeviceState.READY)
        complete();s.onNotification(s.generation,KnownGatt.coffeeNotify,settings);check(s.state==DeviceState.READY)
        var settingResult:OperationResult?=null
        s.writeSetting(MachineSettingChange.BrewTemperature(93)){settingResult=it}
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("0402005D00")))
        complete();check(settingResult is OperationResult.Success)
        s.disconnect();s.onNotification(s.generation,KnownGatt.coffeeNotify,settings);check(s.state==DeviceState.DISCONNECTED)
    }
    case("BOOKOO initialization is paced and requires sample after completed initialization") {
        val d=SessionDriver();var now=0L;val s=DeviceSession(DeviceRole.BOOKOO,d,{now})
        s.connect("scale")
        fun complete(r:OperationResult=OperationResult.Success()){val(g,t,_)=d.calls.last();s.onComplete(g,t,r)}
        complete();complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.bookooWrite,true,false,false,false),CharacteristicInfo(KnownGatt.bookooNotify,false,false,true,false))))
        complete();val initial=d.calls.size
        now=499;s.tick();check(d.calls.size==initial)
        val frame=hex("030B000000012D007A3A2D03424600C803010084")
        s.onNotification(s.generation,KnownGatt.bookooNotify,frame);check(s.state!=DeviceState.READY)
        repeat(4){now+=501;s.tick();check(d.calls.last().third is GattOperation.Write);complete()}
        check(s.state==DeviceState.SYNCHRONIZING)
        s.onNotification(s.generation,KnownGatt.bookooNotify,frame);check(s.state==DeviceState.READY)
    }
    case("unsupported coffee firmware never opens control gate") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0});s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(r:OperationResult=OperationResult.Success()){val(g,t,_)=d.calls.last();s.onComplete(g,t,r)}
        complete();complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))));complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830114FD5C007D0F350019006E"))
        check(s.state==DeviceState.UNSUPPORTED)
        var result:OperationResult?=null;s.stopExtraction{result=it};check(result is OperationResult.Failed)
        s.writeSetting(MachineSettingChange.SteamHeating(false)){result=it};check(result is OperationResult.Failed)
    }
    case("live unauthenticated telemetry never opens ready gate or triggers extra writes") {
        val d=SessionDriver();var now=0L;var telemetry=0
        val s=DeviceSession(DeviceRole.COFFEE,d,{now},coffeeFrame={_,_->telemetry++})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,22,12,0),"000000"))
        fun complete(r:OperationResult=OperationResult.Success()){val(g,t,_)=d.calls.last();s.onComplete(g,t,r)}
        complete();complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        // Real 19-byte idle notification observed even when authentication was not confirmed.
        repeat(10){now=it*1000L;s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("40000ACA0B2A0000000000000000001E22DA47"));s.tick();check(s.state==DeviceState.SYNCHRONIZING)}
        check(telemetry==10)
        var result:OperationResult?=null;s.stopExtraction{result=it};check(result is OperationResult.Failed)
        check(d.calls.count{it.third is GattOperation.Write}==1)
        now=10_000;s.tick();check(s.state==DeviceState.FAILED)
    }
    case("verified factory slot frame passes session gate and stop uses active slot") {
        val factoryHex="02115C0046005A3C000001F41900C8000000004B"
        val profile=StartParameters(false,false,2,1,92,70,false,0,90,60,0,0,500,25,200,0,0)
        val d=SessionDriver()
        val s=DeviceSession(DeviceRole.COFFEE,d,{0},legacyVerifiedStartFrames=setOf(factoryHex))
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        check(s.state==DeviceState.READY)
        var result:OperationResult?=null
        val control=CoffeeSessionControl(s)
        control.start(profile){result=it}
        check(d.calls.last().third is GattOperation.Write)
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex(factoryHex)))
        complete();check(result is OperationResult.Success)
        val writes=d.calls.size
        s.startExtraction(profile.copy(maximumWaterMl=71)){result=it}
        check(result is OperationResult.Failed && d.calls.size==writes)
        control.stop {}
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("0200010000")))
    }
    return tests
}
