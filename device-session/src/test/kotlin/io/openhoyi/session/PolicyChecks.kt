package io.openhoyi.session

fun policyChecks():Int {
    var n=0
    fun case(name:String,f:()->Unit){f();n++;println("PASS $name")}
    case("weight stop requires fresh post-tare sample and fires once") {
        val p=ExtractionPolicy();p.begin(1,3400,0,100)
        check(p.sample(1,WeightReading(3500,200),200)==null)
        p.confirmTare(1,0,1800)
        check(p.sample(1,WeightReading(3430,8200),8200)==StopReason.TARGET_WEIGHT)
        check(p.sample(1,WeightReading(3500,8400),8400)==null)
    }
    case("stale and wrong shot samples never mean target achieved") {
        val p=ExtractionPolicy();p.begin(2,3400,0,0);p.confirmTare(2,0,1500)
        check(p.sample(1,WeightReading(4000,8000),8000)==null)
        check(p.sample(2,WeightReading(4000,1000),8000)==null)
        check(p.sample(2,WeightReading(4000,9000),8000)==null)
        check(p.checkHealth(2,8000)==StopReason.SCALE_UNAVAILABLE)
        check(p.checkHealth(2,9000)==null)
    }
    case("compensation uses signed integer units and no early seven-second stop") {
        val p=ExtractionPolicy();p.begin(3,3400,200,0);p.confirmTare(3,100,1600)
        check(p.sample(3,WeightReading(3400,6000),6000)==null)
        check(p.sample(3,WeightReading(3300,7100),7100)==StopReason.TARGET_WEIGHT)
    }
    case("flow-only extraction does not depend on weight readiness") {
        val p=ExtractionPolicy();p.begin(4,0,0,0)
        check(p.checkHealth(4,60000)==null)
        check(p.sample(4,WeightReading(5000,61000),61000)==null)
    }
    case("remembered scale reconnect works without coffee and remains bounded") {
        val r=ReconnectPolicy();r.foreground(100,hasRememberedScale=false)
        check(!r.shouldAttempt(100,scaleReady=false))
        r.foreground(100,hasRememberedScale=true)
        check(r.shouldAttempt(100,scaleReady=false));r.attemptFinished(100)
        check(!r.shouldAttempt(101,scaleReady=false));check(r.shouldAttempt(5100,scaleReady=false))
        r.manualDisconnect();check(!r.shouldAttempt(9000,scaleReady=false))
        r.foreground(10000,true);check(r.shouldAttempt(10000,scaleReady=false))
        check(!r.shouldAttempt(610000,scaleReady=false))
    }
    case("remembered scale retries after a terminal disconnect instead of remaining in flight") {
        val r=ReconnectPolicy();r.foreground(100,true)
        check(r.shouldAttempt(100,false))
        r.observeScaleState(101,DeviceState.CONNECTING)
        check(!r.shouldAttempt(101,false))
        r.observeScaleState(200,DeviceState.DISCONNECTED)
        check(!r.shouldAttempt(5199,false))
        check(r.shouldAttempt(5200,false))
    }
    case("successful scale reconnect resets failure backoff but avoids immediate flapping") {
        val r=ReconnectPolicy();r.foreground(100,true)
        check(r.shouldAttempt(100,false));r.observeScaleState(200,DeviceState.FAILED)
        check(r.shouldAttempt(5200,false));r.observeScaleState(5300,DeviceState.READY)
        check(!r.shouldAttempt(5301,false))
        check(r.shouldAttempt(10300,false));r.observeScaleState(10400,DeviceState.FAILED)
        check(r.shouldAttempt(15400,false))
    }
    case("unsupported remembered scale stops automatic retries") {
        val r=ReconnectPolicy();r.foreground(100,true)
        check(r.shouldAttempt(100,false))
        r.observeScaleState(200,DeviceState.UNSUPPORTED)
        check(!r.shouldAttempt(10000,false))
    }
    return n
}
