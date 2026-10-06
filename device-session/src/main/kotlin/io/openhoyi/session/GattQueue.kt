package io.openhoyi.session

/** Thread-confined queue; elapsed time must come from a monotonic clock. Never retries writes. */
class GattQueue(private val driver: GattDriver, private val clock: () -> Long, private val invalidated: (String) -> Unit = {}) {
    private data class Pending(val token:Long,val generation:Long,val operation:GattOperation,val timeout:Long,
        val beforeDispatch:()->Boolean,val callback:(OperationResult)->Unit)
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
    fun enqueue(operation:GattOperation, timeoutMs:Long, urgent:Boolean=false,
                beforeDispatch:()->Boolean={true}, callback:(OperationResult)->Unit) {
        assertThread(); require(timeoutMs in 1..120_000)
        if(!active){callback(OperationResult.Cancelled("not connected"));return}
        if(pending.size>=64 && !urgent){callback(OperationResult.Failed("queue full"));return}
        val p=Pending(++serial,generation,operation,timeoutMs,beforeDispatch,callback)
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
        val p=pending.removeFirst()
        current=p;deadline=clock()+p.timeout
        val permitted=runCatching(p.beforeDispatch).getOrDefault(false)
        if(current!==p)return // The guard may have invalidated the connection.
        if(!permitted) {
            current=null
            deliver(p,OperationResult.Failed("pre-dispatch guard rejected operation"))
            pump();return
        }
        val executionGeneration=generation
        val accepted=try{driver.execute(executionGeneration,p.token,p.operation)}catch(_:Exception){
            // A synchronous completion callback may already have replaced this owner.
            if(generation!=executionGeneration || !active)return
            // Execution may have crossed the submission boundary before throwing.
            // Keep the running result unknown and never pump later work on this owner.
            val reason="transport execution exception"
            invalidateOwner(reason,executionGeneration)
            return
        }
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
            invalidateOwner("operation callback failure",p.generation)
        }
    }
    fun tick() {
        assertThread()
        if(active&&current!=null&&clock()>=deadline)invalidateOwner("operation timeout",generation)
    }
    /** Detached observers can explicitly reconnect; old cleanup must stay on its owner. */
    private fun invalidateOwner(reason:String,ownerGeneration:Long) {
        if(generation!=ownerGeneration)return
        var failure:Exception?=null
        try { disconnect(reason) } catch(error:Exception) { failure=error }
        if(generation==ownerGeneration)try { invalidated(reason) } catch(error:Exception) {
            val first=failure
            if(first==null)failure=error
            else if(first!==error)first.addSuppressed(error)
        }
        failure?.let { throw it }
    }
    fun disconnect(reason:String) {
        assertThread();if(!active)return
        active=false
        val running=current;current=null
        val waiting=pending.toList();pending.clear()
        try { driver.close(generation) } catch(_:Exception) { /* All waiters still settle. */ } finally {
            // Failure reporting must not abandon detached operations that have not settled yet.
            var failure: Exception? = null
            fun settle(p: Pending, result: OperationResult) {
                try { deliver(p, result) } catch (error: Exception) {
                    val first = failure
                    if (first == null) failure = error
                    else if (first !== error) first.addSuppressed(error)
                }
            }
            running?.let { settle(it,OperationResult.Unknown(reason)) }
            waiting.forEach{settle(it,OperationResult.Cancelled(reason))}
            failure?.let { throw it }
        }
    }
}
