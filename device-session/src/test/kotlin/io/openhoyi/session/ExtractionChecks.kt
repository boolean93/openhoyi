package io.openhoyi.session
import io.openhoyi.protocol.StartParameters
private class CoffeeFake:CoffeeControl {
    override var ready=true;var starts=0;var stops=0
    override fun start(parameters:StartParameters,done:(OperationResult)->Unit){starts++;done(OperationResult.Success())}
    override fun stop(done:(OperationResult)->Unit){stops++;done(OperationResult.Success())}
}
private class ScaleFake:ScaleControl {
    override var ready=true;var tares=0
    override fun tare(done:(OperationResult)->Unit){tares++;done(OperationResult.Success())}
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
    case("cancelled unsent start can settle on fresh idle after stop completion") {
        var pending:((OperationResult)->Unit)?=null;var now=0L
        val coffee=object:CoffeeControl {
            override val ready=true
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
