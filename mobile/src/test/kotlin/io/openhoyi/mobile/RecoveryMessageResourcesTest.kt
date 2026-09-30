package io.openhoyi.mobile

import io.openhoyi.protocol.*
import io.openhoyi.session.*
import org.junit.Assert.*
import org.junit.Test

class RecoveryMessageResourcesTest {
    @Test fun emptyTranslationDoesNotPermitUnsafePreheatOrRecovery() {
        val studio = StudioStartGate { _, _ -> "" }
        val cancel = BrewWaitCancelGate { _, _ -> "" }
        val clear = ShotRecoveryClearGate { _, _ -> "" }
        val raw = ByteFrame(byteArrayOf())
        val idle = IdleTelemetry(9200,12000,10,10,0,0,0,0,raw)
        val profile = CurveCatalog.profiles[1]
        val preparation = BrewPreparation()
        assertNotNull(studio.block(null,9200,profile,preparation))
        assertNull(studio.block(Settings(1,1,3,0,92,0,125,15,70,0,0,raw),null,profile,preparation))
        assertNotNull(cancel.block(DeviceState.DISCONNECTED,idle,1000,1000,ExtractionState.IDLE,false))
        assertNull(cancel.block(DeviceState.READY,idle,1000,1000,ExtractionState.IDLE,false))
        var writes=0
        val address="AA:BB:CC:DD:EE:01"
        val recovery=ShotRecoveryState(object : ShotRecoveryState.Storage {
            override fun read()=ShotRecoveryState.Record(true,address)
            override fun write(record: ShotRecoveryState.Record): Boolean { writes++; return true }
        })
        assertNotNull(clear.block(recovery,address,DeviceState.READY,idle,1000,1000,ExtractionState.OUTCOME_UNKNOWN,false))
        assertNotNull(clear.block(recovery,"AA:BB:CC:DD:EE:02",DeviceState.READY,idle,1000,1000,ExtractionState.IDLE,false))
        assertNull(clear.block(recovery,address,DeviceState.READY,idle,1000,1000,ExtractionState.IDLE,false))
        assertEquals(0,writes)
        assertTrue(recovery.pending)
    }
}
