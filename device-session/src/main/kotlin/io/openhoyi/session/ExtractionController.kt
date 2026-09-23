package io.openhoyi.session

import io.openhoyi.protocol.StartParameters
import io.openhoyi.protocol.CoffeeCommands
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry

interface CoffeeControl {
    val ready:Boolean
    fun start(parameters:StartParameters,done:(OperationResult)->Unit)
    fun stop(done:(OperationResult)->Unit)
}
interface ScaleControl {val ready:Boolean;fun tare(done:(OperationResult)->Unit)}
class CoffeeSessionControl(private val session:DeviceSession):CoffeeControl {
    init{require(session.role==DeviceRole.COFFEE)}
    private var activeSlot=7
    override val ready get()=session.state==DeviceState.READY
    override fun start(parameters:StartParameters,done:(OperationResult)->Unit){
        activeSlot=parameters.slot
        session.startExtraction(parameters,done)
    }
    override fun stop(done:(OperationResult)->Unit)=session.stopExtraction(activeSlot,done)
}
class ScaleSessionControl(private val session:DeviceSession):ScaleControl {
    init{require(session.role==DeviceRole.BOOKOO)}
    override val ready get()=session.state==DeviceState.READY
    override fun tare(done:(OperationResult)->Unit)=session.tare(done)
}
enum class ExtractionState { IDLE, STARTING, RUNNING, STOP_REQUESTED, ENDED_OBSERVED, OUTCOME_UNKNOWN }
/** Caller forwards fresh decoded weights and explicit machine-state observations; no Activity timers. */
class ExtractionController(private val coffee:CoffeeControl,private val scale:ScaleControl,private val clock:()->Long) {
    var state=ExtractionState.IDLE;private set
    var stopReason:StopReason?=null;private set
    private val policy=ExtractionPolicy()
    private var serial=0L
    private var started=0L
    private var latest:WeightReading?=null
    private var lastActiveFrame:Long?=null
    private var startNotSubmitted=false
    private var stopWrittenAt:Long?=null
    private var target=0
    private var tareSent=false
    private var tareWrittenAt:Long?=null
    private var tareConfirmed=false
    fun start(parameters:StartParameters,targetHundredthsGram:Int,compensationHundredthsGram:Int):Boolean {
        if(state!=ExtractionState.IDLE&&state!=ExtractionState.ENDED_OBSERVED)return false
        if(runCatching { CoffeeCommands.start(parameters) }.isFailure)return false
        if(targetHundredthsGram !in 0..600_000 || compensationHundredthsGram !in -10_000..10_000 || (targetHundredthsGram>0 && compensationHundredthsGram>=targetHundredthsGram))return false
        val now=clock();val sample=latest
        if(!coffee.ready)return false
        if(targetHundredthsGram>0&&(!scale.ready||sample==null||sample.receivedAtMs>now||now-sample.receivedAtMs>1500))return false
        val id=++serial
        policy.begin(id,targetHundredthsGram,compensationHundredthsGram,now)
        started=now;lastActiveFrame=null;startNotSubmitted=false;stopWrittenAt=null;target=targetHundredthsGram;tareSent=false;tareWrittenAt=null;tareConfirmed=false;stopReason=null
        state=ExtractionState.STARTING
        coffee.start(parameters){result ->
            if(id!=serial)return@start
            if(state==ExtractionState.STOP_REQUESTED){
                if(result is OperationResult.Cancelled)startNotSubmitted=true
                return@start
            }
            if(state!=ExtractionState.STARTING)return@start
            state=when(result){is OperationResult.Success->ExtractionState.RUNNING;is OperationResult.Failed,is OperationResult.Cancelled->{policy.end(id);ExtractionState.IDLE};is OperationResult.Unknown->ExtractionState.OUTCOME_UNKNOWN}
        }
        return true
    }
    fun weight(reading:WeightReading) {
        val now=clock()
        if(!scale.ready||reading.receivedAtMs>now||now-reading.receivedAtMs>1500||reading.hundredthsGram !in -50_000..600_000)return
        if(latest!=null&&reading.receivedAtMs<=latest!!.receivedAtMs)return
        latest=reading
        if(state!=ExtractionState.RUNNING)return
        val written=tareWrittenAt
        if(!tareConfirmed&&written!=null&&reading.receivedAtMs>written&&kotlin.math.abs(reading.hundredthsGram)<=50){
            tareConfirmed=true;policy.confirmTare(serial,reading.hundredthsGram,reading.receivedAtMs)
        }
        policy.sample(serial,reading,now)?.let{requestStop(it)}
    }
    fun tick() {
        if(state !in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,ExtractionState.STOP_REQUESTED))return
        if(!coffee.ready){state=ExtractionState.OUTCOME_UNKNOWN;return}
        if(state!=ExtractionState.RUNNING)return
        val now=clock();val id=serial
        if(!tareSent&&scale.ready&&now-started>=1500){
            tareSent=true
            scale.tare { result->if(id==serial&&state==ExtractionState.RUNNING&&result is OperationResult.Success)tareWrittenAt=clock() }
        }
        if(target>0&&!scale.ready){requestStop(StopReason.SCALE_UNAVAILABLE);return}
        policy.checkHealth(serial,now)?.let{requestStop(it)}
    }
    fun manualStop(){
        if(state==ExtractionState.OUTCOME_UNKNOWN&&coffee.ready)requestStop(StopReason.MANUAL,true)
        else if(state==ExtractionState.RUNNING||state==ExtractionState.STARTING)policy.manualStop(serial)?.let{requestStop(it)}
    }
    private fun requestStop(reason:StopReason,explicitRetry:Boolean=false){
        if(state==ExtractionState.STOP_REQUESTED||(state==ExtractionState.OUTCOME_UNKNOWN&&!explicitRetry))return
        stopReason=reason;state=ExtractionState.STOP_REQUESTED
        val id=serial
        coffee.stop { result->
            if(id==serial&&state==ExtractionState.STOP_REQUESTED){
                if(result is OperationResult.Success)stopWrittenAt=clock()
                else state=ExtractionState.OUTCOME_UNKNOWN
            }
        }
    }
    fun machineFrame(frame:HoyiMessage,receivedAtMs:Long) {
        val now=clock()
        if(receivedAtMs<started||receivedAtMs>now||now-receivedAtMs>1500)return
        if(frame is ExtractionTelemetry && frame.valveOpen)lastActiveFrame=receivedAtMs
        val active=lastActiveFrame
        val stoppedUnsentStart=active==null&&startNotSubmitted&&stopWrittenAt?.let{receivedAtMs>it}==true
        if(frame is IdleTelemetry && ((active!=null && receivedAtMs-active>2800)||stoppedUnsentStart))machineIdle()
    }
    /** Only for a host with independent, fresh machine-idle evidence; never silence/timeout. */
    fun machineIdle(){
        if(state in listOf(ExtractionState.RUNNING,ExtractionState.STOP_REQUESTED,ExtractionState.OUTCOME_UNKNOWN)){
            policy.end(serial);state=ExtractionState.ENDED_OBSERVED
        }
    }
    fun scaleDisconnected(){latest=null;if(target>0&&state==ExtractionState.RUNNING)requestStop(StopReason.SCALE_UNAVAILABLE)}
}
