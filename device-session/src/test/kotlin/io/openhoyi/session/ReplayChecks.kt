package io.openhoyi.session

import io.openhoyi.protocol.*

fun replayChecks():Int {
    val profiles=listOf(
        StartParameters(false,false,2,7,91,108,false,0,90,65,0,0,350,22,170,0,0),
        StartParameters(true,true,3,7,92,70,false,0,20,38,20,0,160,5,400,140,0),
        StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0))
    val lines=object{}.javaClass.getResourceAsStream("/shots.tsv")!!.bufferedReader().readLines().filter{!it.startsWith("#")}.map{it.split('\t')}
    repeat(3){ index->
        var now=-2000L;var starts=0;var tares=0;val stops=mutableListOf<Long>()
        val coffee=object:CoffeeControl {
            override val ready=true
            override fun start(parameters:StartParameters,done:(OperationResult)->Unit){starts++;done(OperationResult.Success())}
            override fun stop(done:(OperationResult)->Unit){stops+=now;done(OperationResult.Success())}
        }
        val scale=object:ScaleControl{override val ready=true;override fun tare(done:(OperationResult)->Unit){tares++;done(OperationResult.Success())}}
        val controller=ExtractionController(coffee,scale,{now})
        var started=false
        for(row in lines.filter{it[0].toInt()==index+1}){
            val at=row[1].toLong()
            if(!started && at>=0){now=0;check(controller.start(profiles[index],listOf(2700,0,3400)[index],0));started=true}
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
        check(starts==1 && tares==1){"shot ${index+1}: starts=$starts tares=$tares"}
        when(index){
            0->check(stops==listOf(44098L)){"manual stop: $stops"}
            1->check(stops.isEmpty()){"machine flow end must not synthesize stop: $stops"}
            2->check(stops.size==1&&stops.single() in 17500L..18500L){"weight stop: $stops"}
        }
        println("PASS recorded shot ${index+1}: one start, one tare, stops=$stops")
    }
    return 3
}
