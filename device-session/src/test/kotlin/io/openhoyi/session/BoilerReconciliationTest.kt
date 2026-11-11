package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

class BoilerReconciliationTest {
    private val settings=(HoyiCodec.decode("830113FD5C007D0F350019006E".chunked(2)
        .map {it.toInt(16).toByte()}.toByteArray()) as DecodeResult.Valid).value as Settings
    private fun recovery()=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
        override fun read()=MachineWriteRecoveryState.Record(null,null)
        override fun write(record:MachineWriteRecoveryState.Record)=true
    }).also {check(it.arm(MachineWriteRecoveryState.Kind.SETTING,"AA:BB:CC:DD:EE:01"))}
    @Test fun actualDifferentBoilerReportClearsOnlyTheOriginalLiveOwnerAndNeverPublishesSuccess() {
        val tracker=SettingsWriteTracker();val token=requireNotNull(tracker.begin(MachineSettingChange.BrewHeating(false)))
        tracker.written(token,OperationResult.Success(),0)
        val recovery=recovery();val owner=requireNotNull(recovery.captureOwnership());val events=mutableListOf<OrdinaryWriteReadback.Event>()
        assertTrue(OrdinaryWriteReadback.settings(tracker,recovery,1,settings,{recovery.clear(owner)},true,events::add))
        assertEquals(SettingsWriteTracker.State.RECONCILED,tracker.state)
        assertFalse(recovery.pending);assertEquals(listOf(OrdinaryWriteReadback.Event.RECONCILED),events)
    }
    @Test fun scopedReconnectLeaseCannotBeRearmedByFailureOrNormalReadyState() {
        val lease=BoilerReconnectLease();assertFalse(lease.held)
        assertTrue(lease.claim());assertTrue(lease.held);assertFalse(lease.claim())
        lease.release();assertFalse(lease.held);assertTrue(lease.claim())
    }
    @Test fun oldOwnerOrdinarySettingAndRestartedGenericRecordCannotAutoClear() {
        for(mode in 0..2) {
            val tracker=SettingsWriteTracker();val recovery=recovery();val owner=requireNotNull(recovery.captureOwnership())
            if(mode!=2) {
                val change=if(mode==0)MachineSettingChange.BrewHeating(false) else MachineSettingChange.BrewTemperature(93)
                val token=requireNotNull(tracker.begin(change));tracker.written(token,OperationResult.Unknown("lost"),0)
            }
            if(mode==0){check(recovery.clear(owner));check(recovery.arm(MachineWriteRecoveryState.Kind.SETTING,"AA:BB:CC:DD:EE:01"))}
            OrdinaryWriteReadback.settings(tracker,recovery,1,settings,{recovery.clear(owner)},true) { }
            assertTrue(recovery.pending)
        }
    }
}
