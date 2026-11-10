package io.openhoyi.session

private const val RECOVERY_BUDGET_MS=AutoConnectTiming.FAST_PHASE_MS

/** One remembered scale; a READY link's next physical failure starts a fresh recovery episode. */
class ReconnectPolicy {
    private var until=0L
    private var next=0L
    private var lastAttempt= -AutoConnectTiming.FAST_INTERVAL_MS
    fun requestSoon(now:Long){ next=maxOf(lastAttempt+AutoConnectTiming.FAST_INTERVAL_MS,now) }
    private var foreground=false
    private var hasRememberedScale=false
    private var unsupported=false
    private var suppressed=false
    private var environmentPaused=false
    private var attempting=false
    private var failures=0
    private var readyBeforeFailure=false
    private var failureEpisode=false
    val recoveryEnabled:Boolean get()=foreground && hasRememberedScale && !suppressed && !unsupported

    fun foreground(now:Long,hasRememberedScale:Boolean){
        foreground=true;this.hasRememberedScale=hasRememberedScale
        if(!failureEpisode && !readyBeforeFailure){until=now+RECOVERY_BUDGET_MS;next=now;failures=0}
    }
    fun targetChanged(present:Boolean,now:Long){
        hasRememberedScale=present;attempting=false;unsupported=false;readyBeforeFailure=false;failureEpisode=false
        until=now+RECOVERY_BUDGET_MS;next=now
    }
    fun background(){foreground=false;attempting=false}
    fun setPaused(value:Boolean){suppressed=value}
    fun manualDisconnect(){suppressed=true;attempting=false;readyBeforeFailure=false}
    fun disable(){unsupported=true;attempting=false}
    fun explicitConnect(now:Long?=null){
        suppressed=false;unsupported=false;attempting=false;failureEpisode=false;readyBeforeFailure=false
        if(now!=null){until=now+RECOVERY_BUDGET_MS;next=now;failures=0}
    }
    fun environmentUnavailable():Boolean {
        val changed=!environmentPaused;environmentPaused=true;return changed
    }
    fun environmentAvailable(){environmentPaused=false}
    fun shouldAttempt(now:Long,scaleReady:Boolean):Boolean {
        if(!foreground||!hasRememberedScale||suppressed||unsupported||environmentPaused||attempting||
            readyBeforeFailure||scaleReady||now<next||now<lastAttempt+AutoConnectTiming.FAST_INTERVAL_MS)return false
        attempting=true;lastAttempt=now;return true
    }
    fun observeScaleState(now:Long,state:DeviceState){
        when(state){
            DeviceState.READY->{
                hasRememberedScale=true;readyBeforeFailure=true;failureEpisode=false;attempting=false;failures=0;next=now+5000
            }
            DeviceState.FAILED->{
                if(readyBeforeFailure){
                    readyBeforeFailure=false;failureEpisode=true
                    until=now+RECOVERY_BUDGET_MS;next=now;failures=0
                }
                if(attempting)finish(now,false)
            }
            DeviceState.DISCONNECTED->if(attempting)finish(now,false)
            DeviceState.UNSUPPORTED->disable()
            else->Unit
        }
    }
    fun attemptFinished(now:Long)=finish(now,false)
    private fun finish(now:Long,succeeded:Boolean){
        if(!attempting)return
        attempting=false
        if(succeeded){failures=0;next=now+5000;return}
        failures=(failures+1).coerceAtMost(4)
        next=lastAttempt+if(now<until)AutoConnectTiming.FAST_INTERVAL_MS else AutoConnectTiming.WAIT_INTERVAL_MS
    }
}

/** Only the remembered machine is eligible; ordinary activity never overrides user or credential blocks. */
class CoffeeReconnectPolicy {
    enum class Dispatch { NONE, STARTED, REJECTED, UNAVAILABLE }
    var desiredAddress:String?=null
        private set
    private var next=0L
    private var fastUntil=0L
    private var lastAttempt= -2_000L
    private var foreground=false
    private var failed=false
    private var suppressed=false
    private var credentialBlocked=false
    private var environmentPaused=false
    private var attempting=false
    private var failures=0
    private var readyBeforeFailure=false
    val recoveryEnabled:Boolean get()=foreground && failed && !suppressed && !credentialBlocked && desiredAddress!=null

    fun foreground(now:Long){foreground=true;requestSoon(now)}
    fun background(){foreground=false}
    /** Visibility, user interaction and a newly READY scale can shorten waiting, never reset intent. */
    fun requestSoon(now:Long):Boolean {
        if(!recoveryEnabled || environmentPaused || attempting)return false
        val earlier=minOf(next,maxOf(now,lastAttempt+2_000))
        val changed=earlier<next;next=earlier;return changed
    }
    fun ensureKnown(address:String,now:Long){
        if(suppressed||credentialBlocked||desiredAddress!=null)return
        desiredAddress=address;failed=true;readyBeforeFailure=false
        next=now;fastUntil=now+30_000;failures=0
    }
    fun rememberReady(address:String,now:Long){
        desiredAddress=address;failed=false;readyBeforeFailure=true
        attempting=false;failures=0;next=now+5_000
    }
    fun connectionFailed(now:Long){
        if(desiredAddress==null||suppressed)return
        if(readyBeforeFailure){
            readyBeforeFailure=false;next=now;fastUntil=now+30_000;failures=0
        }else if(attempting)scheduleFailure(now)
        attempting=false;failed=true
    }
    private fun scheduleFailure(now:Long){
        failures=(failures+1).coerceAtMost(3)
        val delay=if(now<fastUntil)when(failures){1->2_000L;2->3_000L;else->5_000L} else 30_000L
        next=now+delay
    }
    fun shouldAttempt(now:Long):String? {
        if(!recoveryEnabled||environmentPaused||attempting||now<next)return null
        val address=desiredAddress ?: return null
        attempting=true;lastAttempt=now;return address
    }
    fun dispatch(now:Long,canConnect:Boolean,environmentReady:Boolean,connect:(String)->Boolean):Dispatch {
        if(!canConnect||!recoveryEnabled)return Dispatch.NONE
        if(!environmentReady)return if(environmentUnavailable())Dispatch.UNAVAILABLE else Dispatch.NONE
        environmentAvailable()
        val address=shouldAttempt(now) ?: return Dispatch.NONE
        val accepted=runCatching {connect(address)}.getOrDefault(false)
        if(!accepted){rejected(now);return Dispatch.REJECTED}
        return Dispatch.STARTED
    }
    fun rejected(now:Long){if(attempting){attempting=false;scheduleFailure(now)}}
    fun environmentUnavailable():Boolean {
        val changed=!environmentPaused;environmentPaused=true;return changed
    }
    fun environmentAvailable(){environmentPaused=false}
    fun disable(){credentialBlocked=true;attempting=false}
    fun setPaused(value:Boolean){
        if(suppressed && !value && desiredAddress!=null){failed=true;readyBeforeFailure=false}
        suppressed=value
    }
    fun manualDisconnect(){suppressed=true;failed=false;attempting=false}
    /** Only an already remembered target can recover; new credentials stay with their original owner. */
    fun explicitConnect(address:String,now:Long=0,remembered:Boolean=false){
        if(remembered)desiredAddress=address
        else if(!desiredAddress.equals(address,ignoreCase=true))desiredAddress=null
        suppressed=false;credentialBlocked=false;environmentPaused=false
        failed=false;readyBeforeFailure=false;attempting=desiredAddress!=null;failures=0
        lastAttempt=now;fastUntil=now+30_000;next=now
    }
}
