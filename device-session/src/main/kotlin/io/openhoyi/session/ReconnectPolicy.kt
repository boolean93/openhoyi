package io.openhoyi.session

/** Foreground polling budget, not Android bonding. Host calls once per foreground transition. */
class ReconnectPolicy {
    private var until=0L
    private var next=0L
    private var enabled=false
    private var attempting=false
    private var failures=0
    fun foreground(now:Long,hasRememberedScale:Boolean){until=now+600_000;next=now;enabled=hasRememberedScale;attempting=false;failures=0}
    fun background(){enabled=false;attempting=false}
    fun manualDisconnect(){enabled=false;attempting=false}
    fun shouldAttempt(now:Long,scaleReady:Boolean):Boolean {
        if(!enabled||attempting||scaleReady||now>=until||now<next)return false
        attempting=true;return true
    }
    fun attemptFinished(now:Long){attempting=false;failures=(failures+1).coerceAtMost(4);next=now+(5000L shl (failures-1)).coerceAtMost(30_000)}
}
