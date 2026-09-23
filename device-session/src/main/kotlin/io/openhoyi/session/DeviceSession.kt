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
    private var notifyEndpoint=if(role==DeviceRole.COFFEE)KnownGatt.coffeeNotify else KnownGatt.bookooNotify
    private var writeEndpoint=if(role==DeviceRole.COFFEE)KnownGatt.coffeeWrite else KnownGatt.bookooWrite
    private var withResponse=true
    private var stageDeadline=0L
    private var initNext=0
    private var initDue=0L
    private var initBusy=false
    private var pendingSettings:Settings?=null
    private fun setState(value:DeviceState){state=value;stateChanged(value)}
    fun connect(address:String,authentication:CoffeeAuthentication?=null) {
        require(address.isNotBlank())
        require(role!=DeviceRole.COFFEE||authentication!=null){"coffee authentication required"}
        // Validate credentials before disturbing an existing connection.
        val auth=authentication?.encode()
        disconnect();queue.open();setState(DeviceState.CONNECTING);stageDeadline=clock()+22_000
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
                            pendingSettings?.let { acceptSettings(it) }
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
                if(frame is Settings){
                    if(state==DeviceState.INITIALIZING)pendingSettings=frame
                    if(state==DeviceState.SYNCHRONIZING||state==DeviceState.READY)acceptSettings(frame)
                }
                coffeeFrame(frame,now)
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
    private fun send(command:EncodedCommand,expectedRole:DeviceRole,urgent:Boolean=false,callback:(OperationResult)->Unit) {
        if(role!=expectedRole||state!=DeviceState.READY){callback(OperationResult.Failed("device not ready or wrong role"));return}
        queue.enqueue(GattOperation.Write(writeEndpoint,command.frame.toByteArray(),withResponse),5000,urgent,callback)
    }
    fun startExtraction(parameters:StartParameters,callback:(OperationResult)->Unit) {
        // Product host supplies only frames checked against the extracted legacy encoder.
        val command=CoffeeCommands.start(parameters)
        val allowed=setOf("02175B006C005A410000015E1600AA00000000DA","02DF5C0046001426140000A0050190008C000059","02DF5C00880014231200009605019000820000AC")
        if(command.frame.hex() !in allowed && command.frame.hex() !in additionalStartFrames){
            callback(OperationResult.Failed("curve outside validated profile set"));return
        }
        send(command,DeviceRole.COFFEE,callback=callback)
    }
    fun stopExtraction(slot:Int=7,callback:(OperationResult)->Unit) {
        require(slot in 1..5 || slot == 7)
        queue.cancelPending { it is GattOperation.Write && it.bytes.size==20 && it.bytes[0].toInt()==2 }
        send(CoffeeCommands.stop(slot),DeviceRole.COFFEE,true,callback)
    }
    fun tare(callback:(OperationResult)->Unit)=send(BookooCodec.tare(),DeviceRole.BOOKOO,callback=callback)
    fun disconnect(){setState(DeviceState.DISCONNECTED);queue.disconnect("user disconnect");pendingSettings=null;initBusy=false}
    private fun fail(reason:String){setState(DeviceState.FAILED);queue.disconnect(reason);pendingSettings=null;diagnostic(reason)}
}
