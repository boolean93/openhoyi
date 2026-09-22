package io.openhoyi.session

private class FakeDriver : GattDriver {
    val sent = mutableListOf<Triple<Long, Long, GattOperation>>()
    val closed = mutableListOf<Long>()
    override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean { sent += Triple(generation, token, operation); return true }
    override fun close(generation: Long) { closed += generation }
}
fun main() {
    var tests=0
    fun case(name:String, block:()->Unit) { block(); tests++; println("PASS $name") }
    case("queue serializes and priority stop overtakes pending writes") {
        val driver=FakeDriver(); var now=0L; val outcomes=mutableListOf<OperationResult>()
        val queue=GattQueue(driver,{now}); val gen=queue.open()
        queue.enqueue(GattOperation.Discover,1000){outcomes+=it}
        queue.enqueue(GattOperation.Write(Endpoint("s","w"),byteArrayOf(1)),1000){outcomes+=it}
        queue.enqueue(GattOperation.Write(Endpoint("s","w"),byteArrayOf(2)),1000, urgent=true){outcomes+=it}
        check(driver.sent.size==1)
        queue.complete(gen,driver.sent.last().second,OperationResult.Success())
        check((driver.sent.last().third as GattOperation.Write).bytes.contentEquals(byteArrayOf(2)))
        queue.complete(gen,driver.sent.last().second,OperationResult.Success())
        check((driver.sent.last().third as GattOperation.Write).bytes.contentEquals(byteArrayOf(1)))
        check(outcomes.size==2)
    }
    case("timeout invalidates generation and never sends queued side effects") {
        val d=FakeDriver();var now=0L;val results=mutableListOf<OperationResult>();val q=GattQueue(d,{now}); val gen=q.open()
        q.enqueue(GattOperation.Write(Endpoint("s","w"),byteArrayOf(2)),100){results+=it}
        q.enqueue(GattOperation.Write(Endpoint("s","w"),byteArrayOf(3)),100){results+=it}
        now=100;q.tick();check(results[0] is OperationResult.Unknown);check(results[1] is OperationResult.Cancelled)
        check(d.sent.size==1 && d.closed==listOf(gen))
        val next=q.open();q.enqueue(GattOperation.Discover,100){}
        q.complete(gen,d.sent.first().second,OperationResult.Success());check(q.inFlight)
        q.complete(next,d.sent.last().second,OperationResult.Success());check(!q.inFlight)
    }
    case("write bytes copied on construction and read") {
        val b=byteArrayOf(1);val op=GattOperation.Write(Endpoint("s","w"),b); b[0]=8;op.bytes[0]=7;check(op.bytes[0].toInt()==1)
    }
    case("disconnect settles in-flight unknown and queued cancelled exactly once") {
        val d=FakeDriver();val q=GattQueue(d,{0});val r=mutableListOf<OperationResult>();val g=q.open()
        q.enqueue(GattOperation.Discover,100){r+=it};q.enqueue(GattOperation.Discover,100){r+=it}
        val t=d.sent.single().second;q.disconnect("link lost");q.disconnect("again");q.complete(g,t,OperationResult.Success())
        check(r.size==2);check(r[0] is OperationResult.Unknown);check(r[1] is OperationResult.Cancelled)
    }
    case("two devices have independent operation queues") {
        val d1=FakeDriver();val d2=FakeDriver();val q1=GattQueue(d1,{0});val q2=GattQueue(d2,{0})
        q1.open();q2.open();q1.enqueue(GattOperation.Discover,100){};q2.enqueue(GattOperation.Discover,100){}
        q1.disconnect("lost");check(q2.inFlight && d2.closed.isEmpty())
    }
    case("callback exception cannot strand queue or leak pending commands") {
        val d=FakeDriver();var cancelled=0;val q=GattQueue(d,{0});val g=q.open()
        q.enqueue(GattOperation.Discover,100){error("consumer failure")}
        q.enqueue(GattOperation.Discover,100){if(it is OperationResult.Cancelled)cancelled++}
        q.complete(g,d.sent.single().second,OperationResult.Success())
        check(!q.active && cancelled==1 && d.sent.size==1)
    }
    case("cancelled queued starts never execute after urgent stop") {
        val d=FakeDriver();val q=GattQueue(d,{0});val g=q.open();var cancelled=0
        q.enqueue(GattOperation.Discover,100){}
        q.enqueue(GattOperation.Write(Endpoint("s","w"),byteArrayOf(2,1)),100){if(it is OperationResult.Cancelled)cancelled++}
        q.cancelPending {it is GattOperation.Write}
        q.enqueue(GattOperation.Write(Endpoint("s","w"),byteArrayOf(2,0)),100,urgent=true){}
        q.complete(g,d.sent.first().second,OperationResult.Success())
        q.complete(g,d.sent.last().second,OperationResult.Success())
        check(cancelled==1 && d.sent.size==2)
    }
    case("urgent stop remains admissible when ordinary queue is full") {
        val d=FakeDriver();val q=GattQueue(d,{0});val g=q.open();var displaced=0
        q.enqueue(GattOperation.Discover,100){}
        repeat(64){q.enqueue(GattOperation.Discover,100){if(it is OperationResult.Cancelled)displaced++}}
        q.enqueue(GattOperation.Write(Endpoint("s","w"),byteArrayOf(2,0)),100,urgent=true){}
        q.complete(g,d.sent.first().second,OperationResult.Success())
        check(d.sent.last().third is GattOperation.Write && displaced==1)
    }
    tests += wireTraceChecks()
    tests += policyChecks()
    tests += deviceChecks()
    tests += extractionChecks()
    tests += replayChecks()
    println("Session checks: $tests passed")
}
