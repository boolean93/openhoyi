package io.openhoyi.session

private const val RECOVERY_BUDGET_MS=600_000L

/** One remembered scale; a READY link's next physical failure starts a fresh bounded episode. */
class ReconnectPolicy {
    private var until=0L
    private var next=0L
    private var foreground=false
    private var hasRememberedScale=false
    private var suppressed=false
    private var environmentPaused=false
    private var attempting=false
    private var failures=0
    private var readyBeforeFailure=false
    private var failureEpisode=false
    val recoveryEnabled:Boolean get()=foreground && hasRememberedScale && !suppressed

    fun foreground(now:Long,hasRememberedScale:Boolean){
        foreground=true;this.hasRememberedScale=hasRememberedScale
        if(!failureEpisode && !readyBeforeFailure){until=now+RECOVERY_BUDGET_MS;next=now;failures=0}
    }
    fun background(){foreground=false;attempting=false}
    fun manualDisconnect(){suppressed=true;attempting=false;readyBeforeFailure=false}
    fun disable(){suppressed=true;attempting=false}
    fun explicitConnect(now:Long?=null){
        suppressed=false;attempting=false;failureEpisode=false;readyBeforeFailure=false
        if(now!=null){until=now+RECOVERY_BUDGET_MS;next=now;failures=0}
    }
    fun environmentUnavailable():Boolean {
        val changed=!environmentPaused;environmentPaused=true;return changed
    }
    fun environmentAvailable(){environmentPaused=false}
    fun shouldAttempt(now:Long,scaleReady:Boolean):Boolean {
        if(!foreground||!hasRememberedScale||suppressed||environmentPaused||attempting||
            readyBeforeFailure||scaleReady||now>=until||now<next)return false
        attempting=true;return true
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
        next=now+(5000L shl (failures-1)).coerceAtMost(30_000)
    }
}

/** Only the remembered machine is eligible; repeated failures never extend one episode. */
class CoffeeReconnectPolicy {
    enum class Dispatch { NONE, STARTED, REJECTED, UNAVAILABLE }
    var desiredAddress:String?=null
        private set
    private var until=0L
    private var next=0L
    private var foreground=false
    private var failed=false
    private var suppressed=false
    private var credentialBlocked=false
    private var environmentPaused=false
    private var attempting=false
    private var failures=0
    private var readyBeforeFailure=false
    val recoveryEnabled:Boolean get()=foreground && failed && !suppressed && !credentialBlocked && desiredAddress!=null

    fun foreground(now:Long){foreground=true}
    fun background(){foreground=false;attempting=false}
    /** Called only for an explicitly foreground service restored from a saved READY address. */
    fun ensureKnown(address:String,now:Long){
        if(suppressed||credentialBlocked||desiredAddress!=null)return
        desiredAddress=address;failed=true;readyBeforeFailure=false
        until=now+RECOVERY_BUDGET_MS;next=now;failures=0
    }
    fun rememberReady(address:String,now:Long){
        desiredAddress=address;failed=false;readyBeforeFailure=true
        attempting=false;failures=0;next=now+5000
    }
    fun connectionFailed(now:Long){
        if(desiredAddress==null||suppressed)return
        if(readyBeforeFailure){
            readyBeforeFailure=false;until=now+RECOVERY_BUDGET_MS
            next=now;failures=0
        }else if(attempting){
            failures=(failures+1).coerceAtMost(4)
            next=now+(5000L shl (failures-1)).coerceAtMost(30_000)
        }
        attempting=false;failed=true
    }
    fun shouldAttempt(now:Long):String? {
        if(!foreground||!failed||suppressed||credentialBlocked||environmentPaused||
            attempting||now>=until||now<next)return null
        val address=desiredAddress ?: return null
        attempting=true;return address
    }
    /** Shared by the Android hub and the deterministic callback tests. */
    fun dispatch(now:Long,canConnect:Boolean,environmentReady:Boolean,connect:(String)->Boolean):Dispatch {
        if(!canConnect||!recoveryEnabled||now>=until)return Dispatch.NONE
        if(!environmentReady)return if(environmentUnavailable())Dispatch.UNAVAILABLE else Dispatch.NONE
        environmentAvailable()
        val address=shouldAttempt(now) ?: return Dispatch.NONE
        val accepted=runCatching {connect(address)}.getOrDefault(false)
        if(!accepted){rejected(now);return Dispatch.REJECTED}
        return Dispatch.STARTED
    }
    fun rejected(now:Long){if(attempting){
        attempting=false;failures=(failures+1).coerceAtMost(4)
        next=now+(5000L shl (failures-1)).coerceAtMost(30_000)
    }}
    fun environmentUnavailable():Boolean {
        val changed=!environmentPaused;environmentPaused=true;return changed
    }
    fun environmentAvailable(){environmentPaused=false}
    fun disable(){credentialBlocked=true;attempting=false}
    fun manualDisconnect(){suppressed=true;failed=false;attempting=false}
    fun explicitConnect(address:String){
        if(desiredAddress!=null && !desiredAddress.equals(address,ignoreCase=true))desiredAddress=null
        suppressed=false;credentialBlocked=false;failed=false;attempting=false
    }
}
