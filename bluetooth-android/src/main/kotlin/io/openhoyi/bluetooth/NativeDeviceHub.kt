package io.openhoyi.bluetooth

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.openhoyi.protocol.*
import io.openhoyi.session.*

/** Service/application-owned integration. The host persists only addresses reported as successfully ready. */
class NativeDeviceHub(context:Context,rememberedScaleAddress:String?=null,
    private val onScaleRemembered:(String)->Unit={},
    private val onState:(DeviceRole,DeviceState)->Unit={_,_->},
    private val onCoffee:(HoyiMessage)->Unit={},private val onWeight:(BookooSample)->Unit={},
    private val diagnostic:(String)->Unit={},
    trace:(DeviceRole,WireTrace)->Unit={_,_->},
    legacyVerifiedStartFrames:Set<String> = emptySet()) : AutoCloseable {
    init {check(Looper.myLooper()==Looper.getMainLooper())}
    private val handler=Handler(Looper.getMainLooper())
    private var remembered=rememberedScaleAddress
    private var candidate:String?=null
    private var closed=false
    private val reconnect=ReconnectPolicy()
    val scanner=ScanCoordinator(context)
    private val coffee:AndroidDevice=AndroidDevice(context,DeviceRole.COFFEE,
        stateChanged={onState(DeviceRole.COFFEE,it)},
        coffeeFrame={frame,time->extraction.machineFrame(frame,time);onCoffee(frame)},diagnostic=diagnostic,
        trace={trace(DeviceRole.COFFEE,it)},legacyVerifiedStartFrames=legacyVerifiedStartFrames)
    private val scale:AndroidDevice=AndroidDevice(context,DeviceRole.BOOKOO,
        stateChanged={state->
            if(state in listOf(DeviceState.DISCONNECTED,DeviceState.FAILED,DeviceState.UNSUPPORTED))extraction.scaleDisconnected()
            if(state==DeviceState.READY){candidate?.let{remembered=it;onScaleRemembered(it)};reconnect.attemptFinished(SystemClock.elapsedRealtime())}
            if(state==DeviceState.FAILED)reconnect.attemptFinished(SystemClock.elapsedRealtime())
            onState(DeviceRole.BOOKOO,state)
        },weightFrame={sample,time->extraction.weight(WeightReading(sample.weightHundredthsGram,time));onWeight(sample)},diagnostic=diagnostic,trace={trace(DeviceRole.BOOKOO,it)})
    val extraction:ExtractionController=ExtractionController(CoffeeSessionControl(coffee.session),ScaleSessionControl(scale.session),{SystemClock.elapsedRealtime()})
    private val ticker=object:Runnable {
        override fun run(){
            if(closed)return
            extraction.tick()
            val idleScale=scale.session.state in listOf(DeviceState.DISCONNECTED,DeviceState.FAILED)
            if(idleScale&&reconnect.shouldAttempt(SystemClock.elapsedRealtime(),coffee.session.state==DeviceState.READY,false)) {
                remembered?.let{connectScale(it)}?:reconnect.manualDisconnect()
            }
            handler.postDelayed(this,50)
        }
    }
    init {handler.post(ticker)}
    private fun usable(){check(Looper.myLooper()==Looper.getMainLooper());check(!closed){"Hub closed"}}
    fun connectCoffee(address:String,authentication:CoffeeAuthentication){usable();coffee.session.connect(address,authentication)}
    fun connectScale(address:String){usable();require(android.bluetooth.BluetoothAdapter.checkBluetoothAddress(address)){"Invalid Bluetooth address"};candidate=address;scale.session.connect(address)}
    fun foreground(){usable();reconnect.foreground(SystemClock.elapsedRealtime(),remembered!=null)}
    fun background(){usable();reconnect.background();scanner.close()}
    fun disconnectScale(){usable();reconnect.manualDisconnect();scale.session.disconnect()}
    fun tareScale(done:(OperationResult)->Unit){usable();scale.session.tare(done)}
    fun writeSetting(change:MachineSettingChange,done:(OperationResult)->Unit){
        usable()
        if(extraction.state in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED,ExtractionState.OUTCOME_UNKNOWN)){
            done(OperationResult.Failed("extraction active"));return
        }
        coffee.session.writeSetting(change,done)
    }
    fun writeSleepSchedule(schedule:WeeklySleepSchedule,done:(OperationResult)->Unit){
        usable()
        if(extraction.state in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED,ExtractionState.OUTCOME_UNKNOWN)){
            done(OperationResult.Failed("extraction active"));return
        }
        coffee.session.writeSleepSchedule(schedule,done)
    }
    fun enterSleep(done:(OperationResult)->Unit){
        usable()
        if(extraction.state in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED,ExtractionState.OUTCOME_UNKNOWN)){
            done(OperationResult.Failed("extraction active"));return
        }
        coffee.session.enterSleep(done)
    }
    fun setBrewWait(targetC:Int,done:(OperationResult)->Unit){
        usable()
        if(extraction.state in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,
                ExtractionState.STOP_REQUESTED,ExtractionState.OUTCOME_UNKNOWN)){
            done(OperationResult.Failed("extraction active"));return
        }
        coffee.session.setBrewWait(targetC,done)
    }
    fun disconnectCoffee(){usable();coffee.session.disconnect()}
    override fun close(){
        usable();closed=true;handler.removeCallbacks(ticker);reconnect.background();scanner.close();coffee.close();scale.close()
    }
}
