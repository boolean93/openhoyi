package io.openhoyi.session

fun policyChecks():Int {
    var n=0
    fun case(name:String,f:()->Unit){f();n++;println("PASS $name")}
    case("shared scale eligibility keeps signed bounds and exact freshness boundary") {
        for(weight in listOf(-50_000,-34_400,0,600_000)) {
            check(ScaleReadingPolicy.isFresh(weight,0,1500))
            check(!ScaleReadingPolicy.isFresh(weight,0,1501))
        }
        for(weight in listOf(-50_001,600_001,Int.MIN_VALUE,Int.MAX_VALUE))
            check(!ScaleReadingPolicy.isFresh(weight,1000,1000))
        check(!ScaleReadingPolicy.isFresh(0,null,0))
        check(!ScaleReadingPolicy.isFresh(0,-1,0))
        check(!ScaleReadingPolicy.isFresh(0,1,0))
        check(!ScaleReadingPolicy.isFresh(0,0,-1))
        check(!ScaleReadingPolicy.isFresh(0,0,Long.MAX_VALUE))
        check(ScaleReadingPolicy.isFresh(0,Long.MAX_VALUE,Long.MAX_VALUE))
    }
    case("invalid weights cannot refresh the target-stop health deadline") {
        val p=ExtractionPolicy();p.begin(20,3400,0,0);p.confirmTare(20,0,100)
        check(p.sample(20,WeightReading(600_001,1601),1601)==null)
        check(p.sample(20,WeightReading(Int.MIN_VALUE,1602),1602)==null)
        check(p.checkHealth(20,1602)==StopReason.SCALE_UNAVAILABLE)
    }
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
    case("stale machine timer falls back to monotonic time at target weight") {
        val p=ExtractionPolicy();p.begin(5,3400,0,0);p.confirmTare(5,0,100)
        p.observeMachineElapsed(5,3,7200)
        check(p.sample(5,WeightReading(3430,7200),7200)==null)
        check(p.sample(5,WeightReading(3440,9000),9000)==StopReason.TARGET_WEIGHT)
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
        r.foreground(10000,true);check(!r.shouldAttempt(10000,scaleReady=false))
        r.explicitConnect();check(r.shouldAttempt(10000,scaleReady=false))
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
    case("first verified scale connection enables recovery after physical failure") {
        val r=ReconnectPolicy();r.foreground(0,hasRememberedScale=false)
        r.explicitConnect(100)
        check(!r.recoveryEnabled)
        check(!r.shouldAttempt(100,false))
        r.observeScaleState(200,DeviceState.CONNECTING)
        check(!r.recoveryEnabled)
        r.observeScaleState(300,DeviceState.READY)
        check(r.recoveryEnabled)
        r.observeScaleState(700_000,DeviceState.FAILED)
        check(r.shouldAttempt(700_000,false))
    }
    case("successful scale reconnect resets failure backoff but avoids immediate flapping") {
        val r=ReconnectPolicy();r.foreground(100,true)
        check(r.shouldAttempt(100,false));r.observeScaleState(200,DeviceState.FAILED)
        check(r.shouldAttempt(5200,false));r.observeScaleState(5300,DeviceState.READY)
        check(!r.shouldAttempt(5301,false))
        r.observeScaleState(10300,DeviceState.FAILED)
        check(r.shouldAttempt(10300,false));r.observeScaleState(10400,DeviceState.FAILED)
        check(r.shouldAttempt(15400,false))
    }
    case("unsupported remembered scale stops automatic retries") {
        val r=ReconnectPolicy();r.foreground(100,true)
        check(r.shouldAttempt(100,false))
        r.observeScaleState(200,DeviceState.UNSUPPORTED)
        check(!r.shouldAttempt(10000,false))
    }
    case("manual scale disconnect remains suppressed across foreground transitions") {
        val r=ReconnectPolicy();r.foreground(100,true)
        r.manualDisconnect();r.background();r.foreground(200,true)
        check(!r.shouldAttempt(200,false))
        r.explicitConnect();check(r.shouldAttempt(200,false))
    }
    case("coffee recovery only retries failed original connection and respects explicit intent") {
        val r=CoffeeReconnectPolicy();r.foreground(100)
        r.rememberReady("AA:BB:CC:DD:EE:01",100)
        check(r.shouldAttempt(100)==null)
        r.connectionFailed(200)
        check(r.shouldAttempt(200)=="AA:BB:CC:DD:EE:01")
        r.connectionFailed(300)
        check(r.shouldAttempt(5299)==null)
        check(r.shouldAttempt(5300)=="AA:BB:CC:DD:EE:01")
        r.manualDisconnect();r.background();r.foreground(6000)
        check(r.shouldAttempt(6000)==null)
        r.explicitConnect("AA:BB:CC:DD:EE:01");r.rememberReady("AA:BB:CC:DD:EE:01",6000);r.connectionFailed(6100)
        check(r.shouldAttempt(6100)=="AA:BB:CC:DD:EE:01")
        r.disable();check(r.shouldAttempt(7000)==null)
    }
    case("coffee recovery pauses in background and never follows a different manual address") {
        val r=CoffeeReconnectPolicy();r.foreground(100)
        r.rememberReady("AA:BB:CC:DD:EE:01",100);r.connectionFailed(200)
        r.background();check(r.shouldAttempt(200)==null)
        r.foreground(300);check(r.shouldAttempt(300)=="AA:BB:CC:DD:EE:01")
        r.explicitConnect("AA:BB:CC:DD:EE:02")
        r.connectionFailed(400);check(r.shouldAttempt(400)==null)
        r.rememberReady("AA:BB:CC:DD:EE:02",500);r.connectionFailed(600)
        check(r.shouldAttempt(600)=="AA:BB:CC:DD:EE:02")
    }
    case("coffee physical failure after long ready opens one bounded episode") {
        val r=CoffeeReconnectPolicy();r.foreground(0)
        r.rememberReady("AA:BB:CC:DD:EE:01",0)
        r.connectionFailed(700_000)
        check(r.shouldAttempt(700_000)=="AA:BB:CC:DD:EE:01")
        r.connectionFailed(700_100)
        r.background();r.foreground(1_300_100)
        check(r.shouldAttempt(1_300_100)==null)
        r.rememberReady("AA:BB:CC:DD:EE:01",1_300_200)
        r.connectionFailed(2_000_000)
        check(r.shouldAttempt(2_000_000)=="AA:BB:CC:DD:EE:01")
    }
    case("scale physical failure after long ready opens one bounded episode") {
        val r=ReconnectPolicy();r.foreground(0,true)
        check(r.shouldAttempt(0,false));r.observeScaleState(100,DeviceState.READY)
        r.observeScaleState(700_000,DeviceState.FAILED)
        check(r.shouldAttempt(700_000,false))
        r.observeScaleState(700_100,DeviceState.FAILED)
        r.background();r.foreground(1_300_100,true)
        check(!r.shouldAttempt(1_300_100,false))
        r.observeScaleState(1_300_200,DeviceState.READY)
        r.observeScaleState(2_000_000,DeviceState.FAILED)
        check(r.shouldAttempt(2_000_000,false))
    }
    case("environment pause never becomes manual suppression or extends an episode") {
        val coffee=CoffeeReconnectPolicy();coffee.foreground(0)
        coffee.rememberReady("AA:BB:CC:DD:EE:01",0);coffee.connectionFailed(100)
        coffee.environmentUnavailable();check(coffee.shouldAttempt(100)==null)
        coffee.environmentAvailable();check(coffee.shouldAttempt(200)=="AA:BB:CC:DD:EE:01")
        coffee.connectionFailed(300);coffee.environmentUnavailable();coffee.environmentAvailable()
        check(coffee.shouldAttempt(600_100)==null)
        val scale=ReconnectPolicy();scale.foreground(0,true)
        scale.environmentUnavailable();check(!scale.shouldAttempt(0,false))
        scale.environmentAvailable();check(scale.shouldAttempt(1,false))
    }
    case("coffee hub callback dispatches only original address and stops after credential refusal") {
        val r=CoffeeReconnectPolicy();r.foreground(0)
        r.ensureKnown("AA:BB:CC:DD:EE:01",0)
        val calls=mutableListOf<String>()
        check(r.dispatch(0,false,true){calls+=it;true}==CoffeeReconnectPolicy.Dispatch.NONE)
        check(r.dispatch(0,true,false){calls+=it;true}==CoffeeReconnectPolicy.Dispatch.UNAVAILABLE)
        check(calls.isEmpty())
        check(r.dispatch(1,true,true){calls+=it;false}==CoffeeReconnectPolicy.Dispatch.REJECTED)
        check(calls==listOf("AA:BB:CC:DD:EE:01"))
        check(r.dispatch(2,true,true){calls+=it;true}==CoffeeReconnectPolicy.Dispatch.NONE)
        r.disable() // service credential gate reached its limit
        check(r.dispatch(6_000,true,true){calls+=it;true}==CoffeeReconnectPolicy.Dispatch.NONE)
        r.explicitConnect("AA:BB:CC:DD:EE:02")
        r.rememberReady("AA:BB:CC:DD:EE:02",7_000);r.connectionFailed(8_000)
        check(r.dispatch(8_000,true,true){calls+=it;true}==CoffeeReconnectPolicy.Dispatch.STARTED)
        check(calls.last()=="AA:BB:CC:DD:EE:02")
    }
    return n
}
