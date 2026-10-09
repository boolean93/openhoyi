package io.openhoyi.session

import io.openhoyi.protocol.*
import java.time.LocalDateTime

enum class DeviceRole { COFFEE, BOOKOO }
enum class DeviceState { DISCONNECTED, CONNECTING, DISCOVERING, SUBSCRIBING, INITIALIZING, SYNCHRONIZING, READY, UNSUPPORTED, FAILED }
class CoffeeAuthentication(val time:LocalDateTime,private val password:String) {
    fun encode()=CoffeeCommands.authenticate(time,password)
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
    private val weightFrame:(BookooSample,Long)->Unit={_,_->},private val diagnostic:(String)->Unit={},
    legacyVerifiedStartFrames:Set<String> = emptySet(),
    val scaleAdapter:ScaleProtocolAdapter = BookooScaleProtocolAdapter,
    private val scaleObservation:(ScaleObservation)->Unit = {}) {
    init { require(role==DeviceRole.COFFEE || (scaleAdapter.transportVerified && scaleAdapter.writeEndpoint!=null &&
        scaleAdapter.notifyEndpoint!=null && scaleAdapter.initializationCommands.isNotEmpty())) { "Offline scale candidates cannot connect" }
        if(role!=DeviceRole.COFFEE)require(scaleAdapter.initializationCommands.first().delayMs>=0 &&
            scaleAdapter.initializationCommands.zipWithNext().all {(a,b)->b.delayMs>a.delayMs}) { "Invalid scale initialization timing" }
    }
    val scaleCapabilities:ScaleCapabilities get()=scaleAdapter.capabilities
    private val additionalStartFrames = legacyVerifiedStartFrames.toSet().also { frames ->
        require(frames.size <= 1200 && frames.all { hex ->
            hex.matches(Regex("02[0-9A-F]{38}")) &&
                hex.chunked(2).map { it.toInt(16) }.reduce(Int::xor) == 0 &&
                (hex.substring(2, 4).toInt(16) and 7).let { it in 1..5 || it == 7 }
        }) { "Invalid legacy start-frame permit" }
    }
    private val queue=GattQueue(GuardedGattDriver(role,driver,scaleAdapter),clock){fail(it)}
    // Request order also detects cancellation before a new GATT generation exists.
    private var connectionRequest=0L
    val generation:Long get()=queue.generation
    var state=DeviceState.DISCONNECTED;private set
    private var activeAddress:String?=null
    val address:String? get()=activeAddress
    val observedFirmware:CoffeeFirmware? get()=lastSettings?.let {
        CoffeeFirmware(it.firmwareMajor,it.firmwareMinor,it.firmwarePatch)
    }
    private var lastIdle:IdleTelemetry?=null
    private var lastIdleAtMs:Long?=null
    private var lastSettingsAtMs:Long?=null
    private var lastSettings:Settings?=null
    private var sleepFirst:SleepPart?=null
    private var sleepSecond:SleepPart?=null
    private var sleepFirstAtMs:Long?=null
    private var sleepSecondAtMs:Long?=null
    private fun clearSleepReadback() {
        sleepFirst=null;sleepSecond=null;sleepFirstAtMs=null;sleepSecondAtMs=null
    }
    private var notifyEndpoint=if(role==DeviceRole.COFFEE)KnownGatt.coffeeNotify else scaleAdapter.notifyEndpoint!!
    private var writeEndpoint=if(role==DeviceRole.COFFEE)KnownGatt.coffeeWrite else scaleAdapter.writeEndpoint!!
    private var withResponse=true
    private var stageDeadline=0L
    private var initNext=0
    private var initDue=0L
    private var initBusy=false
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
        lastIdle=null;lastIdleAtMs=null;lastSettingsAtMs=null;lastSettings=null;clearSleepReadback();activeAddress=address
        val expected=queue.open()
        if(!initializationState(expected,DeviceState.CONNECTING,22_000))return
        step(expected,GattOperation.Connect(address),22_000) {
            if(!initializationState(expected,DeviceState.DISCOVERING))return@step
            step(expected,GattOperation.Discover,10_000){ result ->
                val write=result.characteristics.singleOrNull{it.endpoint==writeEndpoint&&(it.write||it.writeWithoutResponse)}
                val notify=result.characteristics.singleOrNull{it.endpoint==notifyEndpoint&&(it.notify||it.indicate)}
                if(write==null||notify==null){fail("required GATT characteristics missing or ambiguous");return@step}
                withResponse=write.write
                if(!initializationState(expected,DeviceState.SUBSCRIBING))return@step
                step(expected,GattOperation.Subscribe(notifyEndpoint,!notify.notify),5000){
                    if(!initializationState(expected,DeviceState.INITIALIZING,10_000))return@step
                    if(role==DeviceRole.COFFEE){
                        step(expected,GattOperation.Write(writeEndpoint,auth!!.frame.toByteArray(),withResponse),5000){
                            initializationState(expected,DeviceState.SYNCHRONIZING,10_000)
                        }
                    }else{initNext=0;initBusy=false;initDue=clock()+scaleAdapter.initializationCommands.first().delayMs}
                }
            }
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
        if(role==DeviceRole.COFFEE)when(val decoded=HoyiCodec.decode(bytes)) {
            is DecodeResult.Valid -> {
                val frame=decoded.value
                // Notifications received before this connection is READY must not populate
                // product telemetry or satisfy readback checks.
                val authenticated=state==DeviceState.SYNCHRONIZING||state==DeviceState.READY
                if(frame is Settings){
                    if(authenticated){lastSettingsAtMs=now;lastSettings=frame;acceptSettings(frame)}
                }
                // READY observers may synchronously replace this connection.
                if(this.generation!=generation||!queue.active)return
                if(state==DeviceState.READY){
                    when(frame){
                        is IdleTelemetry -> {lastIdle=frame;lastIdleAtMs=now}
                        is ExtractionTelemetry -> {lastIdle=null;lastIdleAtMs=null}
                        is SleepPart -> if(frame.firstDaySundayIndex==0) {
                            sleepFirst=frame;sleepFirstAtMs=now;sleepSecondAtMs=null
                        } else {
                            sleepSecond=frame;sleepSecondAtMs=now
                        }
                        else -> Unit
                    }
                    coffeeFrame(frame,now)
                }
            }
            else -> diagnostic("coffee decode: ${decoded::class.simpleName}")
        }else when(val decoded=scaleAdapter.decode(bytes,now)) {
            is DecodeResult.Valid -> {
                if(state==DeviceState.SYNCHRONIZING)setState(DeviceState.READY)
                if(this.generation!=generation||!queue.active)return
                if(state==DeviceState.READY) {
                    scaleObservation(decoded.value)
                    // Compatibility callback is deliberately outside generic extraction logic.
                    if(this.generation==generation && queue.active && state==DeviceState.READY &&
                        scaleAdapter===BookooScaleProtocolAdapter)
                        (BookooCodec.decode(bytes) as? DecodeResult.Valid)?.let { weightFrame(it.value,now) }
                }
            }
            else -> diagnostic("scale decode: ${decoded::class.simpleName}")
        }
    }
    private fun acceptSettings(frame:Settings){
        // First validated profile only; other firmware remains read-only.
        setState(if(frame.firmwareInteger==0x113)DeviceState.READY else DeviceState.UNSUPPORTED)
    }
    fun tick() {
        queue.tick()
        if(!queue.active)return
        sleepWrite?.let { write ->
            if(write.secondDue?.let { clock() >= it } == true && write.generation == generation) {
                write.secondDue = null
                send(write.frames[1],DeviceRole.COFFEE,beforeDispatch={write.beforeDispatch() && canContinueSleepWrite(write)}) { result ->
                    finishSleepWrite(write, if (result is OperationResult.Success) result
                        else OperationResult.Unknown("weekly sleep schedule may be partially applied"))
                }
            }
        }
        if(state in listOf(DeviceState.INITIALIZING,DeviceState.SYNCHRONIZING)&&clock()>=stageDeadline){fail("protocol initialization timeout");return}
        if(role==DeviceRole.BOOKOO&&state==DeviceState.INITIALIZING&&!initBusy&&clock()>=initDue){
            val commands=scaleAdapter.initializationCommands;val cmd=commands[initNext]
            initBusy=true
            val expected=generation
            step(expected,GattOperation.Write(writeEndpoint,cmd.frame.toByteArray(),withResponse),5000){
                initBusy=false;initNext++
                if(initNext==commands.size)initializationState(expected,DeviceState.SYNCHRONIZING,5000)
                else initDue=clock()+(commands[initNext].delayMs-commands[initNext-1].delayMs)
            }
        }
    }
    private fun send(command:EncodedCommand,expectedRole:DeviceRole,urgent:Boolean=false,
        beforeDispatch:()->Boolean={true},callback:(OperationResult)->Unit) {
        if(role!=expectedRole||state!=DeviceState.READY){callback(OperationResult.Failed("device not ready or wrong role"));return}
        queue.enqueue(GattOperation.Write(writeEndpoint,command.frame.toByteArray(),withResponse),5000,urgent,
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
        return command.takeIf { it.frame.hex() in captured || it.frame.hex() in additionalStartFrames }
    }
    private fun canStartExtraction(parameters:StartParameters):Boolean {
        if(!canControlWithFreshSettings()) return false
        val settings=lastSettings ?: return false
        if(settings.flags and 0x04 == 0) return true
        val idle=lastIdle ?: return false
        return BrewTemperaturePolicy.isAtTarget(BrewTemperaturePolicy.correctedTemperature(
            idle.brewTemperatureHundredthsC,settings.brewCompensationTenthsC),parameters.temperatureC)
    }
    fun captureStartContext(parameters:StartParameters):CoffeeStartContext? {
        if(!canStartExtraction(parameters) || sleepWrite!=null || permittedStart(parameters)==null) return null
        val settings=lastSettings ?: return null
        return CoffeeStartContext(generation,address,parameters,settings.flags and 0x26,
            settings.brewTemperatureC,settings.brewCompensationTenthsC,clock())
    }
    fun startConditionsValid(parameters:StartParameters,context:CoffeeStartContext):Boolean {
        val settings=lastSettings ?: return false
        val now=clock()
        return context.generation==generation && context.address==address && context.parameters==parameters &&
            context.capturedAt<=now && now-context.capturedAt<5000 &&
            settings.flags and 0x26 == context.settingsFlags &&
            settings.brewTemperatureC==context.brewTemperatureC &&
            settings.brewCompensationTenthsC==context.compensationTenthsC && canStartExtraction(parameters)
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
        send(command,DeviceRole.BOOKOO,beforeDispatch=beforeDispatch,callback=callback)
    }
    private fun sendFromIdle(command:EncodedCommand,beforeDispatch:()->Boolean,callback:(OperationResult)->Unit) {
        if (!canControlFromIdle()) {
            callback(OperationResult.Failed("fresh awake idle telemetry without blocking alarms required"))
            return
        }
        send(command,DeviceRole.COFFEE,beforeDispatch={beforeDispatch() && canControlFromIdle()},callback=callback)
    }
    private fun canStartPreheat():Boolean =
        canControlWithFreshSettings() && lastSettings?.flags?.and(0x04) == 0x04
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
        if (sleepWrite != null) callback(OperationResult.Failed("weekly sleep write active"))
        else if (!canWriteSetting(change)) callback(OperationResult.Failed("fresh settings with unchanged companion value required"))
        else {
            val enablingPlan = if(change is MachineSettingChange.SleepScheduleEnabled && change.enabled)
                WeeklySleepSchedule.fromReadback(sleepFirst,sleepSecond)?.days else null
            send(CoffeeCommands.setting(change),DeviceRole.COFFEE,
                beforeDispatch={beforeDispatch() && canWriteSetting(change) &&
                    (enablingPlan == null || WeeklySleepSchedule.fromReadback(sleepFirst,sleepSecond)?.days == enablingPlan)},
                callback=callback)
        }
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
        // Detach old session work before invoking any observer that may reconnect.
        val detachedSleep=sleepWrite;sleepWrite=null;initBusy=false
        activeAddress=null;lastIdle=null;lastIdleAtMs=null;lastSettingsAtMs=null;lastSettings=null;clearSleepReadback()
        try { setState(terminal) }
        finally {
            try { detachedSleep?.let { notifySleepResult(it,OperationResult.Unknown(sleepReason)) } }
            finally { if(generation==expected)queue.disconnect(reason) }
        }
    }
    private fun fail(reason:String){
        connectionRequest++
        endConnection(DeviceState.FAILED,reason)
        diagnostic(reason)
    }
}
