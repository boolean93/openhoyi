package io.openhoyi.bluetooth

import android.content.Context
import android.bluetooth.BluetoothManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.openhoyi.protocol.*
import io.openhoyi.session.*

/** Service/application-owned integration. The host persists only addresses reported as successfully ready. */
class NativeDeviceHub(private val context:Context,rememberedScaleAddress:String?=null,
    private val onScaleRemembered:(String)->Unit={},
    private val onState:(DeviceRole,DeviceState)->Unit={_,_->},
    private val onCoffee:(HoyiMessage)->Unit={},private val onWeight:(BookooSample)->Unit={},
    private val diagnostic:(String)->Unit={},
    private val trace:(DeviceRole,WireTrace)->Unit={_,_->},
    legacyVerifiedStartFrames:Set<String> = emptySet(),
    tareStorage:StandaloneTare.Storage? = null,
    private val onScaleObservation:(ScaleObservation)->Unit = {},
    rememberedScaleProtocolId:String?=null,
    private val onScaleSelectionRemembered:(address:String,protocolId:String)->Unit={_,_->},
    customPressureStartPermit:(StartParameters)->Boolean={false},
    customCurveStartPermit:(StartParameters)->Boolean={false},
    private val onCoffeeReconnect:(String)->Boolean={false},
    rememberedScaleSuppressed:Boolean=false,
    private val automaticAllowed:(DeviceRole)->Boolean={true}) : AutoCloseable {
    init {check(Looper.myLooper()==Looper.getMainLooper())}
    private val handler=Handler(Looper.getMainLooper())
    private var remembered=ScaleSelectionPolicy.remembered(rememberedScaleAddress,rememberedScaleProtocolId)
    private var candidate:ScaleSelection?=null
    private var scaleRequest=0L
    private class ScaleOwner(val device:AndroidDevice,val control:ScaleSessionControl):AutoCloseable {
        override fun close()=device.close()
    }
    private val scaleSlot=SingleScaleConnectionSlot<ScaleOwner>()
    private var automaticScaleAttempts=0
    private var closed=false
    private var automaticScaleConnecting=false
    private var automaticCoffeeConnecting=false
    private var foreground=false
    private var coffeeWasReady=false
    private var scaleWasReady=false
    private var coffeeRecoveryAttempts=0
    private var coffeeRecoveryBlocked=false
    private val standaloneTare=if(tareStorage==null)StandaloneTare { SystemClock.elapsedRealtime() }
        else StandaloneTare(tareStorage) { SystemClock.elapsedRealtime() }
    private var scaleSampleSerial=0L
    val scaleTareState get()=standaloneTare.state
    private val reconnect=ReconnectPolicy().apply { if(rememberedScaleSuppressed)manualDisconnect() }
    private val coffeeReconnect=CoffeeReconnectPolicy()
    val scanner=ScanCoordinator(context)
    private val coffee:AndroidDevice by lazy { AndroidDevice(context,DeviceRole.COFFEE,
        stateChanged={state->
            val now=SystemClock.elapsedRealtime()
            if(state==DeviceState.READY && !coffeeWasReady)reconnect.requestSoon(now)
            coffeeWasReady=state==DeviceState.READY
            if(state in listOf(DeviceState.READY,DeviceState.FAILED,DeviceState.UNSUPPORTED))automaticCoffeeConnecting=false
            when(state){
                DeviceState.READY->coffee.session.address?.let { coffeeReconnect.rememberReady(it,now) }
                DeviceState.FAILED->coffeeReconnect.connectionFailed(now)
                DeviceState.UNSUPPORTED->coffeeReconnect.disable()
                else->Unit
            }
            onState(DeviceRole.COFFEE,state)
        },
        coffeeFrame={frame,time->extraction.machineFrame(frame,time);onCoffee(frame)},diagnostic=diagnostic,
        trace={trace(DeviceRole.COFFEE,it)},legacyVerifiedStartFrames=legacyVerifiedStartFrames,
        customPressureStartPermit=customPressureStartPermit,customCurveStartPermit=customCurveStartPermit) }
    private val coffeeControl=CoffeeSessionControl(coffee.session)
    private val scaleControl=DynamicScaleControl({scaleSlot.current?.owner?.control},{standaloneTare.unresolved})
    val extraction:ExtractionController=ExtractionController(coffeeControl,scaleControl,{SystemClock.elapsedRealtime()})
    // Retain the current-device reflection contract used by lifecycle diagnostics.
    private var scale:AndroidDevice=scaleSlot.replace(ScaleSelection("",BookooScaleProtocolAdapter.id)) {
        createScaleOwner(it,BookooScaleProtocolAdapter)
    }.owner.device
    private fun ownsScale(ticket:Long,request:Long=scaleRequest)=!closed && request==scaleRequest && scaleSlot.owns(ticket)
    private fun createScaleOwner(ticket:Long,adapter:ScaleProtocolAdapter):ScaleOwner {
        val device=AndroidDevice(context,DeviceRole.BOOKOO,
            stateChanged={state->
                val request=scaleRequest
                if(ownsScale(ticket,request)) {
                    if(foreground && state==DeviceState.READY && !scaleWasReady && coffeeReconnect.requestSoon(SystemClock.elapsedRealtime()))
                        diagnostic("coffee.auto_reconnect.trigger=scale_ready")
                    scaleWasReady=state==DeviceState.READY
                    if(state in listOf(DeviceState.READY,DeviceState.FAILED,DeviceState.UNSUPPORTED))automaticScaleConnecting=false
                    if(state!=DeviceState.READY)standaloneTare.disconnected()
                    if(ownsScale(ticket,request) && state!=DeviceState.READY)
                        extraction.scaleDisconnected()
                    if(ownsScale(ticket,request) && state==DeviceState.READY) {
                        val selected=scaleSlot.current?.selection
                        if(selected!=null && selected==candidate && selected.address==scaleSlot.current?.owner?.device?.session?.address) {
                            remembered=selected
                            onScaleSelectionRemembered(selected.address,selected.protocolId)
                            if(ownsScale(ticket,request) && scaleSlot.current?.owner?.device?.session?.state==DeviceState.READY && adapter.id==BookooScaleProtocolAdapter.id)
                                onScaleRemembered(selected.address)
                        }
                    }
                    if(ownsScale(ticket,request))onState(DeviceRole.BOOKOO,state)
                }
            },weightFrame={sample,_->if(ownsScale(ticket))onWeight(sample)},
            scaleObservation={sample->
                val request=scaleRequest
                if(ownsScale(ticket,request)) {
                    if(sample.evidence in setOf(ScaleEvidence.VERIFIED_TRANSPORT,ScaleEvidence.DOCUMENTED_TRANSPORT) &&
                        sample.capabilities.weight && sample.capabilities.tare)
                        standaloneTare.sample(++scaleSampleSerial,sample.hundredthsGram)
                    if(ownsScale(ticket,request))extraction.weight(sample)
                    if(ownsScale(ticket,request))onScaleObservation(sample)
                }
            },diagnostic={if(ownsScale(ticket))diagnostic(it)},trace={if(ownsScale(ticket))trace(DeviceRole.BOOKOO,it)},scaleAdapter=adapter)
        return ScaleOwner(device,ScaleSessionControl(device.session,standaloneTare,{scaleSampleSerial}))
    }
    val scaleCapabilities:ScaleCapabilities get()=scaleControl.capabilities
    val scaleProtocolId:String? get()=scaleSlot.current?.selection?.protocolId
    val scaleAddress:String? get()=scaleSlot.current?.owner?.device?.session?.let { it.address.takeIf { _->it.state==DeviceState.READY } }
    val coffeeFirmware:CoffeeFirmware? get()=coffee.session.observedFirmware
    /** Display-only classification of the last FAILED coffee session; never a control permit. */
    val coffeeFailure:CoffeeSessionFailure? get()=coffee.session.lastFailure
    val coffeeAddress:String? get()=coffee.session.address.takeIf { coffee.session.state==DeviceState.READY }
    private fun bluetoothReady():Boolean=scanner.environmentReady()
    val environmentReady:Boolean get()=bluetoothReady()
    val automaticScanning:Boolean get()=scanner.automaticScanning
    /** Display-only: automatic reconnect is armed and still looking for the remembered scale. */
    val automaticScaleWaiting:Boolean get(){
        val state=scaleSlot.current?.owner?.device?.session?.state?:DeviceState.DISCONNECTED
        return foreground && environmentReady && remembered!=null && reconnect.recoveryEnabled && !scaleSlot.blocked &&
            state!=DeviceState.READY && state!=DeviceState.CONNECTING && state!=DeviceState.UNSUPPORTED
    }
    private val ticker=object:Runnable {
        override fun run(){
            if(closed)return
            extraction.tick()
            val now=SystemClock.elapsedRealtime()
            standaloneTare.tick()
            // Poll the terminal state after connectScale returns: DeviceSession.connect emits a
            // synchronous DISCONNECTED reset before CONNECTING, which is not a failed attempt.
            val state=scaleSlot.current?.owner?.device?.session?.state ?: DeviceState.DISCONNECTED
            reconnect.observeScaleState(now,state)
            val coffeeAddress=coffeeReconnect.desiredAddress
            if(coffeeAddress!=null && coffee.session.state in listOf(DeviceState.DISCONNECTED,DeviceState.FAILED)) {
                val permitted=automaticAllowed(DeviceRole.COFFEE) && DeviceConnectionGate.mayConnectCoffee(extraction.state,coffeeControl.startAddress,coffeeAddress)
                if(foreground && !permitted && !coffeeRecoveryBlocked)diagnostic("coffee.auto_reconnect.blocked=intent_or_safety")
                coffeeRecoveryBlocked=!permitted
                val result=coffeeReconnect.dispatch(now,
                    permitted,
                    bluetoothReady(),onCoffeeReconnect)
                when(result){
                    CoffeeReconnectPolicy.Dispatch.STARTED->diagnostic("coffee.auto_reconnect.attempt=${++coffeeRecoveryAttempts}")
                    CoffeeReconnectPolicy.Dispatch.UNAVAILABLE->diagnostic("coffee.auto_reconnect.unavailable")
                    CoffeeReconnectPolicy.Dispatch.REJECTED->diagnostic("coffee.auto_reconnect.deferred")
                    CoffeeReconnectPolicy.Dispatch.NONE->Unit
                }
            }
            val idleScale=state in listOf(DeviceState.DISCONNECTED,DeviceState.FAILED)
            if(!scaleSlot.blocked&&idleScale&&reconnect.recoveryEnabled&&automaticAllowed(DeviceRole.BOOKOO)&&!scanner.manualScanning&&DeviceConnectionGate.mayChangeScale(extraction.state)) {
                val selection=remembered
                if(selection==null) reconnect.manualDisconnect()
                else {
                    if(!bluetoothReady()) {
                        if(reconnect.environmentUnavailable())diagnostic("scale.auto_reconnect.unavailable")
                    } else {
                        reconnect.environmentAvailable()
                    }
                    if(reconnect.shouldAttempt(now,false))try {
                        val request=scaleRequest
                        diagnostic("scale.auto_reconnect.attempt=${++automaticScaleAttempts}")
                        scanner.start(AutoConnectTiming.SCAN_MS,ScanCoordinator.Owner.AUTOMATIC,selection,
                            onDevice={ found ->
                                if(found.address.equals(selection.address,true) && found.scaleProtocolId==selection.protocolId &&
                                    !closed && foreground && request==scaleRequest && remembered==selection &&
                                    automaticAllowed(DeviceRole.BOOKOO) && DeviceConnectionGate.mayChangeScale(extraction.state) &&
                                    scaleSlot.current?.owner?.device?.session?.state in listOf(DeviceState.DISCONNECTED,DeviceState.FAILED)) {
                                    scanner.cancel(ScanCoordinator.Owner.AUTOMATIC)
                                    connectScale(selection.address,selection.protocolId,explicit=false)
                                }
                            },onFinished={reconnect.attemptFinished(SystemClock.elapsedRealtime())})
                    }catch(error:RuntimeException) {
                        reconnect.attemptFinished(SystemClock.elapsedRealtime())
                        diagnostic("remembered scale reconnect failed: ${error.javaClass.simpleName}")
                    }
                }
            }
            if(!closed)handler.postDelayed(this,50)
        }
    }
    init {handler.post(ticker)}
    private fun usable(){check(Looper.myLooper()==Looper.getMainLooper());check(!closed){"Hub closed"}}
    fun connectCoffee(address:String,authentication:CoffeeAuthentication,explicit:Boolean=true,remembered:Boolean=false){
        usable()
        check(DeviceConnectionGate.mayConnectCoffee(extraction.state,coffeeControl.startAddress,address)) {
            "unsettled extraction: coffee connection change blocked"
        }
        automaticCoffeeConnecting=!explicit
        if(explicit)coffeeReconnect.explicitConnect(address,SystemClock.elapsedRealtime(),remembered)
        coffee.session.connect(address,authentication)
    }
    fun connectScale(address:String,protocolId:String=BookooScaleProtocolAdapter.id,explicit:Boolean=true):Boolean {
        usable()
        check(DeviceConnectionGate.mayChangeScale(extraction.state)){"unsettled extraction: scale connection change blocked"}
        require(android.bluetooth.BluetoothAdapter.checkBluetoothAddress(address)){"Invalid Bluetooth address"}
        val adapter=ScaleSelectionPolicy.adapter(protocolId) ?: throw IllegalArgumentException("Unknown scale protocol")
        val selection=ScaleSelection(address,protocolId)
        if(candidate==selection && scaleSlot.current?.owner?.device?.session?.state !in listOf(
                null,DeviceState.DISCONNECTED,DeviceState.FAILED,DeviceState.UNSUPPORTED))return false
        if(explicit){scanner.cancel(ScanCoordinator.Owner.AUTOMATIC);reconnect.explicitConnect(SystemClock.elapsedRealtime())}
        val request=++scaleRequest
        automaticScaleConnecting=!explicit
        candidate=selection
        standaloneTare.disconnected()
        extraction.scaleDisconnected()
        val lease=try {scaleSlot.replace(selection) {createScaleOwner(it,adapter)}} catch(error:Exception) {
            candidate=null
            if(scaleSlot.blocked)reconnect.manualDisconnect()
            if(!closed && scaleRequest==request)onState(DeviceRole.BOOKOO,DeviceState.FAILED)
            throw error
        }
        scale=lease.owner.device
        if(!ownsScale(lease.ticket,request))return false
        scale.session.connect(address)
        return ownsScale(lease.ticket,request)
    }
    /** Caller owns durable preference removal; transport callbacks cannot revive an old scan. */
    fun setRememberedScale(selection:ScaleSelection?){
        usable()
        check(DeviceConnectionGate.mayChangeScale(extraction.state))
        if(selection==remembered)return
        require(selection==null || ScaleSelectionPolicy.remembered(selection.address,selection.protocolId)==selection)
        scanner.cancel(ScanCoordinator.Owner.AUTOMATIC)
        if(automaticScaleConnecting){
            automaticScaleConnecting=false;++scaleRequest;candidate=null
            scaleSlot.current?.owner?.device?.session?.disconnect()
        }
        remembered=selection
        reconnect.targetChanged(selection!=null,SystemClock.elapsedRealtime())
    }
    fun foreground(){
        usable();foreground=true;automaticScaleAttempts=0
        val now=SystemClock.elapsedRealtime()
        reconnect.foreground(now,remembered!=null)
        coffeeReconnect.foreground(now)
        diagnostic("coffee.auto_reconnect.trigger=foreground")
        if(remembered!=null)diagnostic("scale.auto_reconnect.waiting")
    }
    fun background(keepCoffeeRecovery:Boolean=false){usable();foreground=false;cancelAutomaticConnections();reconnect.background();if(!keepCoffeeRecovery)coffeeReconnect.background();scanner.close();diagnostic("scale.auto_reconnect.window_closed")}
    private fun cancelAutomaticConnections(){
        if(automaticScaleConnecting && !automaticAllowed(DeviceRole.BOOKOO)){
            automaticScaleConnecting=false;++scaleRequest;candidate=null
            scaleSlot.current?.owner?.device?.session?.disconnect()
            reconnect.attemptFinished(SystemClock.elapsedRealtime())
        }
        if(automaticCoffeeConnecting && !automaticAllowed(DeviceRole.COFFEE)){
            automaticCoffeeConnecting=false;coffee.session.disconnect()
            coffeeReconnect.connectionFailed(SystemClock.elapsedRealtime())
        }
    }
    fun refreshAutomaticIntent(scalePaused:Boolean,coffeePaused:Boolean){
        usable()
        cancelAutomaticConnections()
        reconnect.setPaused(scalePaused)
        coffeeReconnect.setPaused(coffeePaused)
        if(!automaticAllowed(DeviceRole.BOOKOO)||!bluetoothReady())scanner.cancel(ScanCoordinator.Owner.AUTOMATIC)
    }
    fun requestCoffeeRecoverySoon(){usable();if(foreground && coffeeReconnect.requestSoon(SystemClock.elapsedRealtime()))diagnostic("coffee.auto_reconnect.trigger=interaction")}
    fun disableCoffeeRecovery(){usable();coffeeReconnect.disable()}
    fun ensureKnownCoffee(address:String){
        usable();require(android.bluetooth.BluetoothAdapter.checkBluetoothAddress(address))
        if(coffee.session.state in listOf(DeviceState.DISCONNECTED,DeviceState.FAILED))
            coffeeReconnect.ensureKnown(address,SystemClock.elapsedRealtime())
    }
    fun disconnectScale(){
        usable();check(DeviceConnectionGate.mayDisconnect(extraction.state)){"unsettled extraction: disconnect blocked"}
        ++scaleRequest;scanner.cancel(ScanCoordinator.Owner.AUTOMATIC);candidate=null;reconnect.manualDisconnect();scaleSlot.current?.owner?.device?.session?.disconnect()
    }
    fun tareScale(done:(OperationResult)->Unit)=tareScale({true},done)
    fun tareScale(beforeDispatch:()->Boolean,done:(OperationResult)->Unit){
        usable()
        if(!DeviceConnectionGate.mayChangeScale(extraction.state)){
            done(OperationResult.Failed("unsettled extraction: manual tare blocked"));return
        }
        scaleControl.tare({beforeDispatch() && DeviceConnectionGate.mayChangeScale(extraction.state)},done)
    }
    fun writeSetting(change:MachineSettingChange,done:(OperationResult)->Unit)=writeSetting(change,{true},done)
    fun writeSetting(change:MachineSettingChange,beforeDispatch:()->Boolean,done:(OperationResult)->Unit){
        usable()
        if(extraction.state in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED,ExtractionState.OUTCOME_UNKNOWN)){
            done(OperationResult.Failed("extraction active"));return
        }
        coffee.session.writeSetting(change,beforeDispatch,done)
    }
    fun writeSleepSchedule(schedule:WeeklySleepSchedule,expected:WeeklySleepSchedule,done:(OperationResult)->Unit)=writeSleepSchedule(schedule,expected,{true},done)
    fun writeSleepSchedule(schedule:WeeklySleepSchedule,expected:WeeklySleepSchedule,beforeDispatch:()->Boolean,done:(OperationResult)->Unit){
        usable()
        if(extraction.state in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED,ExtractionState.OUTCOME_UNKNOWN)){
            done(OperationResult.Failed("extraction active"));return
        }
        coffee.session.writeSleepSchedule(schedule,expected,beforeDispatch,done)
    }
    fun enterSleep(done:(OperationResult)->Unit)=enterSleep({true},done)
    fun enterSleep(beforeDispatch:()->Boolean,done:(OperationResult)->Unit){
        usable()
        if(extraction.state in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED,ExtractionState.OUTCOME_UNKNOWN)){
            done(OperationResult.Failed("extraction active"));return
        }
        coffee.session.enterSleep(beforeDispatch,done)
    }
    fun resetCupCount(expectedCount:Int,done:(OperationResult)->Unit)=resetCupCount(expectedCount,{true},done)
    fun resetCupCount(expectedCount:Int,beforeDispatch:()->Boolean,done:(OperationResult)->Unit){
        usable()
        if(extraction.state in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED,ExtractionState.OUTCOME_UNKNOWN)){
            done(OperationResult.Failed("extraction active"));return
        }
        coffee.session.resetCupCount(expectedCount,beforeDispatch,done)
    }
    fun setBrewWait(targetC:Int,beforeDispatch:()->Boolean,done:(OperationResult)->Unit){
        usable()
        if(extraction.state in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED,ExtractionState.OUTCOME_UNKNOWN)){
            done(OperationResult.Failed("extraction active"));return
        }
        coffee.session.setBrewWait(targetC,{beforeDispatch() && !DeviceConnectionGate.unsettled(extraction.state)},done)
    }
    fun disconnectCoffee(){
        usable();check(DeviceConnectionGate.mayDisconnect(extraction.state)){"unsettled extraction: disconnect blocked"}
        coffeeReconnect.manualDisconnect()
        coffee.session.disconnect()
    }
    override fun close(){
        usable();closed=true;++scaleRequest
        var failure: Exception? = null
        fun cleanup(action: () -> Unit) {
            try { action() } catch (error: Exception) {
                val first = failure
                if (first == null) failure = error
                else if (first !== error) first.addSuppressed(error)
            }
        }
        // One failing observer must not retain the other device or any owner timer.
        cleanup { handler.removeCallbacks(ticker) }
        cleanup { reconnect.background() }
        cleanup { scanner.close() }
        cleanup { coffee.close() }
        cleanup { standaloneTare.disconnected() }
        cleanup { extraction.scaleDisconnected() }
        cleanup { scaleSlot.close() }
        cleanup { onState(DeviceRole.BOOKOO,DeviceState.DISCONNECTED) }
        failure?.let { throw it }
    }
}
