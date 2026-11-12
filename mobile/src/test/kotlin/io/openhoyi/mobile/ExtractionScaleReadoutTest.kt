package io.openhoyi.mobile

import io.openhoyi.protocol.*
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class ExtractionScaleReadoutTest {
    private val ready = DeviceState.READY
    private fun sample(weight: Int = 1234, at: Long = 1000) = ScaleObservation(weight, at,
        ScaleCapabilities(), ScaleEvidence.LIVE_READ_ONLY, raw = ByteFrame(byteArrayOf()))
    @Test fun rawSignedOneDecimalReadOnlyDoesNotRequireTareOrControl() {
        val sample = sample(-1250)
        assertFalse(sample.eligibleForTargetWeight)
        assertEquals("-12.5", ExtractionScaleReadout.live(sample, ready, 1000).number)
        assertEquals("12.3", ExtractionScaleReadout.live(sample(1234), ready, 1000).number)
    }
    @Test fun missingStaleDisconnectedAndNonWeightNeverBecomeZero() {
        assertEquals(ExtractionScaleReadout.Reason.NO_SCALE, ExtractionScaleReadout.live(null, ready, 1000).reason)
        assertEquals(ExtractionScaleReadout.Reason.STALE, ExtractionScaleReadout.live(sample(), ready, 2501).reason)
        assertEquals(ExtractionScaleReadout.Reason.DISCONNECTED, ExtractionScaleReadout.live(sample(), DeviceState.DISCONNECTED, 1000).reason)
        assertEquals("—", ExtractionScaleReadout.live(sample().copy(capabilities=ScaleCapabilities(weight=false)), ready, 1000).number)
    }
    @Test fun firstObservedEndFreezesEvenAfterCupRemovalAndReconnection() {
        val p = ExtractionScaleReadout(); p.begin("a", 3600); p.brewing()
        p.observedIdle(sample(3520), ready, 1000)
        p.observedIdle(sample(-20000, 1100), ready, 1100)
        p.finish(true)
        val end = p.value(sample(-20000, 2000), ready, 2000)
        assertEquals(3520, end.hundredthsGram); assertTrue(end.ended)
        assertEquals(3600, end.targetHundredthsGram)
    }
    @Test fun missingEndCannotBeBackfilledByFreshPostEndSample() {
        val p=ExtractionScaleReadout();p.begin("a", null);p.brewing()
        p.observedIdle(sample(), ready, 2501);p.finish(true)
        assertEquals(ExtractionScaleReadout.Reason.END_UNAVAILABLE,p.value(sample(5000,3000),ready,3000).reason)
    }
    @Test fun nextCupResetsFrozenAndTargetAndFreshReconnectCanDisplay() {
        val p=ExtractionScaleReadout();p.begin("a", 3600);p.finish(true)
        p.begin("b", null)
        val v=p.value(sample(-100),ready,1000)
        assertEquals(-100,v.hundredthsGram);assertNull(v.targetHundredthsGram);assertFalse(v.ended)
    }
    @Test fun unknownAndNoBrewingEndNeverClaimNormalReading() {
        val p=ExtractionScaleReadout();p.begin("a", 3600)
        assertEquals(ExtractionScaleReadout.Reason.UNKNOWN,p.value(sample(),ready,1000,true).reason)
        p.observedIdle(sample(),ready,1000);p.finish(true)
        assertNull(p.value(sample(),ready,1000).hundredthsGram)
        p.begin("b", 0);p.finish(false)
        assertEquals(ExtractionScaleReadout.Reason.UNKNOWN,p.value(sample(),ready,1000).reason)
    }
    @Test fun transientIdleIsDiscardedWhenBrewingResumes() {
        val p = ExtractionScaleReadout(); p.begin("a", 3600); p.brewing()
        p.observedIdle(sample(1000), ready, 1000)
        p.brewing()
        p.observedIdle(sample(3500, 2000), ready, 2000)
        p.finish(true)
        assertEquals(3500, p.value(sample(), ready, 3000).hundredthsGram)
    }

    @Test fun ordinaryMockPublishesStoppedSampleBeforeWatchFinishes() {
        val runtime = MockDeviceRuntime { "" }
        val p = ExtractionScaleReadout(); p.begin("mock", 3600)
        runtime.start(1000)
        val running = runtime.sample(2000)
        assertTrue(running.coffee is ExtractionTelemetry)
        p.brewing()
        runtime.stop()
        val stopped = runtime.sample(2000)
        assertTrue(stopped.coffee is IdleTelemetry)
        p.observedIdle(stopped.scaleObservation, stopped.scaleState, 2000)
        p.finish(true)
        assertEquals(70, p.value(null, ready, 9000).hundredthsGram)
    }

    private val hub=Any()
    private val controller=Any()
    private fun owner(serial:Long=1,epoch:Long=1,address:String="AA:BB:CC:DD:EE:FF") =
        ExtractionScaleReadout.Owner(hub,controller,serial,epoch,address)
    private fun proof(serial:Long=1,baseline:Int=40,at:Long=1000) = ExtractionScaleReadout.Proof(serial,baseline,at)
    private fun netProjection():ExtractionScaleReadout = ExtractionScaleReadout().apply {
        begin("app",3600,owner());observeProof(owner(),proof(),ExtractionState.RUNNING)
    }
    @Test fun confirmedNetSubtractsBaselineNotControlCompensationAndPreservesNegative() {
        val p=netProjection()
        assertEquals(1194,p.value(sample(1234),ready,1000).hundredthsGram)
        assertEquals(ExtractionScaleReadout.Source.BEVERAGE,p.value(sample(),ready,1000).source)
        assertEquals(-140,p.value(sample(-100),ready,1000).hundredthsGram)
        assertEquals(ExtractionScaleReadout.Source.RAW_SCALE,p.value(sample(1234,999),ready,1000).source)
        assertEquals(ExtractionScaleReadout.Source.RAW_SCALE,p.value(sample(),ready,2501).source)
        assertNull(p.value(sample(),ready,2501).hundredthsGram)
    }
    @Test fun rawWithoutThisShotProofIncludingFlowOnlyPassiveAndMockGlobalTare() {
        for(bound in listOf(null,owner())) {
            val p=ExtractionScaleReadout();p.begin("raw",null,bound)
            p.observeProof(owner(),null,ExtractionState.RUNNING)
            assertEquals(ExtractionScaleReadout.Source.RAW_SCALE,p.value(sample(),ready,1000).source)
        }
        val flow=ExtractionScaleReadout();flow.begin("flow-only",null,owner())
        flow.observeProof(owner(),proof(),ExtractionState.RUNNING)
        assertEquals(ExtractionScaleReadout.Source.RAW_SCALE,flow.value(sample(),ready,1000).source)
        val p=ExtractionScaleReadout();p.begin("passive",null)
        // Even a leftover controller proof/global tare cannot authorize a passive or Mock begin.
        p.observeProof(owner(),proof(),ExtractionState.RUNNING)
        assertEquals(1234,p.value(sample(),ready,1000).hundredthsGram)
        assertEquals(ExtractionScaleReadout.Source.RAW_SCALE,p.value(sample(),ready,1000).source)
    }
    @Test fun startMustSucceedAndAllOwnershipFieldsMustMatch() {
        val p=ExtractionScaleReadout();p.begin("a",3600,owner())
        p.observeProof(owner(),proof(),ExtractionState.STARTING)
        assertEquals(ExtractionScaleReadout.Source.RAW_SCALE,p.value(sample(),ready,1000).source)
        p.observeProof(owner(),proof(),ExtractionState.RUNNING)
        assertEquals(ExtractionScaleReadout.Source.BEVERAGE,p.value(sample(),ready,1000).source)
        for(other in listOf(owner(serial=2),owner(epoch=2),owner(address="11:22:33:44:55:66"),
            owner().copy(hub=Any()),owner().copy(controller=Any()))) {
            val q=netProjection();q.observeProof(other,proof(),ExtractionState.RUNNING)
            q.observeProof(owner(),proof(),ExtractionState.RUNNING)
            assertEquals(ExtractionScaleReadout.Source.RAW_SCALE,q.value(sample(),ready,1000).source)
        }
    }
    @Test fun disconnectReconnectAndManualTareEpochPermanentlyRevokeOnlyThisCup() {
        val p=netProjection();p.observeProof(null,null,ExtractionState.STOP_REQUESTED)
        p.observeProof(owner(),proof(),ExtractionState.RUNNING)
        assertEquals(1234,p.value(sample(),ready,1000).hundredthsGram)
        assertEquals(ExtractionScaleReadout.Source.RAW_SCALE,p.value(sample(),ready,1000).source)
        p.begin("next",3600,owner(serial=2,epoch=2))
        p.observeProof(owner(serial=2,epoch=2),proof(serial=2),ExtractionState.RUNNING)
        assertEquals(1194,p.value(sample(),ready,1000).hundredthsGram)
    }
    @Test fun endpointFreezesItsValueAndNameEvenIfOwnershipLaterChanges() {
        val p=netProjection();p.brewing();p.observedIdle(sample(3540),ready,1000)
        p.observeProof(owner(epoch=2),null,ExtractionState.ENDED_OBSERVED)
        p.finish(true)
        val end=p.value(sample(-20000,3000),ready,3000)
        assertEquals(3500,end.hundredthsGram);assertEquals(ExtractionScaleReadout.Source.BEVERAGE,end.source)
        p.observeProof(null,null,ExtractionState.IDLE)
        assertEquals(end,p.value(null,DeviceState.DISCONNECTED,9000))
        p.begin("next-passive",null)
        assertEquals(ExtractionScaleReadout.Source.RAW_SCALE,p.value(sample(),ready,1000).source)
    }
    @Test fun unknownFinishNeverReusesNetEndpoint() {
        val p=netProjection();p.brewing();p.observedIdle(sample(3540),ready,1000);p.finish(false)
        val end=p.value(sample(),ready,1000)
        assertNull(end.hundredthsGram);assertEquals(ExtractionScaleReadout.Reason.UNKNOWN,end.reason)
        assertEquals(ExtractionScaleReadout.Source.RAW_SCALE,end.source)
    }

}
