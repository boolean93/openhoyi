package io.openhoyi.bluetooth

import io.openhoyi.session.*
import org.junit.Assert.*
import org.junit.Test

class ScaleAdvertisementClassifierTest {
    @Test fun publicFelicitaNameSelectsOnlyItsExperimentalReadOnlyAdapter() {
        assertEquals(FelicitaReadOnlyScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("FELICITA"))
        assertEquals(FelicitaReadOnlyScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("felicita arc"))
        assertFalse(FelicitaReadOnlyScaleProtocolAdapter.transportVerified)
    }
    @Test fun existingBookooNamesKeepTheirProtocolAndOtherFamiliesAreNotGuessed() {
        assertEquals(BookooScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("BOOKOO MINI"))
        assertEquals(BookooScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("bookoo"))
        for(name in listOf("ACAIA","DiFluid Microbalance","HOYI","","unknown"))assertNull(ScaleAdvertisementClassifier.protocolId(name))
    }
}
