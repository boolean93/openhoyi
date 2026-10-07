package io.openhoyi.session

import io.openhoyi.protocol.*

/** Captured legacy notifications: fake controls, not physical replay or proof of native tare sequencing. */
fun replayChecks():Int {
    val profiles=listOf(
        StartParameters(false,false,2,7,91,108,false,0,90,65,0,0,350,22,170,0,0),
        StartParameters(true,true,3,7,92,70,false,0,20,38,20,0,160,5,400,140,0),
        StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0))
    val lines=object{}.javaClass.getResourceAsStream("/shots.tsv")!!.bufferedReader().readLines()
        .filter{!it.startsWith("#")}.map{it.split('\t')}
    data class Result(val starts:Int,val tares:Int,val stops:List<Long>,val state:ExtractionState)
    fun replay(index:Int,requestDelta:Long=0):Result {
        val replayOrigin=10_000L
        var now=replayOrigin-2000L;var starts=0;var tares=0;val stops=mutableListOf<Long>()
        val coffee=object:CoffeeControl {
            override val ready=true
            override fun prepareStart(parameters:StartParameters)=ready
            override fun startConditionsValid(parameters:StartParameters)=ready
            override fun start(parameters:StartParameters,beforeDispatch:()->Boolean,done:(OperationResult)->Unit){
                if(!runCatching(beforeDispatch).getOrDefault(false)){done(OperationResult.Failed("caller rejected"));return}
                starts++;done(OperationResult.Success())
            }
            override fun stop(done:(OperationResult)->Unit){stops+=now-replayOrigin;done(OperationResult.Success())}
        }
        val scale=object:ScaleControl {
            override val startAllowed=true;override val ready=true
            override fun tare(beforeDispatch:()->Boolean,done:(OperationResult)->Unit){
                if(beforeDispatch()){tares++;done(OperationResult.Success())}
                else done(OperationResult.Failed("guard rejected"))
            }
        }
        val controller=ExtractionController(coffee,scale,{now})
        var started=false
        for(row in lines.filter{it[0].toInt()==index+1}){
            val at=replayOrigin+row[1].toLong()
            if(!started && at>=replayOrigin+requestDelta){
                now=replayOrigin+requestDelta
                check(controller.start(profiles[index],listOf(2700,0,3400)[index],0));started=true
            }
            while(started&&now+50<at){now+=50;controller.tick()}
            now=at
            val bytes=row[4].chunked(2).map{it.toInt(16).toByte()}.toByteArray()
            if(row[2]=="manual-stop")controller.manualStop()
            if(row[2]=="rx"){
                if(row[3]=="scale"){
                    val sample=(BookooCodec.decode(bytes) as DecodeResult.Valid).value
                    controller.weight(WeightReading(sample.weightHundredthsGram,now))
                }else if(started)controller.machineFrame((HoyiCodec.decode(bytes) as DecodeResult.Valid).value,now)
            }
            if(started)controller.tick()
        }
        return Result(starts,tares,stops.toList(),controller.state)
    }
    for(index in 0..1) {
        val result=replay(index)
        check(result.starts==1 && result.tares==1){"shot ${index+1}: $result"}
        if(index==0)check(result.stops==listOf(44098L)){"manual stop: $result"}
        else check(result.stops.isEmpty()){"machine flow end must not synthesize stop: $result"}
        println("PASS recorded shot ${index+1}: one start, one tare, stops=${result.stops}")
    }
    // At legacy tx t=0, the machine already starts. Valve-open arrives at41ms before
    // post-tare zero at56ms. Native preflight cannot legitimately start again afterward.
    val original=replay(2)
    check(original.starts==0 && original.tares==1 && original.stops.isEmpty() && original.state==ExtractionState.IDLE) {
        "legacy post-start zero cannot authorize native preflight: $original"
    }
    println("PASS raw recorded shot 3: pre-dispatch valve activity rejects native start, no retry or stop")
    // Explicit synthetic control timing: request preflight just before the last captured
    // pre-start near-zero observation. Every captured row/byte/timestamp is retained.
    // Fake tare completion does not prove that observation was produced by a physical tare.
    val zero=lines.filter{it[0]=="3" && it[1].toLong()<0 && it[2]=="rx" && it[3]=="scale"}.last { row->
        val bytes=row[4].chunked(2).map{it.toInt(16).toByte()}.toByteArray()
        kotlin.math.abs((BookooCodec.decode(bytes) as DecodeResult.Valid).value.weightHundredthsGram)<=50
    }
    val requestAt=zero[1].toLong()-1
    check(requestAt==-36L)
    val adapted=replay(2,requestAt)
    check(adapted.starts==1 && adapted.tares==1 && adapted.stops.size==1 && adapted.stops.single() in 17500L..18500L) {
        "native preflight timing + recorded weights: $adapted"
    }
    println("PASS adapted native preflight + recorded shot 3: requestDelta=$requestAt, stops=${adapted.stops}, no physical tare proof")
    return 4
}
