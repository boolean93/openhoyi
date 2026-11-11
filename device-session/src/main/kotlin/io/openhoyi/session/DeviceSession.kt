package io.openhoyi.session

import io.openhoyi.protocol.*
import java.time.LocalDateTime

private const val PROTOCOL_TIMEOUT_REASON = "protocol initialization timeout"
enum class DeviceRole { COFFEE, BOOKOO }
enum class DeviceState { DISCONNECTED, CONNECTING, DISCOVERING, SUBSCRIBING, INITIALIZING, SYNCHRONIZING, READY, UNSUPPORTED, FAILED }
class CoffeeAuthentication(val time:LocalDateTime,private val password:String) {
    fun encode()=encodeAt(time)
    internal fun encodeAt(currentTime:LocalDateTime)=CoffeeCommands.authenticate(currentTime,password)
    internal fun atTime(currentTime:LocalDateTime)=CoffeeAuthentication(currentTime,password)
    override fun toString()="CoffeeAuthentication([REDACTED])"
}
object KnownGatt {
    val coffeeWrite=Endpoint("55535343-fe7d-4ae5-8fa9-9fafd205e455","49535343-8841-43f4-a8d4-ecbe34729bb3")
    val coffeeNotify=Endpoint(coffeeWrite.service,"49535343-1e4d-4bd9-ba61-23c647249616")
    val bookooWrite=Endpoint("00000ffe-0000-1000-8000-00805f9b34fb","0000ff12-0000-1000-8000-00805f9b34fb")
    val bookooNotify=Endpoint(bookooWrite.service,"0000ff11-0000-1000-8000-00805f9b34fb")
}
/** All methods run on one owner thread. Host ticks with a monotonic clock, not Activity lifecycle. */
class DeviceSession(val role:DeviceRole,driver:GattDriver,private val clock:()->Long,
    private val stateChanged:(DeviceState)->Unit={}, private val coffeeFrame:(HoyiMessage,Long)->Unit={_,_->},
    private val diagnostic:(String)->Unit={},
    legacyVerifiedStartFrames:Set<String> = emptySet(),
    val scaleAdapter:ScaleProtocolAdapter = BookooScaleProtocolAdapter,
    private val scaleObservation:(ScaleObservation)->Unit = {},
    private val customPressureStartPermit:(StartParameters)->Boolean = {false},
    private val customCurveStartPermit:(StartParameters)->Boolean = {false},
    private val wallClock:()->LocalDateTime = LocalDateTime::now) {
    init {
        if(role!=DeviceRole.COFFEE) {
            if(scaleAdapter.readOnlyTransport) require(!scaleAdapter.transportVerified && !scaleAdapter.documentedTransport &&
                scaleAdapter.notifyEndpoint!=null && scaleAdapter.writeEndpoint==null &&
                scaleAdapter.initializationCommands.isEmpty() && scaleAdapter.tareCommand==null && scaleAdapter.heartbeat==null &&
                !scaleAdapter.capabilities.tare && !scaleAdapter.capabilities.validatedWeightControl &&
                !scaleAdapter.capabilities.protocolSupportedWeightControl) {
                "Read-only scale adapter cannot expose control or characteristic writes"
            } else {
                require(!scaleAdapter.capabilities.protocolSupportedWeightControl ||
                    (scaleAdapter.documentedTransport && scaleAdapter.capabilities.tare && scaleAdapter.tareCommand!=null)) {
                    "Protocol-supported target control requires documented writable tare transport"
                }
                require(!scaleAdapter.documentedTransport || (!scaleAdapter.transportVerified &&
                    !scaleAdapter.capabilities.validatedWeightControl && !scaleAdapter.capabilities.automaticTare)) {
                    "Documented scale transport is not hardware validated or approved for automatic tare"
                }
                require((scaleAdapter.transportVerified || scaleAdapter.documentedTransport) && scaleAdapter.writeEndpoint!=null &&
                    scaleAdapter.notifyEndpoint!=null) { "Offline scale candidates cannot connect" }
                require((scaleAdapter.initializationCommands.firstOrNull()?.delayMs ?: 0)>=0 &&
                    scaleAdapter.initializationCommands.zipWithNext().all {(a,b)->b.delayMs>a.delayMs}) { "Invalid scale initialization timing" }
                require(scaleAdapter.heartbeat?.intervalMs?.let {it in 1000..30_000} != false) { "Invalid scale heartbeat interval" }
            }
        }
    }
    val scaleCapabilities:ScaleCapabilities get()=scaleAdapter.capabilities
    private val additionalStartFrames = legacyVerifiedStartFrames.toSet().also { frames ->
        require(frames.size <= 1200 && frames.all { hex ->
            hex.matches(Regex("02[0-9A-F]{38}")) &&
                hex.chunked(2).map { it.toInt(16) }.reduce(Int::xor) == 0 &&
                (hex.substring(2, 4).toInt(16) and 7).let { it in 1..5 || it == 7 }
        }) { "Invalid legacy start-frame permit" }
    }
    private var boundScaleAdapter:ScaleProtocolAdapter=scaleAdapter
    private val queue=GattQueue(GuardedGattDriver(role,driver,scaleAdapter){boundScaleAdapter},clock){fail(it)}
    // Request order also detects cancellation before a new GATT generation exists.
    private var connectionRequest=0L
    val generation:Long get()=queue.generation
    val transportCloseFailure: Exception? get()=queue.transportCloseFailure
    var state=DeviceState.DISCONNECTED;private set
    /** Classification of the most recent FAILED; display-only, never a control permit. */
    var lastFailure:CoffeeSessionFailure?=null;private set
    private var activeAddress:String?=null
    private var coffeeAuthentication:CoffeeAuthentication?=null
    private class BoilerReadback(val generation:Long,val address:String,val authentication:CoffeeAuthentication,val startedAt:Long) {
        var automaticStarted=false
        var syncAttempts=0
        var syncDispatched=false
        var automaticCancelled=false
        var reconnectUsed=false
        val deadline get()=startedAt+6000
    }
    private class BoilerRefreshTask(val manual:Boolean,val deadline:Long,val due:List<Long>,
        val beforeDispatch:()->Boolean,val outcome:(Int,OperationResult)->Unit) {
        var next=0
        var inFlight=false
        var waitAfterIdle:Long?=null
        var lastDispatchAt:Long?=null
    }
    private var boilerReadback:BoilerReadback?=null
    private var boilerRefreshTask:BoilerRefreshTask?=null
    private var idleSerial=0L
    private class BoilerReconnectLimit(val generation:Long,val address:String,val deadline:Long,val mayExpire:()->Boolean)
    private var boilerReconnectLimit:BoilerReconnectLimit?=null
    val boilerReadbackReconnecting:Boolean get()=boilerReconnectLimit?.let {
        generation==it.generation && address==it.address && state!=DeviceState.READY && clock()<it.deadline
    }==true
    val address:String? get()=activeAddress
    val observedFirmware:CoffeeFirmware? get()=lastSettings?.let {
        CoffeeFirmware(it.firmwareMajor,it.firmwareMinor,it.firmwarePatch)
    }
    private var lastIdle:IdleTelemetry?=null
    private var lastIdleAtMs:Long?=null
    private var lastSettingsAtMs:Long?=null
    private var lastSettings:Settings?=null
    /** Original monotonic receive time of authenticated Settings, never its replay time. */
    val settingsObservedAtMs:Long? get()=lastSettingsAtMs
    private var sleepFirst:SleepPart?=null
    private var sleepSecond:SleepPart?=null
    private var sleepFirstAtMs:Long?=null
    private var sleepSecondAtMs:Long?=null
    private fun clearSleepReadback() {
        sleepFirst=null;sleepSecond=null;sleepFirstAtMs=null;sleepSecondAtMs=null
    }
    private var notifyEndpoint=if(role==DeviceRole.COFFEE)KnownGatt.coffeeNotify else scaleAdapter.notifyEndpoint!!
    private var writeEndpoint=if(role==DeviceRole.COFFEE)KnownGatt.coffeeWrite else scaleAdapter.writeEndpoint
    private var withResponse=true
    private class InitialAuthentication(val generation:Long,val stageDeadline:Long) {
        var dispatchDeadline:Long?=null
        var settings:Pair<Settings,Long>?=null
    }
    private var initialAuthentication:InitialAuthentication?=null
    private var stageDeadline=0L
    private var initNext=0
    private var initDue=0L
    private var initBusy=false
    private var heartbeatDue:Long?=null
    private var heartbeatBusy=false
    private var scaleDecoder=scaleAdapter.newDecoder() ?: scaleAdapter::decode
    private class ScaleTare(val callback:(OperationResult)->Unit) {
        var dispatched=false
        var written:OperationResult?=null
        var acknowledgement:Boolean?=null
        var deadline=Long.MAX_VALUE
    }
    private var scaleTare:ScaleTare?=null
    private fun finishScaleTare(request:ScaleTare,result:OperationResult) {
        if(scaleTare!==request)return
        scaleTare=null
        request.callback(result)
    }
    private fun completeScaleTare(request:ScaleTare) {
        // Notifications and GATT callbacks can arrive before a delayed tick. Never extend an
        // expired acknowledgement window just because the timeout task has not run yet.
        if(request.dispatched && clock()>=request.deadline) {
            finishScaleTare(request,OperationResult.Unknown("scale tare acknowledgement timeout"))
            return
        }
        val written=request.written ?: return
        if(written !is OperationResult.Success)finishScaleTare(request,written)
        else request.acknowledgement?.let { success ->
            finishScaleTare(request,if(success)written else OperationResult.Failed("scale rejected tare"))
        }
    }
    private fun scaleEvent(expected:Long,event:ScaleProtocolEvent) {
        if(!ownsInitialization(expected))return
        when(event) {
            ScaleProtocolEvent.ReadingInvalidated->if(state==DeviceState.READY)
                initializationState(expected,DeviceState.SYNCHRONIZING,5000)
            is ScaleProtocolEvent.TareAcknowledgement->scaleTare?.takeIf {it.dispatched}?.let {
                // A negative acknowledgement wins over duplicates until this request completes.
                if(it.acknowledgement!=false)it.acknowledgement=event.success
                completeScaleTare(it)
            }
        }
    }
    private class SleepWrite(val frames: List<EncodedCommand>, val generation: Long,
        val expected:WeeklySleepSchedule, val target:WeeklySleepSchedule, val baselineAt:Long,
        val beforeDispatch:()->Boolean,
        val callback: (OperationResult)->Unit) {
        var secondDue: Long? = null
    }
    private var sleepWrite: SleepWrite? = null
    private fun finishSleepWrite(write: SleepWrite, result: OperationResult) {
        if (sleepWrite !== write) return
        sleepWrite = null
        notifySleepResult(write,result)
    }
    private fun notifySleepResult(write:SleepWrite,result:OperationResult) {
        try { write.callback(result) } catch (_: Exception) {
            runCatching { diagnostic("weekly sleep callback failure") }
        }
    }
    private fun cancelSleepWrite(reason: String) {
        sleepWrite?.let { finishSleepWrite(it, OperationResult.Unknown(reason)) }
    }
    private fun setState(value:DeviceState){state=value;stateChanged(value)}
    fun connect(address:String,authentication:CoffeeAuthentication?=null) {
        require(address.isNotBlank())
        require(role!=DeviceRole.COFFEE||authentication!=null){"coffee authentication required"}
        if(role==DeviceRole.BOOKOO && activeAddress==address && state !in listOf(
                DeviceState.DISCONNECTED,DeviceState.FAILED,DeviceState.UNSUPPORTED)) return
        // Validate credentials before disturbing an existing connection.
        val auth=authentication?.encode()
        val request=++connectionRequest
        endConnection(DeviceState.DISCONNECTED,"user disconnect","coffee disconnected")
        if(connectionRequest!=request)return
        lastIdle=null;lastIdleAtMs=null;lastSettingsAtMs=null;lastSettings=null;clearSleepReadback();lastFailure=null;activeAddress=address
        val expected=try { queue.open() } catch (_: TransportCloseUncertainException) {
            fail("transport close uncertain")
            return
        }
        coffeeAuthentication=authentication
        scaleDecoder=scaleAdapter.newDecoder {event->scaleEvent(expected,event)} ?: scaleAdapter::decode
        if(!initializationState(expected,DeviceState.CONNECTING,22_000))return
        step(expected,GattOperation.Connect(address),22_000) {
            if(!initializationState(expected,DeviceState.DISCOVERING))return@step
            step(expected,GattOperation.Discover,10_000){ result ->
                if(role==DeviceRole.BOOKOO && scaleAdapter is DiFluidProtocolAdapter) {
                    val binding=DiFluidGattBinding.resolve(scaleAdapter,result.characteristics)
                    if(binding==null){fail("required GATT characteristics missing or ambiguous");return@step}
                    boundScaleAdapter=binding
                    writeEndpoint=binding.writeEndpoint
                    notifyEndpoint=requireNotNull(binding.notifyEndpoint)
                }
                val strictTimemore=role!=DeviceRole.COFFEE && scaleAdapter.requiresWriteWithoutResponse
                // Android resolves by UUID, so require the raw endpoint itself to be unique before property checks.
                val write=result.characteristics.singleOrNull{it.endpoint==writeEndpoint}?.takeIf {
                    if(strictTimemore)it.writeWithoutResponse else it.write||it.writeWithoutResponse}
                val notify=result.characteristics.singleOrNull{it.endpoint==notifyEndpoint}?.takeIf {
                    if(strictTimemore)it.notify else it.notify||it.indicate}
                val subscriptionOnly=role!=DeviceRole.COFFEE && scaleAdapter.readOnlyTransport
                if((!subscriptionOnly && write==null)||notify==null){fail("required GATT characteristics missing or ambiguous");return@step}
                withResponse=if(strictTimemore)false else write?.write ?: true
                if(!initializationState(expected,DeviceState.SUBSCRIBING))return@step
                step(expected,GattOperation.Subscribe(notifyEndpoint,!notify.notify),5000){
                    if(subscriptionOnly || (role==DeviceRole.BOOKOO && scaleAdapter.initializationCommands.isEmpty())) {
                        scaleInitialized(expected)
                        return@step
                    }
                    if(!initializationState(expected,DeviceState.INITIALIZING,10_000))return@step
                    if(role==DeviceRole.COFFEE){
                        authenticateCoffee(expected,auth!!)
                    }else{initNext=0;initBusy=false;initDue=clock()+scaleAdapter.initializationCommands.first().delayMs}
                }
            }
        }
    }
    private fun authenticateCoffee(expected:Long,auth:EncodedCommand) {
        val attempt=InitialAuthentication(expected,stageDeadline)
        initialAuthentication=attempt
        queue.enqueue(GattOperation.Write(requireNotNull(writeEndpoint),auth.frame.toByteArray(),withResponse),5000,
            beforeDispatch={
                if(initialAuthentication!==attempt || !ownsInitialization(expected) || state!=DeviceState.INITIALIZING)false
                else if(clock()>=attempt.stageDeadline){fail(PROTOCOL_TIMEOUT_REASON);false}
                else {attempt.dispatchDeadline=clock()+5000;true}
            }) { result ->
            if(initialAuthentication!==attempt || !ownsInitialization(expected) || state!=DeviceState.INITIALIZING)return@enqueue
            val cached=attempt.settings
            initialAuthentication=null
            if(result !is OperationResult.Success){fail("authentication: $result");return@enqueue}
            if(clock()>=attempt.stageDeadline || attempt.dispatchDeadline?.let {clock()>=it}!=false) {
                fail(PROTOCOL_TIMEOUT_REASON);return@enqueue
            }
            // Publish SYNCHRONIZING first. Its observer may replace this owner or supply a
            // newer Settings; neither continuation may be overwritten by this early frame.
            if(!initializationState(expected,DeviceState.SYNCHRONIZING,10_000))return@enqueue
            if(clock()>=stageDeadline){fail(PROTOCOL_TIMEOUT_REASON);return@enqueue}
            if(cached!=null && SettingsFreshness.isFresh(cached.second,clock()))
                processCoffeeFrame(expected,cached.first,cached.second)
        }
    }
    private fun ownsInitialization(expected:Long)=generation==expected && queue.active
    /** Publish deadlines before observers; a reentrant reconnect owns all further work. */
    private fun initializationState(expected:Long,value:DeviceState,timeout:Long?=null):Boolean {
        if(!ownsInitialization(expected))return false
        if(timeout!=null)stageDeadline=clock()+timeout
        setState(value)
        return ownsInitialization(expected) && state==value
    }
    private fun step(expected:Long,operation:GattOperation,timeout:Long,success:(OperationResult.Success)->Unit) {
        if(!ownsInitialization(expected))return
        queue.enqueue(operation,timeout){ result ->
            if(!ownsInitialization(expected)||state==DeviceState.DISCONNECTED||state==DeviceState.FAILED)return@enqueue
            if(result is OperationResult.Success)success(result) else fail("$operation: $result")
        }
    }
    fun onComplete(generation:Long,token:Long,result:OperationResult)=queue.complete(generation,token,result)
    fun onDisconnected(generation:Long,reason:String){if(this.generation==generation&&queue.active)fail(reason)}
    fun onNotification(generation:Long,endpoint:Endpoint,bytes:ByteArray) {
        if(this.generation!=generation||!queue.active||endpoint!=notifyEndpoint)return
        val now=clock()
        if(role==DeviceRole.BOOKOO) {
            // No pre-subscription unit/status packet may seed the new decoder's trusted state.
            if(state !in listOf(DeviceState.INITIALIZING,DeviceState.SYNCHRONIZING,DeviceState.READY))return
            if(state!=DeviceState.READY && now>=stageDeadline){fail(PROTOCOL_TIMEOUT_REASON);return}
        }
        if(role==DeviceRole.COFFEE)when(val decoded=HoyiCodec.decode(bytes)) {
            is DecodeResult.Valid -> {
                val frame=decoded.value
                if(state==DeviceState.INITIALIZING && frame is Settings) {
                    initialAuthentication?.takeIf {it.generation==generation && it.dispatchDeadline!=null}?.let {
                        if(now>=it.stageDeadline || now>=it.dispatchDeadline!!)fail(PROTOCOL_TIMEOUT_REASON)
                        else it.settings=frame to now
                    }
                    return
                }
                if(state==DeviceState.SYNCHRONIZING && now>=stageDeadline){fail(PROTOCOL_TIMEOUT_REASON);return}
                processCoffeeFrame(generation,frame,now)
            }
            else -> diagnostic("coffee decode: ${decoded::class.simpleName}")
        }else when(val decoded=scaleDecoder(bytes,now)) {
            is DecodeResult.Valid -> {
                if(!ownsInitialization(generation))return
                val expectedEvidence=scaleAdapter.observationEvidence
                if(decoded.value.evidence!=expectedEvidence) {diagnostic("scale evidence rejected");return}
                if(state==DeviceState.SYNCHRONIZING)setState(DeviceState.READY)
                if(this.generation!=generation||!queue.active)return
                if(state==DeviceState.READY) {
                    scaleObservation(decoded.value)
                }
            }
            else -> diagnostic("scale decode: ${decoded::class.simpleName}")
        }
    }
    /** Shared live/replayed path; timestamps always describe reception, never replay. */
    private fun processCoffeeFrame(expected:Long,frame:HoyiMessage,now:Long) {
        if(!ownsInitialization(expected))return
        val authenticated=state==DeviceState.SYNCHRONIZING||state==DeviceState.READY
        if(frame is Settings && authenticated){lastSettingsAtMs=now;lastSettings=frame;acceptSettings(frame)}
        // READY observers may synchronously replace this connection.
        if(!ownsInitialization(expected))return
        if(state==DeviceState.READY){
            when(frame){
                is IdleTelemetry -> {lastIdle=frame;lastIdleAtMs=now;idleSerial++}
                is ExtractionTelemetry -> {lastIdle=null;lastIdleAtMs=null;boilerReadback?.automaticCancelled=true}
                is SleepPart -> if(frame.firstDaySundayIndex==0) {
                    sleepFirst=frame;sleepFirstAtMs=now;sleepSecondAtMs=null
                } else {sleepSecond=frame;sleepSecondAtMs=now}
                else -> Unit
            }
            coffeeFrame(frame,now)
        }
    }
    private fun acceptSettings(frame:Settings){
        // Only profiles with protocol evidence may control; every other firmware stays read-only.
        val permitted=CoffeeFirmwareProfiles.permitsControl(frame.firmwareInteger)
        // UNSUPPORTED bypasses endConnection; clear any stale display classification defensively.
        if(!permitted)lastFailure=null
        setState(if(permitted)DeviceState.READY else DeviceState.UNSUPPORTED)
    }
    fun tick() {
        queue.tick()
        if(!queue.active)return
        boilerReconnectLimit?.let { limit->
            if(generation!=limit.generation || address!=limit.address || state==DeviceState.READY)boilerReconnectLimit=null
            else if(clock()>=limit.deadline) {
                boilerReconnectLimit=null
                if(limit.mayExpire()){fail(PROTOCOL_TIMEOUT_REASON);return}
            }
        }
        scaleTare?.let {if(clock()>=it.deadline)finishScaleTare(it,OperationResult.Unknown("scale tare acknowledgement timeout"))}
        sleepWrite?.let { write ->
            if(write.secondDue?.let { clock() >= it } == true && write.generation == generation) {
                write.secondDue = null
                send(write.frames[1],DeviceRole.COFFEE,beforeDispatch={write.beforeDispatch() && canContinueSleepWrite(write)}) { result ->
                    finishSleepWrite(write, if (result is OperationResult.Success) result
                        else OperationResult.Unknown("weekly sleep schedule may be partially applied"))
                }
            }
        }
        if(state in listOf(DeviceState.INITIALIZING,DeviceState.SYNCHRONIZING)&&clock()>=stageDeadline){fail(PROTOCOL_TIMEOUT_REASON);return}
        if(role==DeviceRole.BOOKOO&&state==DeviceState.INITIALIZING&&!initBusy&&clock()>=initDue){
            val commands=scaleAdapter.initializationCommands;val cmd=commands[initNext]
            initBusy=true
            val expected=generation
            step(expected,GattOperation.Write(requireNotNull(writeEndpoint),cmd.frame.toByteArray(),withResponse),5000){
                initBusy=false;initNext++
                if(initNext==commands.size)scaleInitialized(expected)
                else initDue=clock()+(commands[initNext].delayMs-commands[initNext-1].delayMs)
            }
        }
        tickBoilerSettingsRefresh()
        tickScaleHeartbeat()
    }
    private fun scaleInitialized(expected:Long) {
        if(!ownsInitialization(expected))return
        // Publish before observers: a synchronous valid reading may already advance to READY.
        // A reentrant disconnect/replacement detaches this due time in endConnection.
        heartbeatDue=scaleAdapter.heartbeat?.let {clock()+it.intervalMs}
        initializationState(expected,DeviceState.SYNCHRONIZING,5000)
    }
    private fun tickScaleHeartbeat() {
        val heartbeat=scaleAdapter.heartbeat ?: return
        val due=heartbeatDue ?: return
        if(role!=DeviceRole.BOOKOO || heartbeatBusy || clock()<due ||
            state !in listOf(DeviceState.SYNCHRONIZING,DeviceState.READY))return
        val expected=generation
        heartbeatBusy=true;heartbeatDue=null
        queue.enqueue(GattOperation.Write(requireNotNull(writeEndpoint),heartbeat.command.frame.toByteArray(),withResponse),5000,
            beforeDispatch={ownsInitialization(expected) && state in listOf(DeviceState.SYNCHRONIZING,DeviceState.READY)}) {result->
            // Never let a detached heartbeat re-arm or clear the replacement owner's pending work.
            if(!ownsInitialization(expected))return@enqueue
            heartbeatBusy=false
            if(result is OperationResult.Success)heartbeatDue=clock()+heartbeat.intervalMs
            else fail("scale heartbeat failed: $result")
        }
    }
    private fun send(command:EncodedCommand,expectedRole:DeviceRole,urgent:Boolean=false,
        beforeDispatch:()->Boolean={true},callback:(OperationResult)->Unit) {
        if(role!=expectedRole||state!=DeviceState.READY){callback(OperationResult.Failed("device not ready or wrong role"));return}
        val endpoint=writeEndpoint ?: run {callback(OperationResult.Failed("scale transport is read-only"));return}
        queue.enqueue(GattOperation.Write(endpoint,command.frame.toByteArray(),withResponse),5000,urgent,
            beforeDispatch={state==DeviceState.READY && beforeDispatch()},callback=callback)
    }
    private fun canControlFromIdle():Boolean {
        val idle=lastIdle ?: return false
        val observedAt=lastIdleAtMs ?: return false
        val now=clock()
        return role==DeviceRole.COFFEE && state==DeviceState.READY &&
            TelemetryFreshness.isFresh(observedAt,now) && idle.sleepStateRaw==0 && CoffeeAlarmPolicy.permitsNewControl(idle.alarmBits)
    }
    private fun canControlWithFreshSettings():Boolean =
        canControlFromIdle() && SettingsFreshness.isFresh(lastSettingsAtMs,clock())
    private fun canControlWithBrewSettings():Boolean = canControlFromIdle() &&
        BrewSettingsEvidence.isAvailable(state==DeviceState.READY,lastSettings!=null,lastSettingsAtMs,clock())
    private fun canCancelBrewWait():Boolean {
        val idle=lastIdle ?: return false
        val observedAt=lastIdleAtMs ?: return false
        val now=clock()
        // Cancellation is allowed during an alarm, but never from stale or extraction telemetry.
        return role==DeviceRole.COFFEE && state==DeviceState.READY &&
            TelemetryFreshness.isFresh(observedAt,now) && idle.sleepStateRaw==0
    }
    private fun permittedStart(parameters:StartParameters):EncodedCommand? {
        val command=runCatching { CoffeeCommands.start(parameters) }.getOrNull() ?: return null
        val captured=setOf("02175B006C005A410000015E1600AA00000000DA","02DF5C0046001426140000A0050190008C000059","02DF5C00880014231200009605019000820000AC")
        return command.takeIf { it.frame.hex() in captured || it.frame.hex() in additionalStartFrames ||
            (CustomPressureStartPolicy.permits(parameters) &&
                runCatching {customPressureStartPermit(parameters)}.getOrDefault(false)) ||
            (CustomCurveStartPolicy.permits(parameters) && CustomCurveFirmwarePolicy.permits(parameters, observedFirmware) &&
                runCatching {customCurveStartPermit(parameters)}.getOrDefault(false)) }
    }
    private fun canStartExtraction(parameters:StartParameters):Boolean {
        if(!canControlWithBrewSettings()) return false
        val settings=lastSettings ?: return false
        if(settings.flags and 0x04 == 0) return true
        val idle=lastIdle ?: return false
        return BrewTemperaturePolicy.isAtTarget(BrewTemperaturePolicy.correctedTemperature(
            idle.brewTemperatureHundredthsC,settings.brewCompensationTenthsC),parameters.temperatureC)
    }
    fun captureStartContext(parameters:StartParameters):CoffeeStartContext? {
        if(!canStartExtraction(parameters) || sleepWrite!=null || permittedStart(parameters)==null) return null
        val settings=lastSettings ?: return null
        return CoffeeStartContext(generation,address,parameters,settings.flags and 0x26,settings.firmwareInteger,
            settings.brewTemperatureC,settings.brewCompensationTenthsC,clock())
    }
    fun startConditionsValid(parameters:StartParameters,context:CoffeeStartContext):Boolean {
        val settings=lastSettings ?: return false
        val now=clock()
        return context.generation==generation && context.address==address && context.parameters==parameters &&
            context.capturedAt<=now && now-context.capturedAt<5000 &&
            settings.flags and 0x26 == context.settingsFlags &&
            settings.firmwareInteger==context.firmwareInteger &&
            settings.brewTemperatureC==context.brewTemperatureC &&
            settings.brewCompensationTenthsC==context.compensationTenthsC &&
            permittedStart(parameters)!=null && canStartExtraction(parameters)
    }
    fun startExtraction(parameters:StartParameters,callback:(OperationResult)->Unit) {
        val context=captureStartContext(parameters)
        if(context==null) {callback(OperationResult.Failed("start conditions not ready"));return}
        startExtraction(parameters,context,callback)
    }
    fun startExtraction(parameters:StartParameters,context:CoffeeStartContext,callback:(OperationResult)->Unit)=
        startExtraction(parameters,context,{true},callback)
    fun startExtraction(parameters:StartParameters,context:CoffeeStartContext,beforeDispatch:()->Boolean,callback:(OperationResult)->Unit) {
        if (sleepWrite != null) { callback(OperationResult.Failed("weekly sleep write active")); return }
        if (!startConditionsValid(parameters,context)) {
            callback(OperationResult.Failed("original start context no longer valid")); return
        }
        // Product host supplies only frames checked against the extracted legacy encoder.
        val command=permittedStart(parameters)
        if(command==null){callback(OperationResult.Failed("curve outside validated profile set"));return}
        send(command,DeviceRole.COFFEE,beforeDispatch={beforeDispatch() && startConditionsValid(parameters,context)},callback=callback)
    }
    fun stopExtraction(slot:Int,callback:(OperationResult)->Unit)=stopExtraction(slot,address,callback)
    fun stopExtraction(slot:Int,expectedAddress:String?,callback:(OperationResult)->Unit) {
        require(slot in 1..5 || slot == 7)
        val stopGeneration=generation
        fun sameTarget():Boolean = role==DeviceRole.COFFEE && state==DeviceState.READY &&
            generation==stopGeneration && !expectedAddress.isNullOrBlank() &&
            address?.equals(expectedAddress,ignoreCase=true)==true
        if (!sameTarget()) {
            callback(OperationResult.Failed("stop requires the expected ready coffee device"));return
        }
        cancelSleepWrite("stop requested during weekly sleep write")
        if(!sameTarget()){callback(OperationResult.Failed("stop connection changed during sleep cancellation"));return}
        // An emergency stop must not be followed by older, still-queued control writes.
        queue.cancelPending { it is GattOperation.Write }
        if(!sameTarget()){callback(OperationResult.Failed("stop connection changed during queue cancellation"));return}
        send(CoffeeCommands.stop(slot),DeviceRole.COFFEE,urgent=true,
            beforeDispatch=::sameTarget,callback=callback)
    }
    fun tare(beforeDispatch:()->Boolean,callback:(OperationResult)->Unit) {
        val command=scaleAdapter.tareCommand
        if(!scaleAdapter.capabilities.tare || command==null) {
            callback(OperationResult.Failed("scale tare not supported by verified adapter"));return
        }
        if(!scaleAdapter.requiresTareAcknowledgement) {
            send(command,DeviceRole.BOOKOO,beforeDispatch=beforeDispatch,callback=callback)
            return
        }
        if(scaleTare!=null){callback(OperationResult.Failed("scale tare pending"));return}
        val pending=ScaleTare(callback)
        val expected=generation
        scaleTare=pending
        send(command,DeviceRole.BOOKOO,beforeDispatch={
            val allowed=ownsInitialization(expected) && scaleTare===pending && beforeDispatch()
            if(allowed){pending.dispatched=true;pending.deadline=clock()+5000}
            allowed
        }) {result->
            if(scaleTare===pending) {
                pending.written=result
                completeScaleTare(pending)
            }
        }
    }
    private fun sendFromIdle(command:EncodedCommand,beforeDispatch:()->Boolean,callback:(OperationResult)->Unit) {
        if (!canControlFromIdle()) {
            callback(OperationResult.Failed("fresh awake idle telemetry without blocking alarms required"))
            return
        }
        send(command,DeviceRole.COFFEE,beforeDispatch={beforeDispatch() && canControlFromIdle()},callback=callback)
    }
    private fun canStartPreheat():Boolean =
        canControlWithBrewSettings() && lastSettings?.flags?.and(0x04) == 0x04
    private fun canWriteSetting(change: MachineSettingChange): Boolean =
        canControlWithFreshSettings() && when(change) {
            is MachineSettingChange.StandbyDelay -> lastSettings?.standbyTemperatureC == change.temperatureC
            is MachineSettingChange.StandbyTemperature -> lastSettings?.standbyMinutes == change.minutes
            is MachineSettingChange.SleepScheduleEnabled -> !change.enabled ||
                SleepScheduleFreshness.isFresh(sleepFirst,sleepFirstAtMs,sleepSecond,sleepSecondAtMs,clock())
            else -> true
        }
    fun writeSetting(change:MachineSettingChange,callback:(OperationResult)->Unit)=writeSetting(change,{true},callback)
    fun writeSetting(change:MachineSettingChange,beforeDispatch:()->Boolean,callback:(OperationResult)->Unit) {
        boilerReadback=null;boilerRefreshTask=null
        if (sleepWrite != null) callback(OperationResult.Failed("weekly sleep write active"))
        else if (!canWriteSetting(change)) callback(OperationResult.Failed("fresh settings with unchanged companion value required"))
        else {
            val enablingPlan = if(change is MachineSettingChange.SleepScheduleEnabled && change.enabled)
                WeeklySleepSchedule.fromReadback(sleepFirst,sleepSecond)?.days else null
            val expected=generation
            send(CoffeeCommands.setting(change),DeviceRole.COFFEE,
                beforeDispatch={beforeDispatch() && canWriteSetting(change) &&
                    (enablingPlan == null || WeeklySleepSchedule.fromReadback(sleepFirst,sleepSecond)?.days == enablingPlan)}) {result->
                // Arm before the host callback: it must enter WAITING_READBACK before requesting refresh.
                if(result is OperationResult.Success && ownsInitialization(expected) && state==DeviceState.READY &&
                    (change is MachineSettingChange.BrewHeating || change is MachineSettingChange.SteamHeating))
                    coffeeAuthentication?.let { authentication->address?.let { target->
                        boilerReadback=BoilerReadback(expected,target,authentication,clock())
                    } }
                callback(result)
            }
        }
    }
    private fun ownsBoilerReadback(readback:BoilerReadback):Boolean = boilerReadback===readback &&
        ownsInitialization(readback.generation) && address==readback.address &&
        coffeeAuthentication===readback.authentication && state==DeviceState.READY
    /** Bounded authenticated time-sync requests, NOT pure reads or boiler-control retries.
     * GATT success never confirms application. The host owns the original six-second watchdog. */
    fun refreshBoilerSettings(beforeDispatch:()->Boolean,outcome:(Int,OperationResult)->Unit) {
        val readback=boilerReadback
        if(readback==null || !ownsBoilerReadback(readback) || readback.automaticStarted || clock()>=readback.deadline) {
            outcome(0,OperationResult.Failed("no eligible boiler settings refresh"));return
        }
        readback.automaticStarted=true
        boilerRefreshTask=BoilerRefreshTask(false,readback.deadline,
            listOf(readback.startedAt+250,readback.startedAt+1250),beforeDispatch,outcome)
    }
    val boilerSettingsRecoveryBusy:Boolean get()=boilerRefreshTask?.let {it.manual && clock()<it.deadline}==true
    val canRefreshUnknownBoilerSettings:Boolean get()=boilerReadback?.let {
        ownsBoilerReadback(it) && !boilerSettingsRecoveryBusy && canControlFromIdle() && sleepWrite==null
    }==true
    /** Explicit user recovery is one synchronization attempt and does not require fresh settings.
     * Only the live original successful boiler write can supply credentials/provenance. */
    fun refreshUnknownBoilerSettings(beforeDispatch:()->Boolean,outcome:(Int,OperationResult)->Unit) {
        if(!canRefreshUnknownBoilerSettings || !beforeDispatch()) {
            outcome(0,OperationResult.Failed("no eligible boiler settings recovery"));return
        }
        val now=clock()
        boilerRefreshTask=BoilerRefreshTask(true,now+6000,listOf(now+250),beforeDispatch,outcome)
    }
    private fun tickBoilerSettingsRefresh() {
        val task=boilerRefreshTask ?: return
        val readback=boilerReadback ?: run {boilerRefreshTask=null;return}
        if(!ownsBoilerReadback(readback) || !task.beforeDispatch() || !task.manual && readback.automaticCancelled) {
            if(!task.manual)readback.automaticCancelled=true
            boilerRefreshTask=null;return
        }
        if(clock()>=task.deadline) {
            boilerRefreshTask=null
            task.outcome(0,OperationResult.Unknown("boiler settings refresh deadline"));return
        }
        if(task.inFlight || task.next>=task.due.size || clock()<maxOf(task.due[task.next],task.lastDispatchAt?.plus(250) ?: 0) ||
            task.waitAfterIdle?.let {idleSerial<=it}==true)return
        // Missing/stale idle waits for a genuinely newer report, not repeated failed writes.
        if(!canControlFromIdle() || (!task.manual && !canControlWithFreshSettings()) || sleepWrite!=null) {
            task.waitAfterIdle=idleSerial;return
        }
        task.waitAfterIdle=null;task.inFlight=true
        var dispatched=false
        var attempt=0
        send(readback.authentication.encodeAt(wallClock()),DeviceRole.COFFEE,beforeDispatch={
            val allowed=boilerRefreshTask===task && ownsBoilerReadback(readback) && clock()<task.deadline &&
                task.beforeDispatch() && sleepWrite==null && canControlFromIdle() &&
                (task.manual || canControlWithFreshSettings())
            if(allowed) {
                dispatched=true;attempt=++task.next;readback.syncDispatched=true;task.lastDispatchAt=clock()
                if(!task.manual)readback.syncAttempts++
            }
            allowed
        }) {result->
            task.inFlight=false
            task.outcome(attempt,result)
            if(boilerRefreshTask===task && !dispatched)task.waitAfterIdle=idleSerial
        }
    }
    /** Typed mismatch reconciliation starts only after this transaction actually sends sync. */
    val boilerReconciliationReady:Boolean get()=boilerReadback?.let {ownsBoilerReadback(it) && it.syncDispatched}==true ||
        boilerReconnectLimit?.let {generation==it.generation && address==it.address && state==DeviceState.READY}==true
    val canReconnectBoilerSettings:Boolean get()=boilerReadback?.let {
        ownsBoilerReadback(it) && it.automaticStarted && !it.automaticCancelled && it.syncAttempts==2 && !it.reconnectUsed &&
            clock()>=it.startedAt+3000 && clock()<it.deadline && boilerRefreshTask?.inFlight!=true &&
            canControlFromIdle() && sleepWrite==null
    }==true
    /** Consume the one reconnect fallback before synchronous disconnect callbacks run. */
    fun reconnectBoilerSettings(beforeDispatch:()->Boolean,mayExpire:()->Boolean):Boolean {
        if(!canReconnectBoilerSettings || !beforeDispatch())return false
        val readback=requireNotNull(boilerReadback)
        readback.reconnectUsed=true;boilerRefreshTask=null
        connect(readback.address,readback.authentication.atTime(wallClock()))
        if(generation==readback.generation+1 && address==readback.address && state!=DeviceState.READY)
            boilerReconnectLimit=BoilerReconnectLimit(generation,readback.address,readback.startedAt+18000,mayExpire)
        return true
    }
    private fun canBeginSleepWrite(expected:WeeklySleepSchedule):Boolean = canControlFromIdle() &&
        SleepScheduleFreshness.isFresh(sleepFirst,sleepFirstAtMs,sleepSecond,sleepSecondAtMs,clock()) &&
        WeeklySleepSchedule.fromReadback(sleepFirst,sleepSecond)?.days == expected.days
    private fun firstSleepMatches(plan:WeeklySleepSchedule):Boolean {
        val first=sleepFirst ?: return false
        val bits=first.enabledBits ?: return false
        return first.firstDaySundayIndex==0 && first.days == plan.days.take(4).map(WeeklySleepDay::time) &&
            plan.days.indices.all { ((bits and (0x80 shr it)) != 0) == plan.days[it].enabled }
    }
    private fun canContinueSleepWrite(write:SleepWrite):Boolean = canControlFromIdle() &&
        SettingsFreshness.isFresh(write.baselineAt,clock()) &&
        (firstSleepMatches(write.expected) || firstSleepMatches(write.target)) &&
        sleepSecond?.firstDaySundayIndex==4 &&
        sleepSecond?.days == write.expected.days.drop(4).map(WeeklySleepDay::time)
    fun writeSleepSchedule(schedule:WeeklySleepSchedule,expected:WeeklySleepSchedule,callback:(OperationResult)->Unit)=
        writeSleepSchedule(schedule,expected,{true},callback)
    fun writeSleepSchedule(schedule:WeeklySleepSchedule,expected:WeeklySleepSchedule,beforeDispatch:()->Boolean,callback:(OperationResult)->Unit) {
        if (sleepWrite!=null || !canBeginSleepWrite(expected)) {
            callback(OperationResult.Failed("fresh unchanged full sleep readback and awake idle required"));return
        }
        val write=SleepWrite(CoffeeCommands.sleepSchedule(schedule),generation,expected,schedule,
            minOf(sleepFirstAtMs!!,sleepSecondAtMs!!),beforeDispatch,callback)
        sleepWrite=write
        send(write.frames[0],DeviceRole.COFFEE,beforeDispatch={beforeDispatch() && canBeginSleepWrite(expected)}) { result ->
            if (result is OperationResult.Success) {
                if (sleepWrite === write) {
                    write.secondDue=clock()+500
                }
            } else finishSleepWrite(write,result)
        }
    }
    fun enterSleep(callback:(OperationResult)->Unit)=enterSleep({true},callback)
    fun enterSleep(beforeDispatch:()->Boolean,callback:(OperationResult)->Unit) {
        if (sleepWrite != null) callback(OperationResult.Failed("weekly sleep write active"))
        else sendFromIdle(CoffeeCommands.sleepNow(),beforeDispatch,callback)
    }
    private fun canResetCupCount(expectedCount:Int):Boolean =
        expectedCount in 1..65535 && canControlWithFreshSettings() &&
            lastSettings?.cupCount == expectedCount && lastIdle?.cupCount == expectedCount
    fun resetCupCount(expectedCount:Int,callback:(OperationResult)->Unit)=resetCupCount(expectedCount,{true},callback)
    fun resetCupCount(expectedCount:Int,beforeDispatch:()->Boolean,callback:(OperationResult)->Unit) {
        if (sleepWrite != null) callback(OperationResult.Failed("weekly sleep write active"))
        else if (!canResetCupCount(expectedCount))
            callback(OperationResult.Failed("fresh matching settings and idle cup count required"))
        else send(CoffeeCommands.resetCupCount(),DeviceRole.COFFEE,
            beforeDispatch={beforeDispatch() && canResetCupCount(expectedCount)},callback=callback)
    }
    fun setBrewWait(targetC:Int,beforeDispatch:()->Boolean,callback:(OperationResult)->Unit) {
        if (sleepWrite != null) callback(OperationResult.Failed("weekly sleep write active"))
        else if (targetC==0) {
            if (!canCancelBrewWait()) callback(OperationResult.Failed("fresh awake idle telemetry required for preheat cancel"))
            else send(CoffeeCommands.brewWait(0),DeviceRole.COFFEE,
                beforeDispatch={beforeDispatch() && canCancelBrewWait()},callback=callback)
        }
        else if (!canStartPreheat())
            callback(OperationResult.Failed("fresh awake idle and studio settings required for preheat"))
        else send(CoffeeCommands.brewWait(targetC),DeviceRole.COFFEE,
            beforeDispatch={beforeDispatch() && canStartPreheat()},callback=callback)
    }
    fun disconnect() {
        connectionRequest++
        endConnection(DeviceState.DISCONNECTED,"user disconnect","coffee disconnected")
    }
    private fun endConnection(terminal:DeviceState,reason:String,sleepReason:String=reason) {
        val expected=generation
        // Display-only classification must be visible to observers of the new state and must not
        // survive into READY/DISCONNECTED/UNSUPPORTED.
        if(terminal!=DeviceState.FAILED)lastFailure=null
        // Detach old session work before invoking any observer that may reconnect.
        boundScaleAdapter=scaleAdapter
        writeEndpoint=if(role==DeviceRole.COFFEE)KnownGatt.coffeeWrite else scaleAdapter.writeEndpoint
        notifyEndpoint=if(role==DeviceRole.COFFEE)KnownGatt.coffeeNotify else scaleAdapter.notifyEndpoint!!
        val detachedSleep=sleepWrite;sleepWrite=null;initBusy=false
        heartbeatDue=null;heartbeatBusy=false
        val detachedTare=scaleTare;scaleTare=null
        initialAuthentication=null
        coffeeAuthentication=null;boilerReadback=null;boilerRefreshTask=null;boilerReconnectLimit=null
        activeAddress=null;lastIdle=null;lastIdleAtMs=null;lastSettingsAtMs=null;lastSettings=null;clearSleepReadback()
        try { setState(terminal) }
        finally {
            try { detachedSleep?.let { notifySleepResult(it,OperationResult.Unknown(sleepReason)) } }
            finally {
                try {detachedTare?.callback?.invoke(OperationResult.Unknown("scale disconnected before tare acknowledgement"))}
                finally { if(generation==expected)queue.disconnect(reason) }
            }
        }
    }
    private fun classifyFailure(reason:String):CoffeeSessionFailure = when {
        reason==PROTOCOL_TIMEOUT_REASON -> CoffeeSessionFailure.PROTOCOL_TIMEOUT
        role==DeviceRole.COFFEE && state==DeviceState.CONNECTING -> CoffeeSessionFailure.CONNECT
        role==DeviceRole.COFFEE && state==DeviceState.INITIALIZING -> CoffeeSessionFailure.AUTH
        else -> CoffeeSessionFailure.OTHER   // service discovery/subscribe/queue timeout; scale sessions are unread
    }
    private fun fail(reason:String){
        connectionRequest++
        lastFailure=classifyFailure(reason)
        endConnection(DeviceState.FAILED,reason)
        diagnostic(reason)
    }
}
