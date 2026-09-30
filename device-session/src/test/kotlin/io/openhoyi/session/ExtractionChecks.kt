package io.openhoyi.session
import io.openhoyi.protocol.StartParameters
private class CoffeeFake:CoffeeControl {
    override var ready=true;var starts=0;var stops=0;var permitValid=true
    override fun prepareStart(parameters:StartParameters)=ready
    override fun startConditionsValid(parameters:StartParameters)=ready && permitValid
    override fun start(parameters:StartParameters,done:(OperationResult)->Unit){starts++;done(OperationResult.Success())}
    override fun stop(done:(OperationResult)->Unit){stops++;done(OperationResult.Success())}
}
private class ScaleFake:ScaleControl {
    override var ready=true;var tares=0
    override fun tare(done:(OperationResult)->Unit){tares++;done(OperationResult.Success())}
}
private class UnknownStartCoffee:CoffeeControl {
    override var ready=true
    var starts=0;var stops=0
    var stopResult:OperationResult=OperationResult.Success()
    var deferStop=false
    var stopCallback:((OperationResult)->Unit)?=null
    override fun prepareStart(parameters:StartParameters)=ready
    override fun startConditionsValid(parameters:StartParameters)=ready
    override fun start(parameters:StartParameters,done:(OperationResult)->Unit){
        starts++;done(OperationResult.Unknown("accepted start without outcome"))
    }
    override fun stop(done:(OperationResult)->Unit){stops++;if(deferStop)stopCallback=done else done(stopResult)}
}
private val profile=StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)
fun extractionChecks():Int {
    var count=0
    fun case(name:String,f:()->Unit){f();count++;println("PASS $name")}
    case("weight shot tares before sending the machine start frame") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L
        val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(0,now))
        check(c.start(profile,3400,0))
        check(scale.tares==1 && coffee.starts==0)
        now=100;c.weight(WeightReading(0,now))
        check(coffee.starts==1 && c.state==ExtractionState.RUNNING)
    }
    case("zero arriving after tare deadline cannot start before the next tick") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L
        val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(0,0));check(c.start(profile,3400,0))
        now=5000;c.weight(WeightReading(0,now))
        check(coffee.starts==0 && coffee.stops==0 && c.state==ExtractionState.IDLE)
    }
    case("rejected conditions before immediate start leave idle without a machine command") {
        val coffee=CoffeeFake();coffee.permitValid=false
        val c=ExtractionController(coffee,ScaleFake(),{0})
        c.start(profile,0,0)
        check(c.state==ExtractionState.IDLE && coffee.starts==0 && coffee.stops==0)
    }
    case("unconfirmed preflight tare never starts the machine") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L
        val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(-34_400,now))
        check(c.start(profile,3400,0))
        now=100;c.weight(WeightReading(-34_300,now))
        now=5_100;c.tick()
        check(coffee.starts==0 && coffee.stops==0 && c.state==ExtractionState.IDLE)
    }
    case("cancelled preflight ignores late tare callback and sends no machine command") {
        val coffee=CoffeeFake();var callback:((OperationResult)->Unit)?=null;var now=0L
        val scale=object:ScaleControl {
            override val ready=true
            override fun tare(done:(OperationResult)->Unit){callback=done}
        }
        val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(0,now));check(c.start(profile,3400,0))
        c.manualStop();callback!!(OperationResult.Success())
        now=100;c.weight(WeightReading(0,now));c.tick()
        check(c.state==ExtractionState.IDLE && coffee.starts==0 && coffee.stops==0)
    }
    case("scale loss during preflight cancels without a machine stop") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L
        val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(0,now));check(c.start(profile,3400,0))
        scale.ready=false;c.scaleDisconnected();now=100;c.tick()
        check(c.state==ExtractionState.IDLE && coffee.starts==0 && coffee.stops==0)
    }
    case("integration zero observation precedes weight stop and no duplicate start or stop") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L;val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(0,0));check(c.start(profile,3400,0));check(!c.start(profile,3400,0))
        check(scale.tares==1 && coffee.starts==0)
        now=100;c.weight(WeightReading(0,now));check(coffee.starts==1)
        now=1600;c.weight(WeightReading(3500,now));check(coffee.stops==0)
        now=7100;c.weight(WeightReading(3430,now));c.weight(WeightReading(3440,now));check(coffee.stops==1)
        check(c.state==ExtractionState.STOP_REQUESTED)
        c.machineIdle();check(c.state==ExtractionState.ENDED_OBSERVED)
    }
    case("machine brew timer gates target stop after a delayed start") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L
        val c=ExtractionController(coffee,scale,{now})
        val raw=io.openhoyi.protocol.ByteFrame(byteArrayOf())
        c.weight(WeightReading(0,0));check(c.start(profile,3400,0))
        now=100;c.weight(WeightReading(0,now));check(coffee.starts==1)
        now=7_200
        c.machineFrame(io.openhoyi.protocol.ExtractionTelemetry(1,3,20,1,9200,20,64,0,raw),now)
        c.weight(WeightReading(3_450,now))
        check(coffee.stops==0)
        now=7_300
        c.machineFrame(io.openhoyi.protocol.ExtractionTelemetry(1,7,20,1,9200,20,64,0,raw),now)
        c.weight(WeightReading(3_460,now))
        check(coffee.stops==1 && c.stopReason==StopReason.TARGET_WEIGHT)
    }
    case("stale scale prevents start without any write") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L;val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(0,0));now=2000;check(!c.start(profile,3400,0));check(coffee.starts==0)
    }
    case("tare success without zero aborts before machine start") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L;val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(0,0));check(c.start(profile,3400,0))
        now=5100;c.tick();c.tick()
        check(coffee.starts==0 && coffee.stops==0 && c.stopReason==StopReason.TARE_UNCONFIRMED)
    }
    case("link loss makes outcome unknown and never replays start") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L;val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(0,0));check(c.start(profile,3400,0));now=100;c.weight(WeightReading(0,now));coffee.ready=false;c.tick()
        check(c.state==ExtractionState.OUTCOME_UNKNOWN);coffee.ready=true;c.tick();check(coffee.starts==1)
        check(!c.start(profile,3400,0))
    }
    case("invalid start parameters leave controller reusable") {
        val coffee=CoffeeFake();val scale=ScaleFake();val c=ExtractionController(coffee,scale,{0})
        c.weight(WeightReading(0,0))
        check(!c.start(profile.copy(segmentCount=5),3400,0));check(coffee.starts==0)
        check(c.start(profile,3400,0))
    }
    case("idle before observed active extraction cannot settle a newly submitted start") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L;val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(0,0));check(c.start(profile,3400,0));now=10;c.weight(WeightReading(0,now))
        val raw=io.openhoyi.protocol.ByteFrame(byteArrayOf())
        val idle=io.openhoyi.protocol.IdleTelemetry(9200,12000,10,10,0,0,0,0,raw)
        c.machineFrame(idle,now);check(c.state==ExtractionState.RUNNING)
        c.machineFrame(io.openhoyi.protocol.ExtractionTelemetry(8,1,20,1,9200,20,64,0,raw),now)
        now=3000;c.machineFrame(idle,now);check(c.state==ExtractionState.ENDED_OBSERVED)
    }
    case("explicit manual stop works after uncertain disconnect and reconnect") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L;val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(0,0));check(c.start(profile,3400,0));now=10;c.weight(WeightReading(0,now));coffee.ready=false;c.tick()
        coffee.ready=true;c.manualStop();c.manualStop()
        check(coffee.stops==1 && coffee.starts==1 && c.state==ExtractionState.STOP_REQUESTED)
    }
    case("unconfirmed stop becomes unknown and only explicit retry sends another stop") {
        val coffee=CoffeeFake();val scale=ScaleFake();var now=0L;val c=ExtractionController(coffee,scale,{now})
        c.weight(WeightReading(0,now));check(c.start(profile,3400,0))
        now=10;c.weight(WeightReading(0,now));c.manualStop()
        check(coffee.stops==1 && c.state==ExtractionState.STOP_REQUESTED)
        now=5_009;c.tick();check(c.state==ExtractionState.STOP_REQUESTED && coffee.stops==1)
        now=5_010;c.tick();check(c.state==ExtractionState.OUTCOME_UNKNOWN && coffee.stops==1)
        c.tick();check(coffee.stops==1 && !c.start(profile,3400,0))
        c.manualStop();check(coffee.stops==2 && c.state==ExtractionState.STOP_REQUESTED)
        now=5_011;c.tick();check(c.state==ExtractionState.STOP_REQUESTED)
        now=10_010;c.tick();check(c.state==ExtractionState.OUTCOME_UNKNOWN && coffee.stops==2)
    }
    case("unknown start without valve evidence settles only after manual stop and continuous idle") {
        val coffee=UnknownStartCoffee();var now=0L
        val c=ExtractionController(coffee,ScaleFake(),{now})
        val idle=io.openhoyi.protocol.IdleTelemetry(9200,12000,10,10,0,0,0,0,io.openhoyi.protocol.ByteFrame(byteArrayOf()))
        check(c.start(profile,0,0) && c.state==ExtractionState.OUTCOME_UNKNOWN)
        for(at in listOf(100L,1100L,2100L,3100L)) {now=at;c.machineFrame(idle,now)}
        check(c.state==ExtractionState.OUTCOME_UNKNOWN && coffee.stops==0)
        c.manualStop()
        for(at in listOf(3200L,4200L,5200L,6000L)) {
            now=at;c.machineFrame(idle,now)
            check(c.state==ExtractionState.STOP_REQUESTED)
        }
        now=6100;c.machineFrame(idle,now)
        check(c.state==ExtractionState.ENDED_OBSERVED && coffee.starts==1 && coffee.stops==1)
    }
    case("idle before stop callback cannot count toward recovery confirmation") {
        val coffee=UnknownStartCoffee();coffee.deferStop=true;var now=0L
        val c=ExtractionController(coffee,ScaleFake(),{now})
        val idle=io.openhoyi.protocol.IdleTelemetry(9200,12000,10,10,0,0,0,0,io.openhoyi.protocol.ByteFrame(byteArrayOf()))
        check(c.start(profile,0,0));c.manualStop()
        for(at in listOf(100L,1100L,2100L,3100L)) {now=at;c.machineFrame(idle,now)}
        check(c.state==ExtractionState.STOP_REQUESTED)
        coffee.stopCallback!!(OperationResult.Success())
        c.machineFrame(idle,now)
        for(at in listOf(3200L,4200L,5200L,6000L)) {
            now=at;c.machineFrame(idle,now);c.tick()
            check(c.state==ExtractionState.STOP_REQUESTED)
        }
        now=6100;c.machineFrame(idle,now)
        check(c.state==ExtractionState.ENDED_OBSERVED && coffee.stops==1)
    }
    case("old stop callback cannot confirm or fail a newer explicit retry") {
        for(oldSuccess in listOf(false,true)) {
            val coffee=UnknownStartCoffee();coffee.deferStop=true;var now=0L
            val c=ExtractionController(coffee,ScaleFake(),{now})
            val idle=io.openhoyi.protocol.IdleTelemetry(9200,12000,10,10,0,0,0,0,io.openhoyi.protocol.ByteFrame(byteArrayOf()))
            check(c.start(profile,0,0));c.manualStop()
            val oldCallback=checkNotNull(coffee.stopCallback)
            now=100;coffee.ready=false;c.tick();coffee.ready=true;c.manualStop()
            val newCallback=checkNotNull(coffee.stopCallback)
            oldCallback(if(oldSuccess) OperationResult.Success() else OperationResult.Unknown("old stop outcome"))
            check(c.state==ExtractionState.STOP_REQUESTED)
            for(at in listOf(200L,1200L,2200L,3200L)) {now=at;c.machineFrame(idle,now)}
            check(c.state==ExtractionState.STOP_REQUESTED && coffee.stops==2)
            newCallback(OperationResult.Success())
            for(at in listOf(3300L,4300L,5300L)) {now=at;c.machineFrame(idle,now)}
            check(c.state==ExtractionState.STOP_REQUESTED)
            now=6300;c.machineFrame(idle,now)
            check(c.state==ExtractionState.ENDED_OBSERVED && coffee.starts==1 && coffee.stops==2)
        }
    }
    case("manual stop idle recovery does not bridge stale gaps phase frames or disconnects") {
        for(interruption in 0..2) {
            val coffee=UnknownStartCoffee();var now=0L
            val c=ExtractionController(coffee,ScaleFake(),{now})
            val raw=io.openhoyi.protocol.ByteFrame(byteArrayOf())
            val idle=io.openhoyi.protocol.IdleTelemetry(9200,12000,10,10,0,0,0,0,raw)
            check(c.start(profile,0,0));c.manualStop()
            now=100;c.machineFrame(idle,now)
            when(interruption) {
                0 -> now=2000
                1 -> {now=1000;c.machineFrame(io.openhoyi.protocol.ExtractionTelemetry(8,1,20,1,9200,20,0,0,raw),now);now=1200}
                else -> {now=500;coffee.ready=false;c.tick();coffee.ready=true;now=1200}
            }
            val first=now
            for(offset in listOf(0L,1000L,2000L)) {
                now=first+offset;c.machineFrame(idle,now)
                check(c.state!=ExtractionState.ENDED_OBSERVED)
            }
            now=first+3000;c.machineFrame(idle,now)
            check(c.state==ExtractionState.ENDED_OBSERVED && coffee.starts==1 && coffee.stops==1)
        }
    }
    case("failed stop and repeated or future idle cannot confirm unknown start recovery") {
        val coffee=UnknownStartCoffee();coffee.stopResult=OperationResult.Unknown("stop not confirmed")
        var now=0L;val c=ExtractionController(coffee,ScaleFake(),{now})
        val idle=io.openhoyi.protocol.IdleTelemetry(9200,12000,10,10,0,0,0,0,io.openhoyi.protocol.ByteFrame(byteArrayOf()))
        check(c.start(profile,0,0));c.manualStop()
        for(at in listOf(100L,1100L,2100L,3100L)) {now=at;c.machineFrame(idle,now)}
        check(c.state==ExtractionState.OUTCOME_UNKNOWN)
        coffee.stopResult=OperationResult.Success();c.manualStop()
        now=3200;c.machineFrame(idle,now)
        now=4200;c.machineFrame(idle,3200);c.machineFrame(idle,7200)
        now=5200;c.machineFrame(idle,4200)
        now=6200;c.machineFrame(idle,now)
        check(c.state==ExtractionState.STOP_REQUESTED)
        now=7200;c.machineFrame(idle,now)
        check(c.state==ExtractionState.STOP_REQUESTED)
        now=8200;c.tick();c.machineFrame(idle,now)
        check(c.state==ExtractionState.OUTCOME_UNKNOWN && coffee.stops==2)
        now=9200;c.machineFrame(idle,now)
        check(c.state==ExtractionState.ENDED_OBSERVED && coffee.stops==2)
    }
    case("cancelled unsent start can settle on fresh idle after stop completion") {
        var pending:((OperationResult)->Unit)?=null;var now=0L
        val coffee=object:CoffeeControl {
            override val ready=true
            override fun prepareStart(parameters:StartParameters)=ready
    override fun startConditionsValid(parameters:StartParameters)=ready
    override fun start(parameters:StartParameters,done:(OperationResult)->Unit){pending=done}
            override fun stop(done:(OperationResult)->Unit){pending!!(OperationResult.Cancelled("superseded"));done(OperationResult.Success())}
        }
        val c=ExtractionController(coffee,ScaleFake(),{now});c.weight(WeightReading(0,0));check(c.start(profile,3400,0));now=10;c.weight(WeightReading(0,now));c.manualStop()
        now=100;c.machineFrame(io.openhoyi.protocol.IdleTelemetry(9200,12000,10,10,0,0,0,0,io.openhoyi.protocol.ByteFrame(byteArrayOf())),now)
        check(c.state==ExtractionState.ENDED_OBSERVED);check(c.start(profile,3400,0))
    }
    case("active evidence prevents cancelled start from settling early") {
        var pending:((OperationResult)->Unit)?=null;var now=0L
        val coffee=object:CoffeeControl {
            override val ready=true
            override fun prepareStart(parameters:StartParameters)=ready
    override fun startConditionsValid(parameters:StartParameters)=ready
    override fun start(parameters:StartParameters,done:(OperationResult)->Unit){pending=done}
            override fun stop(done:(OperationResult)->Unit){pending!!(OperationResult.Cancelled("superseded"));done(OperationResult.Success())}
        }
        val c=ExtractionController(coffee,ScaleFake(),{now});c.weight(WeightReading(0,0));check(c.start(profile,3400,0));now=10;c.weight(WeightReading(0,now));c.manualStop()
        now=50;c.machineFrame(io.openhoyi.protocol.ExtractionTelemetry(8,1,20,1,9200,20,64,0,io.openhoyi.protocol.ByteFrame(byteArrayOf())),now)
        now=100;c.machineFrame(io.openhoyi.protocol.IdleTelemetry(9200,12000,10,10,0,0,0,0,io.openhoyi.protocol.ByteFrame(byteArrayOf())),now)
        check(c.state==ExtractionState.STOP_REQUESTED);check(!c.start(profile,3400,0))
    }
    return count
}
