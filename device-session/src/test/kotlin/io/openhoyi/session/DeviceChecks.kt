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
    fun receiveSleepReadback(session:DeviceSession,plan:WeeklySleepSchedule) {
        val first=ByteArray(20);first[0]=0x83.toByte();first[1]=0x40
        first[2]=plan.days.foldIndexed(0){index,bits,day->
            bits or if(day.enabled) (0x80 shr index) else 0}.toByte()
        val second=ByteArray(15);second[0]=0x83.toByte();second[1]=0x80.toByte()
        plan.days.forEachIndexed { index,day ->
            val bytes=if(index<4) first else second
            val offset=if(index<4) 3+index*4 else 2+(index-4)*4
            bytes[offset]=day.time.sleepHour.toByte();bytes[offset+1]=day.time.sleepMinute.toByte()
            bytes[offset+2]=day.time.wakeHour.toByte();bytes[offset+3]=day.time.wakeMinute.toByte()
        }
        session.onNotification(session.generation,KnownGatt.coffeeNotify,first)
        session.onNotification(session.generation,KnownGatt.coffeeNotify,second)
    }
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
        check(s.state!=DeviceState.READY && received.isEmpty() && s.observedFirmware==null)
        complete();check(s.state==DeviceState.SYNCHRONIZING)
        s.onNotification(s.generation,KnownGatt.coffeeNotify,
            hex("40000ACA0B2A0000000000000000001E22DA47"))
        check(received.isEmpty())
        s.onNotification(s.generation,KnownGatt.coffeeNotify,settings)
        check(s.state==DeviceState.READY && received.size==1)
        check(s.observedFirmware==CoffeeFirmware(1,1,3))
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        var settingResult:OperationResult?=null
        s.writeSetting(MachineSettingChange.BrewTemperature(93)){settingResult=it}
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("0402005D00")))
        complete();check(settingResult is OperationResult.Success)
        var sleepResult:OperationResult?=null
        s.enterSleep { sleepResult=it }
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("2001A5A521")))
        complete();check(sleepResult is OperationResult.Success)
        s.setBrewWait(92,{true}) { sleepResult=it }
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("1102005C00")))
        complete();check(sleepResult is OperationResult.Success)
        s.setBrewWait(0,{true}) { sleepResult=it }
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("1102000000")))
        complete();check(sleepResult is OperationResult.Success)
        s.resetCupCount(25) { sleepResult=it }
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("0A01A5A500")))
        complete();check(sleepResult is OperationResult.Success)
        s.disconnect();s.onNotification(s.generation,KnownGatt.coffeeNotify,settings);check(s.state==DeviceState.DISCONNECTED && s.observedFirmware==null)
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
        receiveSleepReadback(s,plan);s.writeSleepSchedule(plan,plan){result=it}
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("0910960F071E960F071E960F071E960F071E00")))
        val firstCount=d.calls.size
        complete();now=499;s.tick();check(d.calls.size==firstCount && result==null)
        var overlapping:OperationResult?=null
        s.writeSetting(MachineSettingChange.Light(true)){overlapping=it}
        check(overlapping is OperationResult.Failed && d.calls.size==firstCount)
        now=500;s.tick();check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("090C960F071E960F071E960F071E00")))
        complete();check(result is OperationResult.Success)
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        receiveSleepReadback(s,plan);s.writeSleepSchedule(plan,plan){result=it};complete(OperationResult.Failed("write failed"))
        val failedCount=d.calls.size;now=2000;s.tick()
        check(result is OperationResult.Failed && d.calls.size==failedCount)
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        receiveSleepReadback(s,plan);s.writeSleepSchedule(plan,plan){result=it};complete()
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
        receiveSleepReadback(s,plan);s.writeSleepSchedule(plan,plan){outcomes+=it};complete()
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
    case("cancelled preflight keeps its pending scale tare blocking the next shot") {
        for(nextTarget in listOf(0,3400)) for(outcome in listOf(OperationResult.Success(),OperationResult.Unknown("lost result"))) {
            val d=SessionDriver();var now=0L;var sampleSerial=0L
            val tare=StandaloneTare { now }
            val s=DeviceSession(DeviceRole.BOOKOO,d,{now})
            s.connect("scale")
            fun complete(r:OperationResult=OperationResult.Success()) {
                val(g,t,_)=d.calls.last();s.onComplete(g,t,r)
            }
            complete();complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.bookooWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.bookooNotify,false,false,true,false))))
            complete()
            repeat(4){now+=501;s.tick();complete()}
            s.onNotification(s.generation,KnownGatt.bookooNotify,hex("030B000000012D007A3A2D03424600C803010084"))
            check(s.state==DeviceState.READY)
            val scale=ScaleSessionControl(s,tare,{sampleSerial})
            var starts=0
            val coffee=object:CoffeeControl {
                override val ready=true
                override fun prepareStart(parameters:StartParameters)=true
                override fun startConditionsValid(parameters:StartParameters)=true
                override fun start(parameters:StartParameters,beforeDispatch:()->Boolean,done:(OperationResult)->Unit){
                if(!runCatching(beforeDispatch).getOrDefault(false)){done(OperationResult.Failed("caller rejected"));return}
starts++;done(OperationResult.Success())}
                override fun stop(done:(OperationResult)->Unit){error("preflight must not stop the coffee machine")}
            }
            val profile=StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)
            val c=ExtractionController(coffee,scale,{now})
            c.weight(WeightReading(0,now))
            check(c.start(profile,3400,0))
            c.manualStop()
            check(c.state==ExtractionState.IDLE && starts==0)
            val submitted=d.calls.size
            check(!c.start(profile,nextTarget,0))
            check(d.calls.size==submitted && starts==0)
            var duplicate:OperationResult?=null
            scale.tare({true}) { duplicate=it }
            check(duplicate is OperationResult.Failed && d.calls.size==submitted)
            complete(outcome)
            check(!c.start(profile,nextTarget,0))
            now+=100;tare.sample(++sampleSerial,0);c.weight(WeightReading(0,now))
            if(outcome is OperationResult.Unknown) {
                check(!c.start(profile,nextTarget,0))
                scale.tare({true}) {}
                complete()
                check(!c.start(profile,nextTarget,0))
                now+=100;tare.sample(++sampleSerial,0);c.weight(WeightReading(0,now))
            }
            check(c.start(profile,nextTarget,0))
        }
    }
    case("queued tare rechecks cancelled expired changed and ended-shot intent before dispatch") {
        for(cause in listOf("cancel","deadline","conditions","coffee_lost","flow_stop","independent")) {
        val d=SessionDriver();var now=0L
        val tare=StandaloneTare { now }
        val s=DeviceSession(DeviceRole.BOOKOO,d,{now})
        s.connect("scale")
        fun complete(r:OperationResult=OperationResult.Success()) {
            val(g,t,_)=d.calls.last();s.onComplete(g,t,r)
        }
        complete();complete(OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.bookooWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.bookooNotify,false,false,true,false))))
        complete();repeat(4){now+=501;s.tick();complete()}
        s.onNotification(s.generation,KnownGatt.bookooNotify,hex("030B000000012D007A3A2D03424600C803010084"))
        check(s.state==DeviceState.READY)
        // A transport write occupies the queue; the new shot's tare has not been sent.
        s.tare({true}) {}
        val scale=ScaleSessionControl(s,tare,{0})
        var starts=0;var conditions=true;var coffeeReady=true;var allowed=true
        val coffee=object:CoffeeControl {
            override val ready get()=coffeeReady
            override fun prepareStart(parameters:StartParameters)=true
            override fun startConditionsValid(parameters:StartParameters)=conditions
            override fun start(parameters:StartParameters,beforeDispatch:()->Boolean,done:(OperationResult)->Unit){
                if(!runCatching(beforeDispatch).getOrDefault(false)){done(OperationResult.Failed("caller rejected"));return}
starts++;done(OperationResult.Success())}
            override fun stop(done:(OperationResult)->Unit){check(starts>0);done(OperationResult.Success())}
        }
        val profile=StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)
        val c=ExtractionController(coffee,scale,{now});c.weight(WeightReading(0,now))
        val sent=d.calls.size
        if(cause=="independent") {
            scale.tare({allowed}) {}
            allowed=false
        } else if(cause=="flow_stop") {
            check(c.start(profile,0,0));now+=1500;c.tick()
            check(tare.state==StandaloneTare.State.WRITING && d.calls.size==sent)
            c.manualStop()
        } else {
            check(c.start(profile,3400,0) && d.calls.size==sent)
            when(cause) {
                "cancel" -> c.manualStop()
                "deadline" -> now+=5000 // No controller tick: the dispatch guard must suffice.
                "conditions" -> conditions=false
                "coffee_lost" -> coffeeReady=false
            }
        }
        complete()
        check(d.calls.size==sent && starts==if(cause=="flow_stop")1 else 0)
        check(c.state==if(cause=="flow_stop")ExtractionState.STOP_REQUESTED else ExtractionState.IDLE)
        check(tare.state==StandaloneTare.State.FAILED && scale.startAllowed)
        }
    }
    case("queued preheat intent cannot survive cancellation consumption or disconnection") {
        for(action in listOf("cancel","consume","disconnect")) {
            val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
            s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
            fun complete(r:OperationResult=OperationResult.Success()) {
                val(g,t,_)=d.calls.last();s.onComplete(g,t,r)
            }
            complete();complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
            s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
            s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
            s.writeSetting(MachineSettingChange.Light(false)){}
            val sent=d.calls.size
            val preparation=BrewPreparation()
            val token=requireNotNull(preparation.begin("curve",92))
            s.setBrewWait(92,{preparation.permitsWrite(token,92)}) {
                preparation.written(token,it,0)
            }
            when(action) {
                "cancel" -> {
                    val cancel=requireNotNull(preparation.beginCancel())
                    s.setBrewWait(0,{preparation.permitsWrite(cancel,0)}){preparation.cancelled(cancel,it)}
                }
                "consume" -> preparation.consumed()
                "disconnect" -> preparation.disconnected()
            }
            complete()
            val newWrites=d.calls.drop(sent).mapNotNull { (it.third as? GattOperation.Write)?.bytes }
            check(newWrites.none { it.contentEquals(hex("1102005C00")) })
            if(action=="cancel") {
                check(newWrites.size==1 && newWrites.single().contentEquals(hex("1102000000")))
                complete();check(preparation.state==BrewPreparation.State.CANCEL_WRITTEN)
            } else check(newWrites.isEmpty())
        }
    }
    case("unsupported coffee firmware never opens control gate") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0});s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(r:OperationResult=OperationResult.Success()){val(g,t,_)=d.calls.last();s.onComplete(g,t,r)}
        complete();complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))));complete();complete()
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830114FD5C007D0F350019006E"))
        check(s.state==DeviceState.UNSUPPORTED)
        check(s.observedFirmware==CoffeeFirmware(1,1,4))
        check(s.observedFirmware.toString()=="1.1.4")
        val writesBefore=d.calls.size
        var result:OperationResult?=null
        s.startExtraction(StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)){result=it}
        check(result is OperationResult.Failed)
        s.stopExtraction(7){result=it};check(result is OperationResult.Failed)
        s.writeSetting(MachineSettingChange.SteamHeating(false)){result=it};check(result is OperationResult.Failed)
        s.enterSleep {result=it};check(result is OperationResult.Failed)
        s.setBrewWait(92,{true}) {result=it};check(result is OperationResult.Failed)
        s.resetCupCount(25) { result=it };check(result is OperationResult.Failed)
        check(d.calls.size==writesBefore)
        val oldGeneration=s.generation
        s.connect("other",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"654321"))
        s.onNotification(oldGeneration,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
        check(s.observedFirmware==null)
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
        var result:OperationResult?=null;s.stopExtraction(7){result=it};check(result is OperationResult.Failed)
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
        s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400023F02F1C770B00000000000000190321AF"))
        var result:OperationResult?=null
        val control=CoffeeSessionControl(s)
        check(control.prepareStart(profile))
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
            b[2]=0x23;b[3]=0x8C.toByte()
            b[8]=sleep.toByte();b[12]=(alarm shr 8).toByte();b[13]=alarm.toByte()
            s.onNotification(s.generation,KnownGatt.coffeeNotify,b)
        }
        rejectedStart()
        idle();now=1501;rejectedStart()
        idle(sleep=1);rejectedStart()
        for (bit in 0..15) {
            if (bit == 14) continue
            idle(alarm=(1 shl bit) or 0x4000);rejectedStart()
        }
        // Unknown bit 15 appearing while a start is queued must revoke actual dispatch.
        idle()
        s.writeSetting(MachineSettingChange.Light(true)){}
        val beforeAlarm=d.calls.size
        result=null;s.startExtraction(profile){result=it}
        check(result==null && d.calls.size==beforeAlarm)
        idle(alarm=0x8000);complete()
        check(result is OperationResult.Failed && d.calls.size==beforeAlarm)
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
        receiveSleepReadback(s,plan);s.writeSleepSchedule(plan,plan){rejected+=it}
        s.enterSleep {rejected+=it}
        s.resetCupCount(25) {rejected+=it}
        s.setBrewWait(92,{true}){rejected+=it}
        check(rejected.size==5 && rejected.all{it is OperationResult.Failed} && d.calls.size==before)
        s.setBrewWait(0,{true}){rejected+=it}
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
        s.setBrewWait(0,{true}){cancellation=it}
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
    case("sleep enable requires a fresh complete pair and rechecks before dispatch") {
        val d=SessionDriver();var now=0L;val s=DeviceSession(DeviceRole.COFFEE,d,{now})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        fun receive(value:String)=s.onNotification(s.generation,KnownGatt.coffeeNotify,hex(value))
        val first="8340FE0A00071E0A00071E0A00071E0A00071E3D"
        val second="83800A00071E0A00071E0A00071E00"
        receive("830113FD5C007D0F350019006E")
        receive("400024BF2F1C770B00000000000000190321AF")
        val before=d.calls.size
        var result:OperationResult?=null
        s.writeSetting(MachineSettingChange.SleepScheduleEnabled(true)){result=it}
        check(result is OperationResult.Failed && d.calls.size==before)
        receive(first)
        s.writeSetting(MachineSettingChange.SleepScheduleEnabled(true)){result=it}
        check(result is OperationResult.Failed && d.calls.size==before)
        now=1_000;receive(second)
        receive("400024BF2F1C770B00000000000000190321AF")
        s.writeSetting(MachineSettingChange.Light(true)){}
        val submitted=d.calls.size
        result=null
        s.writeSetting(MachineSettingChange.SleepScheduleEnabled(true)){result=it}
        check(result==null && d.calls.size==submitted)
        now=1_000;receive(first)
        complete()
        check(result is OperationResult.Failed && d.calls.size==submitted)
        receive(second)
        result=null
        s.writeSetting(MachineSettingChange.SleepScheduleEnabled(true)){result=it}
        check(result==null && d.calls.size==submitted+1)
        complete()
        now=182_001
        receive("830113FD5C007D0F350019006E")
        receive("400024BF2F1C770B00000000000000190321AF")
        val calls=d.calls.size
        s.writeSetting(MachineSettingChange.SleepScheduleEnabled(true)){result=it}
        check(result is OperationResult.Failed && d.calls.size==calls)
        result=null
        s.writeSetting(MachineSettingChange.SleepScheduleEnabled(false)){result=it}
        check(result==null && d.calls.size==calls+1)
    }
    case("queued sleep enable cannot activate a changed complete plan") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        fun receive(value:String)=s.onNotification(s.generation,KnownGatt.coffeeNotify,hex(value))
        receive("830113FD5C007D0F350019006E")
        receive("400024BF2F1C770B00000000000000190321AF")
        receive("8340FE0A00071E0A00071E0A00071E0A00071E3D")
        receive("83800A00071E0A00071E0A00071E00")
        s.writeSetting(MachineSettingChange.Light(true)){}
        val submitted=d.calls.size
        var result:OperationResult?=null
        s.writeSetting(MachineSettingChange.SleepScheduleEnabled(true)){result=it}
        check(result==null && d.calls.size==submitted)
        receive("8340FE0B00071E0A00071E0A00071E0A00071E3D")
        receive("83800A00071E0A00071E0A00071E00")
        complete()
        check(result is OperationResult.Failed && d.calls.size==submitted)
    }
    case("sleep pair freshness rejects old missing inverted separated and invalid reports") {
        val raw=io.openhoyi.protocol.ByteFrame(byteArrayOf())
        val day=SleepDay(22,0,8,0)
        val first=io.openhoyi.protocol.SleepPart(0,0xFE,List(4){day},raw)
        val second=io.openhoyi.protocol.SleepPart(4,null,List(3){day},raw)
        fun fresh(a:Long?,b:Long?,now:Long=180_000)=
            SleepScheduleFreshness.isFresh(first,a,second,b,now)
        check(fresh(0,1_000))
        check(!fresh(0,1_000,180_001))
        check(!fresh(null,1_000) && !fresh(0,null))
        check(!fresh(1_000,0) && !fresh(0,10_001))
        check(fresh(0,10_000))
        check(!fresh(-1,0) && !fresh(180_001,180_002))
        check(!SleepScheduleFreshness.isFresh(null,0,second,1_000,1_000))
        check(!SleepScheduleFreshness.isFresh(first,0,null,1_000,1_000))
        check(!SleepScheduleFreshness.isFresh(io.openhoyi.protocol.SleepPart(0,0xFE,List(4){day.copy(sleepHour=25)},raw),
            0,second,1_000,1_000))
        check(!SleepScheduleFreshness.isFresh(first,0,
            io.openhoyi.protocol.SleepPart(4,null,List(3){day.copy(wakeMinute=61)},raw),1_000,1_000))
    }
    case("queued full schedule cannot overwrite newly reported untouched days") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        fun receive(value:String)=s.onNotification(s.generation,KnownGatt.coffeeNotify,hex(value))
        receive("830113FD5C007D0F350019006E")
        receive("400024BF2F1C770B00000000000000190321AF")
        receive("8340FE0A00071E0A00071E0A00071E0A00071E3D")
        receive("83800A00071E0A00071E0A00071E00")
        val target=WeeklySleepSchedule(List(7){WeeklySleepDay(true,SleepDay(if(it==0) 11 else 10,0,7,30))})
        s.writeSetting(MachineSettingChange.Light(true)){}
        val submitted=d.calls.size
        var result:OperationResult?=null
        s.writeSleepSchedule(target,WeeklySleepSchedule(List(7){WeeklySleepDay(true,SleepDay(10,0,7,30))})){result=it}
        check(result==null && d.calls.size==submitted)
        receive("8340FE0A00071E0A00071E0A00071E0A00071E3D")
        receive("83800C00071E0A00071E0A00071E00")
        complete()
        check(result is OperationResult.Failed && d.calls.size==submitted)
    }
    case("sleep tail accepts its own first readback but rejects unrelated changes and old baseline") {
        for(mode in 0..3) {
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
            fun idle()=s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
            idle()
            val expected=WeeklySleepSchedule(List(7){WeeklySleepDay(true,SleepDay(10,0,7,30))})
            val target=WeeklySleepSchedule(expected.days.mapIndexed{index,day->
                if(index==0) day.copy(time=day.time.copy(sleepHour=11)) else day})
            receiveSleepReadback(s,expected)
            val before=d.calls.size
            var result:OperationResult?=null
            s.writeSleepSchedule(target,target){result=it}
            check(result is OperationResult.Failed && d.calls.size==before)
            result=null
            s.writeSleepSchedule(target,expected){result=it}
            complete()
            val sent=d.calls.size
            val reported=WeeklySleepSchedule(target.days.mapIndexed{index,day->
                if(mode==1 && index==1 || mode==2 && index==4)
                    day.copy(time=day.time.copy(sleepHour=12)) else day})
            receiveSleepReadback(s,reported)
            now=if(mode==3) 180_001 else 500
            idle();s.tick()
            if(mode==0) {
                check(result==null && d.calls.size==sent+1)
                complete();check(result is OperationResult.Success)
            } else {
                check(result is OperationResult.Unknown && d.calls.size==sent)
                now+=500;s.tick();check(d.calls.size==sent)
            }
        }
    }
    case("positive preheat requires studio mode at submission and dispatch") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        fun receive(value:String)=s.onNotification(s.generation,KnownGatt.coffeeNotify,hex(value))
        receive("830113FD5C007D0F350019006E")
        receive("400024BF2F1C770B00000000000000190321AF")
        s.writeSetting(MachineSettingChange.Light(true)){}
        val submitted=d.calls.size
        var result:OperationResult?=null
        s.setBrewWait(92,{true}){result=it}
        check(result==null && d.calls.size==submitted)
        receive("830113F95C007D0F350019006E")
        complete()
        check(result is OperationResult.Failed && d.calls.size==submitted)
        result=null
        s.setBrewWait(92,{true}){result=it}
        check(result is OperationResult.Failed && d.calls.size==submitted)
        result=null
        s.setBrewWait(0,{true}){result=it}
        check(result==null && d.calls.size==submitted+1)
        complete()
        receive("830113FD5C007D0F350019006E")
        result=null
        s.setBrewWait(92,{true}){result=it}
        check(result==null && d.calls.size==submitted+2)
        complete();check(result is OperationResult.Success)
    }
    case("studio start checks corrected temperature and queued mode before dispatch") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
        val profile=StartParameters(false,false,2,7,91,108,false,0,90,65,0,0,350,22,170,0,0)
        s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        complete()
        val(g,t,_)=d.calls.last()
        s.onComplete(g,t,OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        fun receive(value:String)=s.onNotification(s.generation,KnownGatt.coffeeNotify,hex(value))
        receive("830113FD5C007D0F350019006E")
        receive("400024BF2F1C770B00000000000000190321AF")
        val before=d.calls.size
        var result:OperationResult?=null
        s.startExtraction(profile){result=it}
        check(result is OperationResult.Failed && d.calls.size==before)
        fun warm()=receive("4000238C2F1C770B00000000000000190321AF")
        warm()
        for(modeChange in 0..2) {
            receive(if(modeChange==2) "830113F95C007D0F350019006E" else "830113FD5C007D0F350019006E");warm()
            s.writeSetting(MachineSettingChange.Light(true)){}
            val queued=d.calls.size
            result=null
            s.startExtraction(profile){result=it}
            check(result==null && d.calls.size==queued)
            if(modeChange==1) receive("830113F95C007D0F350019006E")
            else if(modeChange==2) receive("830113FD5C007D0F350019006E")
            else receive("400022C42F1C770B00000000000000190321AF")
            complete()
            check(result is OperationResult.Failed && d.calls.size==queued)
        }
        // Compensation is subtracted in tenths of a degree, never added or ignored.
        receive("830113FD5C147D0F350019006E");warm()
        val calls=d.calls.size
        s.startExtraction(profile){result=it}
        check(result is OperationResult.Failed && d.calls.size==calls)
        receive("400024542F1C770B00000000000000190321AF")
        result=null;s.startExtraction(profile){result=it}
        check(result==null && d.calls.size==calls+1)
        complete()
        receive("830113F95C007D0F350019006E")
        receive("400022C42F1C770B00000000000000190321AF")
        result=null;s.startExtraction(profile){result=it}
        check(result==null && d.calls.size==calls+2)
        complete();check(result is OperationResult.Success)
    }
    case("shared brew target includes one-degree boundary and subtracts compensation") {
        check(BrewTemperaturePolicy.correctedTemperature(9300,20)==9100)
        check(BrewTemperaturePolicy.isAtTarget(9000,91))
        check(BrewTemperaturePolicy.isAtTarget(9200,91))
        check(!BrewTemperaturePolicy.isAtTarget(8999,91))
        check(!BrewTemperaturePolicy.isAtTarget(9201,91))
        check(!BrewTemperaturePolicy.isAtTarget(Int.MIN_VALUE,Int.MAX_VALUE))
    }
    case("tare wait keeps the originally approved machine settings") {
        for(change in 0..5) {
            val d=SessionDriver();var now=0L;val s=DeviceSession(DeviceRole.COFFEE,d,{now})
            val profile=StartParameters(false,false,2,7,91,108,false,0,90,65,0,0,350,22,170,0,0)
            s.connect("device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
            fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
            complete()
            val(g,t,_)=d.calls.last()
            s.onComplete(g,t,OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
            fun receive(value:String)=s.onNotification(s.generation,KnownGatt.coffeeNotify,hex(value))
            receive("830113FD5C007D0F350019006E")
            receive("4000238C2F1C770B00000000000000190321AF")
            val scale=object:ScaleControl {
                override val startAllowed=true
                override val ready=true
                override fun tare(beforeDispatch:()->Boolean,done:(OperationResult)->Unit){done(if(beforeDispatch())OperationResult.Success() else OperationResult.Failed("guard rejected"))}
            }
            val controller=ExtractionController(CoffeeSessionControl(s),scale,{now})
            controller.weight(WeightReading(0,0))
            check(controller.start(profile,3400,0))
            val before=d.calls.size
            now=100
            receive(when(change) {
                0->"830113F95C007D0F350019006E"
                1->"830113FD5C0A7D0F350019006E"
                2->"830113FD5D007D0F350019006E"
                3->"830113FF5C007D0F350019006E"
                4->"830113DD5C007D0F350019006E"
                else->"830113FD5C007D0F350019006E"
            })
            receive(if(change==1) "400023F02F1C770B00000000000000190321AF"
                else "4000238C2F1C770B00000000000000190321AF")
            controller.weight(WeightReading(0,now))
            if(change==5) {
                check(d.calls.size==before+1);complete()
                check(controller.state==ExtractionState.RUNNING)
            } else {
                check(d.calls.size==before && controller.state==ExtractionState.IDLE &&
                    controller.stopReason==StopReason.START_CONDITIONS_CHANGED)
            }
        }
    }
    case("start context expires and cannot migrate to a new connection or curve") {
        val d=SessionDriver();var now=0L;val s=DeviceSession(DeviceRole.COFFEE,d,{now})
        val profile=StartParameters(false,false,2,7,91,108,false,0,90,65,0,0,350,22,170,0,0)
        fun ready(address:String) {
            s.connect(address,CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
            fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
            complete()
            val(g,t,_)=d.calls.last()
            s.onComplete(g,t,OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
            s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
            s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("4000238C2F1C770B00000000000000190321AF"))
        }
        fun idle()=s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("4000238C2F1C770B00000000000000190321AF"))
        ready("device")
        val context=checkNotNull(s.captureStartContext(profile))
        check(s.startConditionsValid(profile,context))
        check(!s.startConditionsValid(profile.copy(slot=1),context))
        now=4999;idle();check(s.startConditionsValid(profile,context))
        now=5000;idle();check(!s.startConditionsValid(profile,context))
        val oldConnection=checkNotNull(s.captureStartContext(profile))
        ready("device")
        check(!s.startConditionsValid(profile,oldConnection))
        val fresh=checkNotNull(s.captureStartContext(profile))
        ready("other-device")
        check(!s.startConditionsValid(profile,fresh))
        val before=d.calls.size
        var result:OperationResult?=null
        s.startExtraction(profile,fresh){result=it}
        check(result is OperationResult.Failed && d.calls.size==before)
        val control=CoffeeSessionControl(s)
        result=null;control.start(profile){result=it}
        check(result is OperationResult.Failed && d.calls.size==before)
        check(control.prepareStart(profile))
        check(control.startAddress=="other-device")
        control.start(profile){result=it}
        check(d.calls.size==before+1)
        s.disconnect()
        check(control.startAddress=="other-device")
        check(!control.prepareStart(profile) && control.startAddress=="other-device")
    }
    case("connection recovery keeps the original coffee machine and freezes scale changes") {
        for(state in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,ExtractionState.STOP_REQUESTED)) {
            check(!DeviceConnectionGate.mayConnectCoffee(state,"coffee-a","coffee-a"))
            check(!DeviceConnectionGate.mayConnectCoffee(state,"coffee-a","coffee-b"))
            check(!DeviceConnectionGate.mayChangeScale(state))
            check(!DeviceConnectionGate.mayDisconnect(state))
        }
        check(DeviceConnectionGate.mayConnectCoffee(ExtractionState.OUTCOME_UNKNOWN,"coffee-a","coffee-a"))
        check(!DeviceConnectionGate.mayConnectCoffee(ExtractionState.OUTCOME_UNKNOWN,"coffee-a","coffee-b"))
        check(!DeviceConnectionGate.mayConnectCoffee(ExtractionState.OUTCOME_UNKNOWN,null,"coffee-a"))
        check(!DeviceConnectionGate.mayConnectCoffee(ExtractionState.OUTCOME_UNKNOWN,"",""))
        check(!DeviceConnectionGate.mayChangeScale(ExtractionState.OUTCOME_UNKNOWN))
        check(!DeviceConnectionGate.mayDisconnect(ExtractionState.OUTCOME_UNKNOWN))
        for(state in listOf(ExtractionState.IDLE,ExtractionState.ENDED_OBSERVED)) {
            check(DeviceConnectionGate.mayConnectCoffee(state,"coffee-a","coffee-b"))
            check(DeviceConnectionGate.mayChangeScale(state) && DeviceConnectionGate.mayDisconnect(state))
        }
    }
    case("coffee control stop stays with the submitted machine and slot across preparations") {
        val factoryHex="02115C0046005A3C000001F41900C8000000004B"
        val profile=StartParameters(false,false,2,1,92,70,false,0,90,60,0,0,500,25,200,0,0)
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0},legacyVerifiedStartFrames=setOf(factoryHex))
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        fun ready(address:String) {
            s.connect(address,CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
            complete()
            val(g,t,_)=d.calls.last()
            s.onComplete(g,t,OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
            s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
            s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400023F02F1C770B00000000000000190321AF"))
        }
        ready("coffee-a")
        val control=CoffeeSessionControl(s)
        var result:OperationResult?=null
        val initial=d.calls.size
        control.stop{result=it}
        check(result is OperationResult.Failed && d.calls.size==initial)
        check(!control.prepareStart(profile.copy(maximumWaterMl=71)))
        check(control.prepareStart(profile));control.start(profile){};complete()
        ready("coffee-b")
        val switched=d.calls.size
        result=null;control.stop{result=it}
        check(result is OperationResult.Failed && d.calls.size==switched)
        check(control.prepareStart(profile))
        result=null;control.stop{result=it}
        check(result is OperationResult.Failed && d.calls.size==switched)
        ready("coffee-a")
        val reconnected=d.calls.size
        result=null;control.start(profile){result=it}
        check(result is OperationResult.Failed && d.calls.size==reconnected)
        result=null;control.stop{result=it}
        check(result==null && d.calls.size==reconnected+1)
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(hex("0200010000")))
        complete();check(result is OperationResult.Success)
    }
    case("stop cannot follow a cancellation callback onto a replacement connection") {
        for(replacement in listOf("coffee-a","coffee-b")) {
            val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
            fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
            fun ready(address:String) {
                s.connect(address,CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
                complete()
                val(g,t,_)=d.calls.last()
                s.onComplete(g,t,OperationResult.Success(listOf(
                    CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                    CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
                complete();complete()
                s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
                s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
            }
            ready("coffee-a")
            s.writeSetting(MachineSettingChange.Light(true)){}
            s.writeSetting(MachineSettingChange.Light(false)){if(it is OperationResult.Cancelled)ready(replacement)}
            var stopped:OperationResult?=null
            s.stopExtraction(7){stopped=it}
            check(stopped is OperationResult.Failed && s.address==replacement)
            val stop=io.openhoyi.protocol.CoffeeCommands.stop(7).frame.toByteArray()
            check(d.calls.none{(it.third as? GattOperation.Write)?.bytes?.contentEquals(stop)==true})
            s.stopExtraction(7){stopped=it}
            check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(stop))
        }
    }
    case("sleep cancellation observer cannot make old stop cancel the new machine queue") {
        val d=SessionDriver();val s=DeviceSession(DeviceRole.COFFEE,d,{0})
        fun complete(){val(g,t,_)=d.calls.last();s.onComplete(g,t,OperationResult.Success())}
        fun ready(address:String) {
            s.connect(address,CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
            complete()
            val(g,t,_)=d.calls.last()
            s.onComplete(g,t,OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
            s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
            s.onNotification(s.generation,KnownGatt.coffeeNotify,hex("400024BF2F1C770B00000000000000190321AF"))
        }
        ready("coffee-a")
        s.writeSetting(MachineSettingChange.Light(true)){}
        val plan=WeeklySleepSchedule(List(7){WeeklySleepDay(false,SleepDay(0,0,0,0))})
        receiveSleepReadback(s,plan)
        var newSetting:OperationResult?=null
        s.writeSleepSchedule(plan,plan){
            if(it is OperationResult.Unknown) {
                ready("coffee-b")
                s.writeSetting(MachineSettingChange.Light(true)){}
                s.writeSetting(MachineSettingChange.Light(false)){newSetting=it}
            }
        }
        var stopped:OperationResult?=null
        s.stopExtraction(7){stopped=it}
        check(stopped is OperationResult.Failed && newSetting==null && s.address=="coffee-b")
        complete();check(newSetting==null)
        complete();check(newSetting is OperationResult.Success)
        val stop=io.openhoyi.protocol.CoffeeCommands.stop(7).frame.toByteArray()
        check(d.calls.none{(it.third as? GattOperation.Write)?.bytes?.contentEquals(stop)==true})
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
        receiveSleepReadback(s,plan);s.writeSleepSchedule(plan,plan){result=it};complete()
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
        receiveSleepReadback(s,plan);s.writeSleepSchedule(plan,plan){planResult=it}
        val beforeStop=d.calls.size
        s.stopExtraction(7) { }
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
        receiveSleepReadback(s,plan);s.writeSleepSchedule(plan,plan){error("observer failed")}
        s.stopExtraction(7) { }
        complete()
        check((d.calls.last().third as GattOperation.Write).bytes.contentEquals(
            io.openhoyi.protocol.CoffeeCommands.stop(7).frame.toByteArray()))
    }
    return tests
}
