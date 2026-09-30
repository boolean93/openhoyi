package io.openhoyi.session

import java.time.LocalDateTime
import io.openhoyi.protocol.StartParameters
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule
private class SessionDriver:GattDriver {
    val calls=mutableListOf<Triple<Long,Long,GattOperation>>()
    override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {calls+=Triple(generation,token,operation);return true}
    override fun close(generation:Long){}
}
fun deviceChecks():Int {
    var tests=0
    fun case(name:String,f:()->Unit){f();tests++;println("PASS $name")}
    fun hex(s:String)=s.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
    case("accepted write with a failed GATT callback has an unknown device outcome") {
        val write=GattOperation.Write(KnownGatt.coffeeWrite,hex("0402005D00"),true)
        check(GattCallbackResult.fromStatus(write,0) is OperationResult.Success)
        check(GattCallbackResult.fromStatus(write,133) is OperationResult.Unknown)
        check(GattCallbackResult.fromStatus(GattOperation.Discover,133) is OperationResult.Failed)
    }
    case("coffee ready requires authentication transport and fresh settings on matching endpoint") {
        val d=SessionDriver();val received=mutableListOf<io.openhoyi.protocol.HoyiMessage>()
        val s=DeviceSession(DeviceRole.COFFEE,d,{0},coffeeFrame={frame,_->received+=frame})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(r:OperationResult=OperationResult.Success()){val(g,t,_)=d.calls.last();s.onComplete(g,t,r)}
        complete();complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();check(s.state==DeviceState.INITIALIZING)
        val settings=hex("830113FD5C007D0F350019006E")
        s.onNotification(s.generation,KnownGatt.bookooNotify,settings);check(s.state!=DeviceState.READY)
        s.onNotification(s.generation,KnownGatt.coffeeNotify,settings)
        check(s.state!=DeviceState.READY && received.isEmpty())
        complete();check(s.state==DeviceState.SYNCHRONIZING)
        s.onNotification(s.generation,KnownGatt.coffeeNotify,
            hex("40000ACA0B2A0000000000000000001E22DA47"))
        check(received.isEmpty())
        s.onNotification(s.generation,KnownGatt.coffeeNotify,settings)
        check(s.state==DeviceState.READY && received.size==1)
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        var settingResult:OperationResult?=null
        s.writeSetting(MachineSettingChange.BrewTemperature(93)){settingResult=it}
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("0402005D00")))
        complete();check(settingResult is OperationResult.Success)
        var sleepResult:OperationResult?=null
        s.enterSleep { sleepResult=it }
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("2001A5A521")))
        complete();check(sleepResult is OperationResult.Success)
        s.setBrewWait(92) { sleepResult=it }
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("1102005C00")))
        complete();check(sleepResult is OperationResult.Success)
        s.setBrewWait(0) { sleepResult=it }
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("1102000000")))
        complete();check(sleepResult is OperationResult.Success)
        s.resetCupCount(25) { sleepResult=it }
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("0A01A5A500")))
        complete();check(sleepResult is OperationResult.Success)
        s.disconnect();s.onNotification(s.generation,KnownGatt.coffeeNotify,settings);check(s.state==DeviceState.DISCONNECTED)
    }
    case("pre-auth settings alone time out without enabling coffee writes") {
        val d=SessionDriver();var now=0L
        val s=DeviceSession(DeviceRole.COFFEE,d,{now})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(result:OperationResult=OperationResult.Success()){
            val(g,t,_)=d.calls.last();s.onComplete(g,t,result)
        }
        complete()
        complete(OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        complete()
        check(s.state==DeviceState.SYNCHRONIZING)
        now=10_001;s.tick()
        check(s.state==DeviceState.FAILED)
        val before=d.calls.size
        var result:OperationResult?=null
        s.writeSetting(MachineSettingChange.BrewTemperature(93)){result=it}
        check(result is OperationResult.Failed && d.calls.size==before)
    }
    case("weekly sleep write waits 500ms and never sends second fragment after a failed first") {
        val d=SessionDriver();var now=0L
        val s=DeviceSession(DeviceRole.COFFEE,d,{now})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(r:OperationResult=OperationResult.Success()){val(g,t,_)=d.calls.last();s.onComplete(g,t,r)}
        complete();complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete();s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        val plan=WeeklySleepSchedule(List(7){WeeklySleepDay(true,SleepDay(22,15,7,30))})
        var result:OperationResult?=null
        s.writeSleepSchedule(plan){result=it}
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("0910960F071E960F071E960F071E960F071E00")))
        val firstCount=d.calls.size
        complete();now=499;s.tick();check(d.calls.size==firstCount && result==null)
        var overlapping:OperationResult?=null
        s.writeSetting(MachineSettingChange.Light(true)){overlapping=it}
        check(overlapping is OperationResult.Failed && d.calls.size==firstCount)
        now=500;s.tick();check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("090C960F071E960F071E960F071E00")))
        complete();check(result is OperationResult.Success)
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        s.writeSleepSchedule(plan){result=it};complete(OperationResult.Failed("write failed"))
        val failedCount=d.calls.size;now=2000;s.tick()
        check(result is OperationResult.Failed && d.calls.size==failedCount)
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        s.writeSleepSchedule(plan){result=it};complete()
        now=2500;s.tick();complete(OperationResult.Failed("second write failed"))
        check(result is OperationResult.Unknown)
    }
    case("weekly sleep write cancels pending second fragment on disconnect") {
        val d=SessionDriver();var now=0L
        val s=DeviceSession(DeviceRole.COFFEE,d,{now})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(r:OperationResult=OperationResult.Success()){val(g,t,_)=d.calls.last();s.onComplete(g,t,r)}
        complete();complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete();s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        val plan=WeeklySleepSchedule(List(7){WeeklySleepDay(false,SleepDay(0,0,0,0))})
        val outcomes=mutableListOf<OperationResult>()
        s.writeSleepSchedule(plan){outcomes+=it};complete()
        val sent=d.calls.size
        s.disconnect();now=600;s.tick()
        check(outcomes.size==1 && outcomes.single() is OperationResult.Unknown && d.calls.size==sent)
    }
    case("BOOKOO initialization is paced and requires sample after completed initialization") {
        val d=SessionDriver();var now=0L;val s=DeviceSession(DeviceRole.BOOKOO,d,{now})
        s.connect("scale")
        val firstGeneration=s.generation
        s.connect("scale")
        check(s.generation==firstGeneration && d.calls.size==1)
        fun complete(r:OperationResult=OperationResult.Success()){val(g,t,_)=d.calls.last();s.onComplete(g,t,r)}
        complete();complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.bookooWrite,true,false,false,false),CharacteristicInfo(KnownGatt.bookooNotify,false,false,true,false))))
        complete();val initial=d.calls.size
        now=499;s.tick();check(d.calls.size==initial)
        val frame=hex("030B000000012D007A3A2D03424600C803010084")
        s.onNotification(s.generation,KnownGatt.bookooNotify,frame);check(s.state!=DeviceState.READY)
        repeat(4){now+=501;s.tick();check(d.calls.last().third is GattOperation.Write);complete()}
        check(s.state==DeviceState.SYNCHRONIZING)
        s.onNotification(s.generation,KnownGatt.bookooNotify,frame);check(s.state==DeviceState.READY)
        val readyCalls=d.calls.size
        s.connect("scale")
        check(s.generation==firstGeneration && d.calls.size==readyCalls && s.state==DeviceState.READY)
        s.connect("other-scale")
        check(s.generation!=firstGeneration && d.calls.size==readyCalls+1 && s.state==DeviceState.CONNECTING)
    }
    case("unsupported coffee firmware never opens control gate") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0});s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(r:OperationResult=OperationResult.Success()){val(g,t,_)=d.calls.last();s.onComplete(g,t,r)}
        complete();complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))));complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830114FD5C007D0F350019006E"))
        check(s.state==DeviceState.UNSUPPORTED)
        var result:OperationResult?=null;s.stopExtraction{result=it};check(result is OperationResult.Failed)
        s.writeSetting(MachineSettingChange.SteamHeating(false)){result=it};check(result is OperationResult.Failed)
        s.enterSleep {result=it};check(result is OperationResult.Failed)
        s.setBrewWait(92) {result=it};check(result is OperationResult.Failed)
        s.resetCupCount(25) { result=it };check(result is OperationResult.Failed)
    }
    case("live unauthenticated telemetry never reaches product state or triggers extra writes") {
        val d=SessionDriver();var now=0L;var telemetry=0
        val s=DeviceSession(DeviceRole.COFFEE,d,{now},coffeeFrame={_,_->telemetry++})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,22,12,0),"000000"))
        fun complete(r:OperationResult=OperationResult.Success()){val(g,t,_)=d.calls.last();s.onComplete(g,t,r)}
        complete();complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        // Real 19-byte idle notification observed even when authentication was not confirmed.
        repeat(10){now=it*1000L;s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("40000ACA0B2A0000000000000000001E22DA47"));s.tick();check(s.state==DeviceState.SYNCHRONIZING)}
        check(telemetry==0)
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
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
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
    case("session start requires fresh awake idle without blocking alarms") {
        val d=SessionDriver();var now=0L
        val s=DeviceSession(DeviceRole.COFFEE,d,{now})
        val profile=StartParameters(false,false,2,7,91,108,false,0,90,65,0,0,350,22,170,0,0)
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
        fun rejectedStart() {
            val before=d.calls.size
            result=null;s.startExtraction(profile){result=it}
            check(result is OperationResult.Failed && d.calls.size==before)
        }
        fun idle(sleep:Int=0,alarm:Int=0) {
            val b=hex("400024BF2F1C770B00000000000000190321AF")
            b[8]=sleep.toByte();b[12]=(alarm shr 8).toByte();b[13]=alarm.toByte()
            s.onNotification(s.generation,KnownGatt.coffeeNotify,b)
        }
        rejectedStart()
        idle();now=1501;rejectedStart()
        idle(sleep=1);rejectedStart()
        idle(alarm=1);rejectedStart()
        idle(alarm=0x4000)
        result=null;s.startExtraction(profile){result=it}
        check(result==null && (d.calls.last().third as GattOperation.Write).bytes[0].toInt()==2)
        complete();check(result is OperationResult.Success)
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("80080700000000052421325103"))
        rejectedStart()
        // A fresh observation at enqueue time is insufficient if another GATT write delays dispatch.
        idle()
        s.writeSetting(MachineSettingChange.Light(true)){}
        val beforeQueued=d.calls.size
        result=null;s.startExtraction(profile){result=it}
        check(result==null && d.calls.size==beforeQueued)
        now+=1501;complete()
        check(result is OperationResult.Failed && d.calls.size==beforeQueued)
        // An extraction notification also revokes a queued start, even while its idle is fresh.
        idle()
        s.writeSetting(MachineSettingChange.Light(false)){}
        val beforeActive=d.calls.size
        result=null;s.startExtraction(profile){result=it}
        check(result==null && d.calls.size==beforeActive)
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("80080700000000052421325103"))
        complete()
        check(result is OperationResult.Failed && d.calls.size==beforeActive)
        s.disconnect()
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        complete()
        val(g2,t2,_)=d.calls.last()
        s.onComplete(g2,t2,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        rejectedStart()
    }
    case("queued machine settings do not dispatch after idle evidence expires") {
        val d=SessionDriver();var now=0L
        val s=DeviceSession(DeviceRole.COFFEE,d,{now})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        var pending:OperationResult?=null
        s.writeSetting(MachineSettingChange.Light(true)){}
        val submitted=d.calls.size
        s.resetCupCount(25) { pending=it }
        check(pending==null && d.calls.size==submitted)
        now=1_501;complete()
        check(pending is OperationResult.Failed && d.calls.size==submitted)
    }
    case("all coffee writes except emergency stop require fresh idle at submission") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        val plan=WeeklySleepSchedule(List(7){WeeklySleepDay(false,SleepDay(0,0,0,0))})
        val before=d.calls.size
        val rejected=mutableListOf<OperationResult>()
        s.writeSetting(MachineSettingChange.Light(true)){rejected+=it}
        s.writeSleepSchedule(plan){rejected+=it}
        s.enterSleep {rejected+=it}
        s.resetCupCount(25) {rejected+=it}
        s.setBrewWait(92){rejected+=it}
        check(rejected.size==5 && rejected.all{it is OperationResult.Failed} && d.calls.size==before)
        s.setBrewWait(0){rejected+=it}
        check(rejected.size==6 && rejected.last() is OperationResult.Failed && d.calls.size==before)
    }
    case("preheat cancel is rechecked before a queued GATT write") {
        val d=SessionDriver();var now=0L;val s=DeviceSession(DeviceRole.COFFEE,d,{now})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        s.writeSetting(MachineSettingChange.Light(true)){}
        val submitted=d.calls.size
        var cancellation:OperationResult?=null
        s.setBrewWait(0){cancellation=it}
        check(cancellation==null && d.calls.size==submitted)
        now=100
        s.onNotification(s.generation,KnownGatt.coffeeNotify,
            hex("80080700000000052421325103"))
        complete()
        check(cancellation is OperationResult.Failed && d.calls.size==submitted)
    }
    case("queued cup reset rejects a changed device count") {
        for (changedSettings in listOf(true,false)) {
            val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
            s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
            fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
            complete()
            val(g,t,_)=d.calls.last()
            s.onComplete(g,t,OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
            s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
            s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
            s.writeSetting(MachineSettingChange.Light(true)){}
            val submitted=d.calls.size
            var result:OperationResult?=null
            s.resetCupCount(25) {result=it}
            check(result==null && d.calls.size==submitted)
            val updated=if(changedSettings) hex("830113FD5C007D0F35001A006E")
                else hex("400024BF2F1C770B000000000000001A0321AF")
            s.onNotification(s.generation,KnownGatt.coffeeNotify,updated)
            complete()
            check(result is OperationResult.Failed && d.calls.size==submitted)
        }
    }
    case("cup reset requires a positive confirmed count on both reports") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        val before=d.calls.size
        for(count in listOf(-1,0,24,26,65536)) {
            var result:OperationResult?=null
            s.resetCupCount(count){result=it}
            check(result is OperationResult.Failed && d.calls.size==before)
        }
        s.resetCupCount(25){}
        check(d.calls.size==before+1)
    }
    case("settings freshness rejects missing future and negative timestamps") {
        check(!SettingsFreshness.isFresh(null,180_000))
        check(!SettingsFreshness.isFresh(-1,0))
        check(!SettingsFreshness.isFresh(101,100))
        check(SettingsFreshness.isFresh(0,180_000))
        check(!SettingsFreshness.isFresh(0,180_001))
    }
    case("stale settings cannot authorize a paired setting write after fresh idle") {
        val d=SessionDriver();var now=0L;val s=DeviceSession(DeviceRole.COFFEE,d,{now})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        val settings=hex("830113FD5C007D0F350019006E")
        val idle=hex("400024BF2F1C770B00000000000000190321AF")
        s.onNotification(s.generation,KnownGatt.coffeeNotify,settings)
        now=180_001
        s.onNotification(s.generation,KnownGatt.coffeeNotify,idle)
        val before=d.calls.size
        var result:OperationResult?=null
        s.writeSetting(MachineSettingChange.StandbyDelay(30,53)){result=it}
        check(result is OperationResult.Failed && d.calls.size==before)
        s.onNotification(s.generation,KnownGatt.coffeeNotify,settings)
        result=null
        s.writeSetting(MachineSettingChange.StandbyDelay(30,53)){result=it}
        check(result==null && d.calls.size==before+1)
    }
    case("queued setting is withheld if settings expire before GATT dispatch") {
        val d=SessionDriver();var now=0L;val s=DeviceSession(DeviceRole.COFFEE,d,{now})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        s.writeSetting(MachineSettingChange.Light(true)){}
        val submitted=d.calls.size
        var queued:OperationResult?=null
        s.writeSetting(MachineSettingChange.StandbyDelay(30,53)){queued=it}
        check(queued==null && d.calls.size==submitted)
        now=180_001
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        complete()
        check(queued is OperationResult.Failed && d.calls.size==submitted)
    }
    case("queued standby writes reject a newly observed companion value") {
        for (change in listOf(MachineSettingChange.StandbyDelay(30,53),
                MachineSettingChange.StandbyTemperature(60,15))) {
            val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
            s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
            fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
            complete()
            val(g,t,_)=d.calls.last()
            s.onComplete(g,t,OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
            val settings=hex("830113FD5C007D0F350019006E")
            s.onNotification(s.generation,KnownGatt.coffeeNotify,settings)
            s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
            s.writeSetting(MachineSettingChange.Light(true)){}
            val submitted=d.calls.size
            var result:OperationResult?=null
            s.writeSetting(change){result=it}
            check(result==null && d.calls.size==submitted)
            val updated=settings.copyOf()
            if(change is MachineSettingChange.StandbyDelay) updated[8]=54 else updated[7]=30
            s.onNotification(s.generation,KnownGatt.coffeeNotify,updated)
            complete()
            check(result is OperationResult.Failed && d.calls.size==submitted)
            result=null
            s.writeSetting(change){result=it}
            check(result is OperationResult.Failed && d.calls.size==submitted)
        }
    }
    case("weekly sleep second fragment is withheld when machine starts extracting") {
        val d=SessionDriver();var now=0L;val s=DeviceSession(DeviceRole.COFFEE,d,{now})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        val plan=WeeklySleepSchedule(List(7){WeeklySleepDay(false,SleepDay(0,0,0,0))})
        var result:OperationResult?=null
        s.writeSleepSchedule(plan){result=it};complete()
        val submitted=d.calls.size
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("80080700000000052421325103"))
        now=500;s.tick()
        check(result is OperationResult.Unknown && d.calls.size==submitted)
    }
    case("emergency stop cancels queued sleep write before it reaches the machine") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        s.writeSetting(MachineSettingChange.Light(true)){}
        val plan=WeeklySleepSchedule(List(7){WeeklySleepDay(false,SleepDay(0,0,0,0))})
        var planResult:OperationResult?=null
        s.writeSleepSchedule(plan){planResult=it}
        val beforeStop=d.calls.size
        s.stopExtraction { }
        check(planResult is OperationResult.Unknown && d.calls.size==beforeStop)
        complete()
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(
            io.openhoyi.protocol.CoffeeCommands.stop(7).frame.toByteArray()))
        complete()
        check(d.calls.size==beforeStop+1)
    }
    case("a failing sleep observer cannot prevent emergency stop") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        s.writeSetting(MachineSettingChange.Light(true)){}
        val plan=WeeklySleepSchedule(List(7){WeeklySleepDay(false,SleepDay(0,0,0,0))})
        s.writeSleepSchedule(plan){error("observer failed")}
        s.stopExtraction { }
        complete()
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(
            io.openhoyi.protocol.CoffeeCommands.stop(7).frame.toByteArray()))
    }
    return tests
}
