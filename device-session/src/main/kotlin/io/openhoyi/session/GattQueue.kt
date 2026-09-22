package io.openhoyi.session

/** Thread-confined queue; elapsed time must come from a monotonic clock. Never retries writes. */
class GattQueue(private val driver: GattDriver, private val clock: () -> Long, private val invalidated: (String) -> Unit = {}) {
    private data class Pending(val token:Long,val operation:GattOperation,val timeout:Long,val callback:(OperationResult)->Unit)
    private val owner=Thread.currentThread()
    private val pending=ArrayDeque<Pending>()
    private var current:Pending?=null
    private var deadline=0L
    private var serial=0L
    var generation=0L; private set
    var active=false; private set
    val inFlight:Boolean get()=current!=null
    private fun assertThread()=check(Thread.currentThread()===owner){"GattQueue accessed outside owner thread"}
    fun open():Long {
        assertThread(); disconnect("replaced"); generation++; active=true;return generation
    }
    fun enqueue(operation:GattOperation, timeoutMs:Long, urgent:Boolean=false, callback:(OperationResult)->Unit) {
        assertThread(); require(timeoutMs in 1..120_000)
        if(!active){callback(OperationResult.Cancelled("not connected"));return}
        if(pending.size>=64 && !urgent){callback(OperationResult.Failed("queue full"));return}
        val p=Pending(++serial,operation,timeoutMs,callback)
        val displaced=if(pending.size>=64)pending.removeLast() else null
        if(urgent)pending.addFirst(p) else pending.addLast(p)
        displaced?.let{deliver(it,OperationResult.Cancelled("displaced by urgent stop"))}
        pump()
    }
    fun cancelPending(predicate:(GattOperation)->Boolean) {
        assertThread()
        val cancelled=pending.filter {predicate(it.operation)}
        pending.removeAll(cancelled.toSet())
        cancelled.forEach{deliver(it,OperationResult.Cancelled("superseded"))}
    }
    private fun pump() {
        if(!active||current!=null||pending.isEmpty())return
        val p=pending.removeFirst();current=p;deadline=clock()+p.timeout
        val accepted=try{driver.execute(generation,p.token,p.operation)}catch(e:Exception){false}
        if(!accepted && current===p) {
            current=null
            deliver(p,OperationResult.Failed("transport rejected operation"))
            pump()
        }
    }
    fun complete(generation:Long, token:Long, result:OperationResult) {
        assertThread();val p=current?:return
        if(!active||this.generation!=generation||p.token!=token)return
        current=null;deliver(p,result);pump()
    }
    private fun deliver(p:Pending,result:OperationResult) {
        try {p.callback(result)} catch(_:Exception) {
            disconnect("operation callback failure")
            invalidated("operation callback failure")
        }
    }
    fun tick() {
        assertThread()
        if(active&&current!=null&&clock()>=deadline){disconnect("operation timeout");invalidated("operation timeout")}
    }
    fun disconnect(reason:String) {
        assertThread();if(!active)return
        active=false
        val running=current;current=null
        val waiting=pending.toList();pending.clear()
        try { driver.close(generation) } catch(_:Exception) { /* All waiters still settle. */ } finally {
            running?.let { deliver(it,OperationResult.Unknown(reason)) }
            waiting.forEach{deliver(it,OperationResult.Cancelled(reason))}
        }
    }
}
