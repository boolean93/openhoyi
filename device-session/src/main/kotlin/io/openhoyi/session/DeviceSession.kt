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
    legacyVerifiedStartFrames:Set<String> = emptySet()) {
    private val additionalStartFrames = legacyVerifiedStartFrames.toSet().also { frames ->
        require(frames.size <= 1200 && frames.all { hex ->
            hex.matches(Regex("02[0-9A-F]{38}")) &&
                hex.chunked(2).map { it.toInt(16) }.reduce(Int::xor) == 0 &&
                (hex.substring(2, 4).toInt(16) and 7).let { it in 1..5 || it == 7 }
        }) { "Invalid legacy start-frame permit" }
    }
    private val queue=GattQueue(driver,clock){fail(it)}
    val generation:Long get()=queue.generation
    var state=DeviceState.DISCONNECTED;private set
    private var activeAddress:String?=null
    val address:String? get()=activeAddress
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
    private var notifyEndpoint=if(role==DeviceRole.COFFEE)KnownGatt.coffeeNotify else KnownGatt.bookooNotify
    private var writeEndpoint=if(role==DeviceRole.COFFEE)KnownGatt.coffeeWrite else KnownGatt.bookooWrite
    private var withResponse=true
    private var stageDeadline=0L
    private var initNext=0
    private var initDue=0L
    private var initBusy=false
    private class SleepWrite(val frames: List<EncodedCommand>, val generation: Long,
        val expected:WeeklySleepSchedule, val target:WeeklySleepSchedule, val baselineAt:Long,
        val callback: (OperationResult)->Unit) {
        var secondDue: Long? = null
    }
    private var sleepWrite: SleepWrite? = null
    private fun finishSleepWrite(write: SleepWrite, result: OperationResult) {
        if (sleepWrite !== write) return
        sleepWrite = null
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
        disconnect();lastIdle=null;lastIdleAtMs=null;lastSettingsAtMs=null;lastSettings=null;clearSleepReadback();activeAddress=address;queue.open();setState(DeviceState.CONNECTING);stageDeadline=clock()+22_000
        step(GattOperation.Connect(address),22_000) {
            setState(DeviceState.DISCOVERING)
            step(GattOperation.Discover,10_000){ result ->
                val write=result.characteristics.singleOrNull{it.endpoint==writeEndpoint&&(it.write||it.writeWithoutResponse)}
                val notify=result.characteristics.singleOrNull{it.endpoint==notifyEndpoint&&(it.notify||it.indicate)}
                if(write==null||notify==null){fail("required GATT characteristics missing or ambiguous");return@step}
                withResponse=write.write
                setState(DeviceState.SUBSCRIBING)
                step(GattOperation.Subscribe(notifyEndpoint,!notify.notify),5000){
                    setState(DeviceState.INITIALIZING);stageDeadline=clock()+10_000
                    if(role==DeviceRole.COFFEE){
                        step(GattOperation.Write(writeEndpoint,auth!!.frame.toByteArray(),withResponse),5000){
                            setState(DeviceState.SYNCHRONIZING);stageDeadline=clock()+10_000
                        }
                    }else{initNext=0;initBusy=false;initDue=clock()+500}
                }
            }
        }
    }
    private fun step(operation:GattOperation,timeout:Long,success:(OperationResult.Success)->Unit) {
        val expected=generation
        queue.enqueue(operation,timeout){ result ->
            if(generation!=expected||state==DeviceState.DISCONNECTED||state==DeviceState.FAILED)return@enqueue
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
        }else when(val decoded=BookooCodec.decode(bytes)) {
            is DecodeResult.Valid -> {
                if(state==DeviceState.SYNCHRONIZING)setState(DeviceState.READY)
                if(state==DeviceState.READY)weightFrame(decoded.value,now)
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
                send(write.frames[1],DeviceRole.COFFEE,beforeDispatch={canContinueSleepWrite(write)}) { result ->
                    finishSleepWrite(write, if (result is OperationResult.Success) result
                        else OperationResult.Unknown("weekly sleep schedule may be partially applied"))
                }
            }
        }
        if(state in listOf(DeviceState.INITIALIZING,DeviceState.SYNCHRONIZING)&&clock()>=stageDeadline){fail("protocol initialization timeout");return}
        if(role==DeviceRole.BOOKOO&&state==DeviceState.INITIALIZING&&!initBusy&&clock()>=initDue){
            val commands=BookooCodec.initializationCommands();val cmd=commands[initNext]
            initBusy=true
            step(GattOperation.Write(writeEndpoint,cmd.frame.toByteArray(),withResponse),5000){
                initBusy=false;initNext++
                if(initNext==commands.size){setState(DeviceState.SYNCHRONIZING);stageDeadline=clock()+5000}
                else initDue=clock()+500
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
        return role==DeviceRole.COFFEE && state==DeviceState.READY && observedAt<=now &&
            now-observedAt<=1500 && idle.sleepStateRaw==0 && idle.alarmBits and 0xBFFF==0
    }
    private fun canControlWithFreshSettings():Boolean =
        canControlFromIdle() && SettingsFreshness.isFresh(lastSettingsAtMs,clock())
    private fun canCancelBrewWait():Boolean {
        val idle=lastIdle ?: return false
        val observedAt=lastIdleAtMs ?: return false
        val now=clock()
        // Cancellation is allowed during an alarm, but never from stale or extraction telemetry.
        return role==DeviceRole.COFFEE && state==DeviceState.READY && observedAt<=now &&
            now-observedAt<=1500 && idle.sleepStateRaw==0
    }
    fun startExtraction(parameters:StartParameters,callback:(OperationResult)->Unit) {
        if (sleepWrite != null) { callback(OperationResult.Failed("weekly sleep write active")); return }
        if (!canControlWithFreshSettings()) {
            callback(OperationResult.Failed("fresh awake idle and settings telemetry required")); return
        }
        // Product host supplies only frames checked against the extracted legacy encoder.
        val command=CoffeeCommands.start(parameters)
        val allowed=setOf("02175B006C005A410000015E1600AA00000000DA","02DF5C0046001426140000A0050190008C000059","02DF5C00880014231200009605019000820000AC")
        if(command.frame.hex() !in allowed && command.frame.hex() !in additionalStartFrames){
            callback(OperationResult.Failed("curve outside validated profile set"));return
        }
        send(command,DeviceRole.COFFEE,beforeDispatch=::canControlWithFreshSettings,callback=callback)
    }
    fun stopExtraction(slot:Int=7,callback:(OperationResult)->Unit) {
        require(slot in 1..5 || slot == 7)
        if (role!=DeviceRole.COFFEE) {
            callback(OperationResult.Failed("stop requires coffee session"));return
        }
        cancelSleepWrite("stop requested during weekly sleep write")
        // An emergency stop must not be followed by older, still-queued control writes.
        queue.cancelPending { it is GattOperation.Write }
        send(CoffeeCommands.stop(slot),DeviceRole.COFFEE,urgent=true,callback=callback)
    }
    fun tare(callback:(OperationResult)->Unit)=send(BookooCodec.tare(),DeviceRole.BOOKOO,callback=callback)
    private fun sendFromIdle(command:EncodedCommand,callback:(OperationResult)->Unit) {
        if (!canControlFromIdle()) {
            callback(OperationResult.Failed("fresh awake idle telemetry without blocking alarms required"))
            return
        }
        send(command,DeviceRole.COFFEE,beforeDispatch=::canControlFromIdle,callback=callback)
    }
    private fun sendWithFreshSettings(command:EncodedCommand,callback:(OperationResult)->Unit) {
        if (!canControlWithFreshSettings()) {
            callback(OperationResult.Failed("fresh awake idle and settings telemetry required"))
            return
        }
        send(command,DeviceRole.COFFEE,beforeDispatch=::canControlWithFreshSettings,callback=callback)
    }
    private fun canWriteSetting(change: MachineSettingChange): Boolean =
        canControlWithFreshSettings() && when(change) {
            is MachineSettingChange.StandbyDelay -> lastSettings?.standbyTemperatureC == change.temperatureC
            is MachineSettingChange.StandbyTemperature -> lastSettings?.standbyMinutes == change.minutes
            is MachineSettingChange.SleepScheduleEnabled -> !change.enabled ||
                SleepScheduleFreshness.isFresh(sleepFirst,sleepFirstAtMs,sleepSecond,sleepSecondAtMs,clock())
            else -> true
        }
    fun writeSetting(change: MachineSettingChange,callback:(OperationResult)->Unit) {
        if (sleepWrite != null) callback(OperationResult.Failed("weekly sleep write active"))
        else if (!canWriteSetting(change)) callback(OperationResult.Failed("fresh settings with unchanged companion value required"))
        else {
            val enablingPlan = if(change is MachineSettingChange.SleepScheduleEnabled && change.enabled)
                WeeklySleepSchedule.fromReadback(sleepFirst,sleepSecond)?.days else null
            send(CoffeeCommands.setting(change),DeviceRole.COFFEE,
                beforeDispatch={canWriteSetting(change) &&
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
    fun writeSleepSchedule(schedule: WeeklySleepSchedule, expected:WeeklySleepSchedule, callback:(OperationResult)->Unit) {
        if (sleepWrite!=null || !canBeginSleepWrite(expected)) {
            callback(OperationResult.Failed("fresh unchanged full sleep readback and awake idle required"));return
        }
        val write=SleepWrite(CoffeeCommands.sleepSchedule(schedule),generation,expected,schedule,
            minOf(sleepFirstAtMs!!,sleepSecondAtMs!!),callback)
        sleepWrite=write
        send(write.frames[0],DeviceRole.COFFEE,beforeDispatch={canBeginSleepWrite(expected)}) { result ->
            if (result is OperationResult.Success) {
                if (sleepWrite === write) {
                    write.secondDue=clock()+500
                }
            } else finishSleepWrite(write,result)
        }
    }
    fun enterSleep(callback:(OperationResult)->Unit) {
        if (sleepWrite != null) callback(OperationResult.Failed("weekly sleep write active"))
        else sendFromIdle(CoffeeCommands.sleepNow(),callback)
    }
    private fun canResetCupCount(expectedCount:Int):Boolean =
        expectedCount in 1..65535 && canControlWithFreshSettings() &&
            lastSettings?.cupCount == expectedCount && lastIdle?.cupCount == expectedCount
    fun resetCupCount(expectedCount:Int,callback:(OperationResult)->Unit) {
        if (sleepWrite != null) callback(OperationResult.Failed("weekly sleep write active"))
        else if (!canResetCupCount(expectedCount))
            callback(OperationResult.Failed("fresh matching settings and idle cup count required"))
        else send(CoffeeCommands.resetCupCount(),DeviceRole.COFFEE,
            beforeDispatch={canResetCupCount(expectedCount)},callback=callback)
    }
    fun setBrewWait(targetC:Int,callback:(OperationResult)->Unit) {
        if (sleepWrite != null) callback(OperationResult.Failed("weekly sleep write active"))
        else if (targetC==0) {
            if (!canCancelBrewWait()) callback(OperationResult.Failed("fresh awake idle telemetry required for preheat cancel"))
            else send(CoffeeCommands.brewWait(0),DeviceRole.COFFEE,
                beforeDispatch=::canCancelBrewWait,callback=callback)
        }
        else sendWithFreshSettings(CoffeeCommands.brewWait(targetC),callback)
    }
    fun disconnect(){activeAddress=null;lastIdle=null;lastIdleAtMs=null;lastSettingsAtMs=null;lastSettings=null;clearSleepReadback();setState(DeviceState.DISCONNECTED);cancelSleepWrite("coffee disconnected");queue.disconnect("user disconnect");initBusy=false}
    private fun fail(reason:String){activeAddress=null;lastIdle=null;lastIdleAtMs=null;lastSettingsAtMs=null;lastSettings=null;clearSleepReadback();setState(DeviceState.FAILED);cancelSleepWrite(reason);queue.disconnect(reason);diagnostic(reason)}
}
