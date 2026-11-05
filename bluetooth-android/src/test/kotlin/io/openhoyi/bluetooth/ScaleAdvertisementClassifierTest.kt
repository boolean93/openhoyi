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
    @Test fun timemoreNamesChooseOnlyEvidencedModelsWithoutGuessingFamilyOrNonBleProducts() {
        for(name in listOf("TIMEMORE DOT","DOT","TES017","Timemore-DOT-1234"))
            assertEquals(TimemoreDotScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId(name))
        for(name in listOf("TIMEMORE Basic3 Link","Basic 3 Link","TES016"))
            assertEquals(TimemoreBasicLinkScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId(name))
        assertEquals(TimemoreLegacyReadOnlyScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("timemore scale"))
        for(name in listOf("TIMEMORE","TIMEMORE Basic2","TIMEMORE Nano","TIMEMORE Mini","TIMEMORE Basic4","Black Mirror 2","FFF0"))
            assertNull(ScaleAdvertisementClassifier.protocolId(name))
    }
    @Test fun existingBookooNamesKeepTheirProtocolAndOtherFamiliesAreNotGuessed() {
        assertEquals(BookooScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("BOOKOO MINI"))
        assertEquals(BookooScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("bookoo"))
        for(name in listOf("ACAIA","DiFluid Microbalance","HOYI","","unknown"))assertNull(ScaleAdvertisementClassifier.protocolId(name))
    }
}
