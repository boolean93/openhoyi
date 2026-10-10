package io.openhoyi.mobile

/** App-only stopwatch. Persist the monotonic origin, not redraw counts, across rotation. A
 * monotonic clock reset (e.g. reboot) cancels the running origin instead of inventing duration. */
class LocalScaleTimer(state:State = State()) {
    data class State(val accumulatedMs:Long = 0,val startedAtMs:Long? = null)
    private var accumulated=state.accumulatedMs.coerceAtLeast(0)
    private var started=state.startedAtMs?.takeIf { it>=0 }
    val running:Boolean get()=started!=null
    fun start(nowMs:Long) {require(nowMs>=0);if(started==null)started=nowMs}
    fun pause(nowMs:Long) {accumulated=elapsedMs(nowMs);started=null}
    fun reset() {accumulated=0;started=null}
    fun elapsedMs(nowMs:Long):Long {
        val origin=started ?: return accumulated
        if(nowMs<origin) {started=null;return accumulated}
        val delta=nowMs-origin
        return if(Long.MAX_VALUE-accumulated<delta)Long.MAX_VALUE else accumulated+delta
    }
    fun snapshot()=State(accumulated,started)
}
