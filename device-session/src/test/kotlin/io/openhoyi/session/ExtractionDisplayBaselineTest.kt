package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

/** Read-only proof follows the existing dispatch path. No extra tare/start/stop calls. */
class ExtractionDisplayBaselineTest {
    private class Fixture {
        var now=1000L;var sequence=0L;var starts=0;var tares=0
        var coffeeReady=true
        var startDone:((OperationResult)->Unit)?=null
        var tareDone:((OperationResult)->Unit)?=null
        val parameters=StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)
        val controller=ExtractionController(object:CoffeeControl {
            override val ready get()=coffeeReady
            override fun prepareStart(parameters:StartParameters)=true
            override fun startConditionsValid(parameters:StartParameters)=true
            override fun start(parameters:StartParameters,beforeDispatch:()->Boolean,done:(OperationResult)->Unit) {
                check(beforeDispatch());starts++;startDone=done
            }
            override fun stop(done:(OperationResult)->Unit){done(OperationResult.Success())}
        },object:ScaleControl {
            override val ready=true
            override val startAllowed=true
            override val capabilities=ScaleCapabilities(tare=true,validatedWeightControl=true)
            override fun tare(beforeDispatch:()->Boolean,done:(OperationResult)->Unit) {
                check(beforeDispatch());tares++;tareDone=done
            }
        },{now},{sequence})
        fun weight(value:Int) {
            sequence++
            controller.weight(ScaleObservation(value,now,ScaleCapabilities(tare=true,validatedWeightControl=true),
                ScaleEvidence.VERIFIED_TRANSPORT,raw=ByteFrame(byteArrayOf())))
        }
        fun begin(target:Int=3600) { weight(1800);assertTrue(controller.start(parameters,target,100)) }
        fun zero() { tareDone!!(OperationResult.Success());weight(40) }
        fun running() {begin();zero();startDone!!(OperationResult.Success())}
    }
    @Test fun orderedSameMillisecondZeroBecomesProofOnlyAfterSuccessfulStart() {
        val f=Fixture();f.begin();val serial=f.controller.displayShotSerial
        f.weight(0);assertNull(f.controller.displayTareBaseline);assertEquals(0,f.starts)
        f.zero();assertEquals(1,f.starts);assertNull(f.controller.displayTareBaseline)
        f.startDone!!(OperationResult.Success())
        assertEquals(ExtractionController.DisplayTareBaseline(serial,40,1000),f.controller.displayTareBaseline)
        assertEquals(1,f.tares);assertEquals(1,f.starts)
    }
    @Test fun failedCancelledUnknownAndUnsubmittedStartNeverPublishProof() {
        for(result in listOf(OperationResult.Failed("fail"),OperationResult.Cancelled("cancel"),OperationResult.Unknown("unknown"))) {
            val f=Fixture();f.begin();f.zero();f.startDone!!(result)
            assertNull(f.controller.displayTareBaseline)
        }
        val f=Fixture();f.begin();f.controller.manualStop();f.zero()
        assertNull(f.controller.displayTareBaseline);assertEquals(0,f.starts)
    }
    @Test fun disconnectUnknownAndNextCupCannotReuseProof() {
        val f=Fixture();f.running();assertNotNull(f.controller.displayTareBaseline)
        f.controller.scaleDisconnected();assertNull(f.controller.displayTareBaseline)
        f.now++;f.weight(100);assertNull(f.controller.displayTareBaseline)
        f.controller.machineIdle();val old=f.controller.displayShotSerial
        f.begin();assertTrue(f.controller.displayShotSerial>old);assertNull(f.controller.displayTareBaseline)
        val g=Fixture();g.running();g.coffeeReady=false;g.controller.tick()
        assertEquals(ExtractionState.OUTCOME_UNKNOWN,g.controller.state);assertNull(g.controller.displayTareBaseline)
    }
    @Test fun flowOnlyDoesNotPublishProofOrAddATare() {
        val f=Fixture();f.begin(target=0);f.startDone!!(OperationResult.Success())
        assertNull(f.controller.displayTareBaseline);assertEquals(0,f.tares);assertEquals(1,f.starts)
    }
}
